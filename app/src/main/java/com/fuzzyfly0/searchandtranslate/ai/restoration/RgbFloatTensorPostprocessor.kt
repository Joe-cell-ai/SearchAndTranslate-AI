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
 * Converts a single-image RGB float tensor into an ARGB pixel buffer.
 *
 * Tensor layout and dimensions are explicit inputs: this class does not assume the
 * unverified ONNX graph names, shape, scale, or channel order. Values must be finite
 * and are clamped to [0, 1] before 8-bit conversion.
 */
object RgbFloatTensorPostprocessor {
    enum class Layout { NCHW, NHWC }

    fun toArgb(
        values: FloatArray,
        width: Int,
        height: Int,
        layout: Layout,
    ): IntArray {
        require(width > 0 && height > 0) { "Tensor dimensions must be positive" }
        val pixels = width.toLong() * height.toLong()
        require(pixels <= Int.MAX_VALUE / 3) { "Tensor is too large" }
        val expectedValues = pixels * 3L
        require(values.size.toLong() == expectedValues) {
            "RGB tensor buffer does not match the supplied dimensions"
        }

        val output = IntArray(pixels.toInt())
        val planeSize = pixels.toInt()
        for (pixel in 0 until planeSize) {
            val red: Float
            val green: Float
            val blue: Float
            when (layout) {
                Layout.NCHW -> {
                    red = values[pixel]
                    green = values[planeSize + pixel]
                    blue = values[2 * planeSize + pixel]
                }
                Layout.NHWC -> {
                    val offset = pixel * 3
                    red = values[offset]
                    green = values[offset + 1]
                    blue = values[offset + 2]
                }
            }
            require(red.isFinite() && green.isFinite() && blue.isFinite()) {
                "RGB tensor contains a non-finite value"
            }
            output[pixel] = (0xff shl 24) or
                (toByte(red) shl 16) or
                (toByte(green) shl 8) or
                toByte(blue)
        }
        return output
    }

    private fun toByte(value: Float): Int =
        (value.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
}
