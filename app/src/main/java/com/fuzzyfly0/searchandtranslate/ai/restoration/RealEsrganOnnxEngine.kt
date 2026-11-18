/*
 * Copyright (C) 2025 AKS-Labs (original author)
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.fuzzyfly0.searchandtranslate.ai.restoration

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.FloatBuffer
import kotlin.coroutines.coroutineContext

/**
 * Real-ESRGAN x4plus FP32 inference using ONNX Runtime CPU.
 *
 * Inference is performed on overlapping tiles. Only each tile's non-overlapping
 * core is copied into the final bitmap, avoiding seams without retaining a
 * full-image FP32 tensor or a full-image FP32 model output.
 *
 * The model remains in user-selected SAF storage. The conservative 1MP input
 * and 16MP output limits are retained for mobile memory safety.
 */
class RealEsrganOnnxEngine(
    private val maxInputPixels: Long = DEFAULT_MAX_INPUT_PIXELS,
    private val intraOpThreads: Int = 2,
    private val policy: RestorationExecutionPolicy = RestorationExecutionPolicy(
        maxInputPixels = DEFAULT_MAX_INPUT_PIXELS,
        maxOutputPixels = MAX_OUTPUT_PIXELS,
        maxTileEdge = TILE_EDGE,
        tileOverlap = TILE_OVERLAP,
        maxTiles = MAX_TILES,
    ),
) : RestorationEngine {
    init {
        require(maxInputPixels in 1..DEFAULT_MAX_INPUT_PIXELS)
        require(intraOpThreads in 1..4)
    }

    override suspend fun restore(
        modelFile: File,
        input: Bitmap,
        task: RestorationTask,
        onProgress: (Float) -> Unit,
    ): Bitmap = withContext(Dispatchers.Default) {
        require(task == RestorationTask.IMAGE_UPSCALING) {
            "This model supports image upscaling only. Face restoration needs the separate GFPGAN model."
        }
        require(modelFile.isFile && modelFile.length() > 0L) {
            "The verified Real-ESRGAN model file is missing."
        }
        val inputPixels = input.width.toLong() * input.height.toLong()
        require(inputPixels in 1..maxInputPixels) {
            "This on-device model supports images up to 1 megapixel. Choose a smaller image."
        }

        val scale = RealEsrganX4PlusContract.UPSCALE_FACTOR
        val outputWidth = Math.multiplyExact(input.width, scale)
        val outputHeight = Math.multiplyExact(input.height, scale)
        policy.validateOutput(outputWidth, outputHeight)
        val tiles = policy.planTiles(input.width, input.height)
        coroutineContext.ensureActive()

        val sourcePixels = IntArray(Math.toIntExact(inputPixels))
        input.getPixels(sourcePixels, 0, input.width, 0, 0, input.width, input.height)
        onProgress(0.02f)

        val environment = OrtEnvironment.getEnvironment()
        val options = OrtSession.SessionOptions()
        try {
            options.setIntraOpNumThreads(intraOpThreads)
            environment.createSession(modelFile.absolutePath, options).use { session ->
                require(session.inputNames.contains(RealEsrganX4PlusContract.INPUT_NAME)) {
                    "The selected model does not expose the expected Real-ESRGAN input."
                }
                require(session.outputNames.contains(RealEsrganX4PlusContract.OUTPUT_NAME)) {
                    "The selected model does not expose the expected Real-ESRGAN output."
                }

                val stitcher = RestorationTileStitcher.Session(
                    inputWidth = input.width,
                    inputHeight = input.height,
                    scale = scale,
                    plannedTiles = tiles,
                    maxOutputPixels = MAX_OUTPUT_PIXELS,
                )
                tiles.forEachIndexed { index, tile ->
                    coroutineContext.ensureActive()
                    val tileWidth = tile.expanded.right - tile.expanded.left
                    val tileHeight = tile.expanded.bottom - tile.expanded.top
                    val nchw = toNchw(
                        pixels = sourcePixels,
                        imageWidth = input.width,
                        region = tile.expanded,
                    )
                    val expectedWidth = Math.multiplyExact(tileWidth, scale)
                    val expectedHeight = Math.multiplyExact(tileHeight, scale)
                    val inputShape = longArrayOf(1L, 3L, tileHeight.toLong(), tileWidth.toLong())
                    coroutineContext.ensureActive()

                    val tileArgb = OnnxTensor.createTensor(
                        environment,
                        FloatBuffer.wrap(nchw),
                        inputShape,
                    ).use { tensor ->
                        session.run(mapOf(RealEsrganX4PlusContract.INPUT_NAME to tensor)).use { result ->
                            coroutineContext.ensureActive()
                            val output = result.get(RealEsrganX4PlusContract.OUTPUT_NAME)
                                .orElseThrow {
                                    IllegalStateException("Real-ESRGAN returned no output tensor.")
                                }
                            require(output is OnnxTensor) {
                                "Real-ESRGAN returned a non-tensor output."
                            }
                            val shape = output.info.shape
                            require(
                                shape.size == 4 &&
                                    shape[0] == 1L &&
                                    shape[1] == 3L &&
                                    shape[2] == expectedHeight.toLong() &&
                                    shape[3] == expectedWidth.toLong()
                            ) {
                                "The model output dimensions do not match 4x tile upscaling."
                            }
                            val expectedValues = expectedWidth.toLong() * expectedHeight.toLong() * 3L
                            require(expectedValues <= Int.MAX_VALUE) {
                                "The inferred tile exceeds the supported tensor size."
                            }
                            val buffer = output.floatBuffer
                            require(buffer.remaining().toLong() == expectedValues) {
                                "The model output tensor has an unexpected size."
                            }
                            val values = FloatArray(expectedValues.toInt())
                            buffer.get(values)
                            RgbFloatTensorPostprocessor.toArgb(
                                values,
                                expectedWidth,
                                expectedHeight,
                                RgbFloatTensorPostprocessor.Layout.NCHW,
                            )
                        }
                    }

                    stitcher.add(
                        RestorationTileStitcher.TileOutput(
                            tile = tile,
                            pixels = tileArgb,
                            width = expectedWidth,
                            height = expectedHeight,
                        ),
                    )
                    onProgress(0.05f + 0.9f * (index + 1).toFloat() / tiles.size.toFloat())
                }

                coroutineContext.ensureActive()
                val stitched = stitcher.finish()
                coroutineContext.ensureActive()
                val bitmap = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
                try {
                    bitmap.setPixels(stitched, 0, outputWidth, 0, 0, outputWidth, outputHeight)
                    coroutineContext.ensureActive()
                    onProgress(0.99f)
                    bitmap
                } catch (failure: Throwable) {
                    // Do not leak an allocated bitmap if filling it, cancellation, or
                    // a progress callback fails before ownership reaches the caller.
                    bitmap.recycle()
                    throw failure
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            options.close()
        }
    }

    private fun toNchw(
        pixels: IntArray,
        imageWidth: Int,
        region: RestorationRect,
    ): FloatArray {
        val width = region.right - region.left
        val height = region.bottom - region.top
        val plane = Math.multiplyExact(width, height)
        val output = FloatArray(Math.multiplyExact(plane, 3))
        for (y in 0 until height) {
            val sourceOffset = (region.top + y) * imageWidth + region.left
            val rowOffset = y * width
            for (x in 0 until width) {
                val pixel = pixels[sourceOffset + x]
                val offset = rowOffset + x
                output[offset] = ((pixel ushr 16) and 0xff) / 255f
                output[plane + offset] = ((pixel ushr 8) and 0xff) / 255f
                output[plane * 2 + offset] = (pixel and 0xff) / 255f
            }
        }
        return output
    }

    companion object {
        const val DEFAULT_MAX_INPUT_PIXELS = 1_000_000L
        const val MAX_OUTPUT_PIXELS = 16_000_000L
        const val TILE_EDGE = 256
        const val TILE_OVERLAP = 16
        const val MAX_TILES = 512
    }
}
