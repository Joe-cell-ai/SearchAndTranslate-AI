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
 * Converts one source-image tile into the Real-ESRGAN x4plus FLOAT32 NCHW tensor.
 *
 * The caller supplies ARGB pixels for the complete source image. Pixels beyond the
 * image edge are replicated from the nearest edge pixel; this avoids introducing
 * artificial black borders. The output is planar RGB in [0, 1], not interleaved RGB.
 *
 * This utility does not execute inference or stitch model outputs.
 */
object RealEsrganRgbTensorPreprocessor {
    const val CHANNELS = 3
    const val VALUES_PER_TILE =
        CHANNELS * RealEsrganX4PlusContract.TILE_EDGE * RealEsrganX4PlusContract.TILE_EDGE

    fun toNchw(
        argbPixels: IntArray,
        imageWidth: Int,
        imageHeight: Int,
        tileLeft: Int,
        tileTop: Int,
    ): FloatArray {
        require(imageWidth > 0 && imageHeight > 0) { "Image dimensions must be positive" }
        require(argbPixels.size.toLong() == imageWidth.toLong() * imageHeight.toLong()) {
            "Pixel buffer does not match image dimensions"
        }
        require(tileLeft >= 0 && tileTop >= 0) { "Tile origin must not be negative" }
        require(tileLeft < imageWidth && tileTop < imageHeight) {
            "Tile origin must be inside the image"
        }

        val edge = RealEsrganX4PlusContract.TILE_EDGE
        val planeSize = edge * edge
        val output = FloatArray(VALUES_PER_TILE)

        for (y in 0 until edge) {
            val sourceY = (tileTop + y).coerceAtMost(imageHeight - 1)
            for (x in 0 until edge) {
                val sourceX = (tileLeft + x).coerceAtMost(imageWidth - 1)
                val pixel = argbPixels[sourceY * imageWidth + sourceX]
                val index = y * edge + x
                output[index] = ((pixel shr 16) and 0xff) / 255f
                output[planeSize + index] = ((pixel shr 8) and 0xff) / 255f
                output[2 * planeSize + index] = (pixel and 0xff) / 255f
            }
        }
        return output
    }
}
