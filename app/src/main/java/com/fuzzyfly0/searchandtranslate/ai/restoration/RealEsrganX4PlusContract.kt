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
 * Admission contract for the independently inspected Real-ESRGAN x4plus FP32 ONNX graph.
 *
 * The published graph has dynamic spatial dimensions. TILE_EDGE is an application
 * memory policy only; it is not a static ONNX input constraint.
 */
object RealEsrganX4PlusContract {
    const val MODEL_ID = "realesrgan-x4plus-onnx-fp32"
    const val INPUT_NAME = "input"
    const val OUTPUT_NAME = "output"
    const val TILE_EDGE = 128
    const val UPSCALE_FACTOR = 4

    /** -1 denotes a dynamic dimension in the inspected ONNX graph. */
    val INPUT_SHAPE = longArrayOf(1, 3, -1, -1)
    val OUTPUT_SHAPE = longArrayOf(1, 3, -1, -1)

    fun accepts(
        inputName: String,
        inputShape: LongArray,
        inputType: String,
        outputName: String,
        outputShape: LongArray,
        outputType: String,
    ): Boolean =
        inputName == INPUT_NAME &&
            acceptsDynamicRgbShape(inputShape) &&
            inputType == "tensor(float)" &&
            outputName == OUTPUT_NAME &&
            acceptsDynamicRgbShape(outputShape) &&
            outputType == "tensor(float)"

    private fun acceptsDynamicRgbShape(shape: LongArray): Boolean =
        shape.size == 4 &&
            shape[0] == 1L &&
            shape[1] == 3L &&
            shape[2] == -1L &&
            shape[3] == -1L
}
