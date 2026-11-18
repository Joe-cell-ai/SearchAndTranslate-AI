/*
 * Copyright (C) 2025 AKS-Labs (original author)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.fuzzyfly0.searchandtranslate.ai.restoration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RealEsrganRgbTensorPreprocessorTest {
    private val edge = RealEsrganX4PlusContract.TILE_EDGE
    private val planeSize = edge * edge

    @Test
    fun writesPlanarRgbNormalizedToZeroThroughOne() {
        val pixels = IntArray(edge * edge) { 0xff804020.toInt() }
        val tensor = RealEsrganRgbTensorPreprocessor.toNchw(pixels, edge, edge, 0, 0)

        assertEquals(RealEsrganRgbTensorPreprocessor.VALUES_PER_TILE, tensor.size)
        assertEquals(128 / 255f, tensor[0], 0.000001f)
        assertEquals(64 / 255f, tensor[planeSize], 0.000001f)
        assertEquals(32 / 255f, tensor[2 * planeSize], 0.000001f)
    }

    @Test
    fun replicatesRightAndBottomEdgesForPartialTiles() {
        val pixels = intArrayOf(0xffff0000.toInt(), 0xff00ff00.toInt(),
            0xff0000ff.toInt(), 0xffffffff.toInt())
        val tensor = RealEsrganRgbTensorPreprocessor.toNchw(pixels, 2, 2, 1, 1)

        // Tile origin is the bottom-right white pixel; every padded pixel is white.
        assertEquals(1f, tensor[0], 0f)
        assertEquals(1f, tensor[planeSize - 1], 0f)
        assertEquals(1f, tensor[planeSize], 0f)
        assertEquals(1f, tensor[2 * planeSize], 0f)
    }

    @Test
    fun rejectsMismatchedBufferAndOutOfRangeTileOrigin() {
        assertThrows(IllegalArgumentException::class.java) {
            RealEsrganRgbTensorPreprocessor.toNchw(intArrayOf(0), 2, 2, 0, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RealEsrganRgbTensorPreprocessor.toNchw(intArrayOf(0), 1, 1, 1, 0)
        }
    }
}
