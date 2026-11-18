/*
 * Copyright (C) 2025 AKS-Labs (original author)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.fuzzyfly0.searchandtranslate.ai.restoration

import android.graphics.Rect

/** Conservative memory limits, independent of a particular inference runtime. */
data class RestorationExecutionPolicy(
    val maxInputPixels: Long = DEFAULT_MAX_INPUT_PIXELS,
    val maxOutputPixels: Long = DEFAULT_MAX_OUTPUT_PIXELS,
    val maxTileEdge: Int = DEFAULT_TILE_EDGE,
    val tileOverlap: Int = DEFAULT_TILE_OVERLAP,
    val maxTiles: Int = DEFAULT_MAX_TILES,
) {
    init {
        require(maxInputPixels > 0)
        require(maxOutputPixels > 0)
        require(maxTileEdge >= 32)
        require(tileOverlap >= 0 && tileOverlap * 2 < maxTileEdge)
        require(maxTiles > 0)
    }

    fun validateInput(width: Int, height: Int) {
        require(width > 0 && height > 0) { "Image dimensions must be positive" }
        val pixels = width.toLong() * height.toLong()
        require(pixels <= maxInputPixels) {
            "Image is too large for on-device restoration ($pixels pixels)"
        }
    }

    fun validateOutput(width: Int, height: Int) {
        require(width > 0 && height > 0) { "Output dimensions must be positive" }
        val pixels = width.toLong() * height.toLong()
        require(pixels <= maxOutputPixels) {
            "Restored image exceeds the output memory limit ($pixels pixels)"
        }
    }

    /**
     * Splits an image into non-overlapping output cores with expanded inference regions.
     * A backend must crop each inferred tile back to its core before stitching.
     */
    fun planTiles(width: Int, height: Int): List<RestorationTile> {
        validateInput(width, height)
        val columns = ceilDiv(width, maxTileEdge)
        val rows = ceilDiv(height, maxTileEdge)
        val count = columns.toLong() * rows.toLong()
        require(count <= maxTiles) { "Image requires too many restoration tiles ($count)" }

        return buildList(count.toInt()) {
            for (row in 0 until rows) {
                for (column in 0 until columns) {
                    val left = column * maxTileEdge
                    val top = row * maxTileEdge
                    val right = minOf(width, left + maxTileEdge)
                    val bottom = minOf(height, top + maxTileEdge)
                    add(
                        RestorationTile(
                            core = Rect(left, top, right, bottom),
                            expanded = Rect(
                                maxOf(0, left - tileOverlap),
                                maxOf(0, top - tileOverlap),
                                minOf(width, right + tileOverlap),
                                minOf(height, bottom + tileOverlap),
                            ),
                        )
                    )
                }
            }
        }
    }

    private fun ceilDiv(value: Int, divisor: Int): Int = (value + divisor - 1) / divisor

    companion object {
        // ARGB_8888 input/output buffers are approximately 32 MiB / 128 MiB,
        // before accounting for model tensors and native runtime memory.
        const val DEFAULT_MAX_INPUT_PIXELS = 8_000_000L
        const val DEFAULT_MAX_OUTPUT_PIXELS = 32_000_000L
        const val DEFAULT_TILE_EDGE = 256
        const val DEFAULT_TILE_OVERLAP = 16
        const val DEFAULT_MAX_TILES = 512
    }
}

data class RestorationTile(
    val core: Rect,
    val expanded: Rect,
)
