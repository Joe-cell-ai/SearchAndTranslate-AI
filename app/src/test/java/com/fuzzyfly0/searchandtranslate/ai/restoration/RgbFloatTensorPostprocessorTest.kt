/*
 * Copyright (C) 2025 AKS-Labs (original author)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.fuzzyfly0.searchandtranslate.ai.restoration

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RgbFloatTensorPostprocessorTest {
    @Test
    fun convertsPlanarNchwRgbToOpaqueArgb() {
        val pixels = RgbFloatTensorPostprocessor.toArgb(
            values = floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f),
            width = 2,
            height = 1,
            layout = RgbFloatTensorPostprocessor.Layout.NCHW,
        )

        assertArrayEquals(intArrayOf(0xffff0000.toInt(), 0xff00ff00.toInt()), pixels)
    }

    @Test
    fun convertsInterleavedNhwcRgb() {
        val pixels = RgbFloatTensorPostprocessor.toArgb(
            values = floatArrayOf(0f, 0f, 1f, 1f, 1f, 1f),
            width = 2,
            height = 1,
            layout = RgbFloatTensorPostprocessor.Layout.NHWC,
        )

        assertArrayEquals(intArrayOf(0xff0000ff.toInt(), 0xffffffff.toInt()), pixels)
    }

    @Test
    fun clampsOutOfRangeValuesAndRoundsToNearestByte() {
        val pixels = RgbFloatTensorPostprocessor.toArgb(
            values = floatArrayOf(-1f, 0.5f, 2f),
            width = 1,
            height = 1,
            layout = RgbFloatTensorPostprocessor.Layout.NCHW,
        )

        assertEquals(0xff0080ff.toInt(), pixels.single())
    }

    @Test
    fun rejectsWrongBufferSizeAndNonFiniteValues() {
        assertThrows(IllegalArgumentException::class.java) {
            RgbFloatTensorPostprocessor.toArgb(
                floatArrayOf(0f, 0f), 1, 1,
                RgbFloatTensorPostprocessor.Layout.NCHW,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            RgbFloatTensorPostprocessor.toArgb(
                floatArrayOf(Float.NaN, 0f, 0f), 1, 1,
                RgbFloatTensorPostprocessor.Layout.NCHW,
            )
        }
    }
}
