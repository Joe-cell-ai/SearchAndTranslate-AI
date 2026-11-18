/*
 * Copyright (C) 2025 AKS-Labs (original author)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.fuzzyfly0.searchandtranslate.ai.restoration

/**
 * Admission contract for the published Real-ESRGAN x4plus 128px ONNX export.
 *
 * This describes the CPU-compatible FLOAT32 graph only. It intentionally does not
 * publish a download URL or checksum and does not imply that an inference runtime
 * is installed. Quantized Qualcomm HTP assets are not interchangeable with this
 * CPU contract.
 */
object RealEsrganX4PlusContract {
    const val MODEL_ID = "realesrgan-x4plus-onnx-fp32"
    const val INPUT_NAME = "input"
    const val OUTPUT_NAME = "output"
    const val TILE_EDGE = 128
    const val UPSCALE_FACTOR = 4

    val INPUT_SHAPE = longArrayOf(1, 3, TILE_EDGE.toLong(), TILE_EDGE.toLong())
    val OUTPUT_SHAPE = longArrayOf(
        1, 3,
        (TILE_EDGE * UPSCALE_FACTOR).toLong(),
        (TILE_EDGE * UPSCALE_FACTOR).toLong(),
    )

    /**
     * Qualcomm's sample export uses a static NCHW RGB tensor, normalized to [0, 1].
     * The output is RGB NCHW, also in [0, 1]. Reject unexpected exports rather than
     * guessing layouts, names, precision, or dimensions at runtime.
     */
    fun accepts(
        inputName: String,
        inputShape: LongArray,
        inputType: String,
        outputName: String,
        outputShape: LongArray,
        outputType: String,
    ): Boolean =
        inputName == INPUT_NAME &&
            inputShape.contentEquals(INPUT_SHAPE) &&
            inputType == "tensor(float)" &&
            outputName == OUTPUT_NAME &&
            outputShape.contentEquals(OUTPUT_SHAPE) &&
            outputType == "tensor(float)"
}
