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
 * The model stays in the user-selected SAF folder; the runner stages it in cache
 * only for this call. To keep peak memory bounded on mobile, this first backend
 * intentionally accepts images up to 1 megapixel (4x output = 16 megapixels).
 * Larger images are rejected with an actionable message rather than risking OOM.
 */
class RealEsrganOnnxEngine(
    private val maxInputPixels: Long = DEFAULT_MAX_INPUT_PIXELS,
    private val intraOpThreads: Int = 2,
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
        require(modelFile.isFile && modelFile.length() > 0L) { "The verified Real-ESRGAN model file is missing." }
        val inputPixels = input.width.toLong() * input.height.toLong()
        require(inputPixels in 1..maxInputPixels) {
            "This first on-device model supports images up to 1 megapixel. Choose a smaller image."
        }
        val outputWidth = Math.multiplyExact(input.width, RealEsrganX4PlusContract.UPSCALE_FACTOR)
        val outputHeight = Math.multiplyExact(input.height, RealEsrganX4PlusContract.UPSCALE_FACTOR)
        val outputPixels = outputWidth.toLong() * outputHeight.toLong()
        require(outputPixels <= MAX_OUTPUT_PIXELS) { "The restored image would exceed the memory limit." }
        coroutineContext.ensureActive()

        val environment = OrtEnvironment.getEnvironment()
        val options = OrtSession.SessionOptions()
        options.setIntraOpNumThreads(intraOpThreads)
        try {
            environment.createSession(modelFile.absolutePath, options).use { session ->
                require(session.inputNames.contains(RealEsrganX4PlusContract.INPUT_NAME)) {
                    "The selected model does not expose the expected Real-ESRGAN input."
                }
                require(session.outputNames.contains(RealEsrganX4PlusContract.OUTPUT_NAME)) {
                    "The selected model does not expose the expected Real-ESRGAN output."
                }

                val argb = IntArray(input.width * input.height)
                input.getPixels(argb, 0, input.width, 0, 0, input.width, input.height)
                val plane = input.width * input.height
                val nchw = FloatArray(plane * 3)
                for (index in 0 until plane) {
                    val pixel = argb[index]
                    nchw[index] = ((pixel ushr 16) and 0xff) / 255f
                    nchw[plane + index] = ((pixel ushr 8) and 0xff) / 255f
                    nchw[plane * 2 + index] = (pixel and 0xff) / 255f
                }
                coroutineContext.ensureActive()
                onProgress(0.05f)

                val shape = longArrayOf(1L, 3L, input.height.toLong(), input.width.toLong())
                OnnxTensor.createTensor(environment, FloatBuffer.wrap(nchw), shape).use { tensor ->
                    session.run(mapOf(RealEsrganX4PlusContract.INPUT_NAME to tensor)).use { result ->
                        coroutineContext.ensureActive()
                        val output = result.get(RealEsrganX4PlusContract.OUTPUT_NAME)
                            .orElseThrow { IllegalStateException("Real-ESRGAN returned no output tensor.") }
                        require(output is OnnxTensor) { "Real-ESRGAN returned a non-tensor output." }
                        val outputShape = output.info.shape
                        require(
                            outputShape.size == 4 &&
                                outputShape[0] == 1L &&
                                outputShape[1] == 3L &&
                                outputShape[2] == outputHeight.toLong() &&
                                outputShape[3] == outputWidth.toLong()
                        ) { "The model output dimensions do not match 4x RGB upscaling." }
                        val buffer = output.floatBuffer
                        val expected = outputPixels * 3L
                        require(expected <= Int.MAX_VALUE && buffer.remaining().toLong() == expected) {
                            "The model output tensor has an unexpected size."
                        }
                        val values = FloatArray(expected.toInt())
                        buffer.get(values)
                        coroutineContext.ensureActive()
                        onProgress(0.9f)
                        val pixels = RgbFloatTensorPostprocessor.toArgb(
                            values,
                            outputWidth,
                            outputHeight,
                            RgbFloatTensorPostprocessor.Layout.NCHW,
                        )
                        val restored = Bitmap.createBitmap(
                            outputWidth,
                            outputHeight,
                            Bitmap.Config.ARGB_8888,
                        )
                        restored.setPixels(pixels, 0, outputWidth, 0, 0, outputWidth, outputHeight)
                        onProgress(0.99f)
                        restored
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            options.close()
        }
    }

    companion object {
        const val DEFAULT_MAX_INPUT_PIXELS = 1_000_000L
        const val MAX_OUTPUT_PIXELS = 16_000_000L
    }
}
