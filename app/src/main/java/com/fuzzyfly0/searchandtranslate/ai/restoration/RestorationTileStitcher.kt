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
 * Reassembles independently inferred tiles by copying only each tile's non-overlapping
 * core. The expanded inference margins are discarded, preventing double-blended seams.
 *
 * The caller must provide outputs for the exact tile plan, in any order. Tile output
 * dimensions must equal the expanded input region multiplied by [scale].
 */
object RestorationTileStitcher {
    data class TileOutput(
        val tile: RestorationTile,
        val pixels: IntArray,
        val width: Int,
        val height: Int,
    )

    fun stitch(
        inputWidth: Int,
        inputHeight: Int,
        scale: Int,
        plannedTiles: List<RestorationTile>,
        tileOutputs: List<TileOutput>,
        maxOutputPixels: Long = RestorationExecutionPolicy.DEFAULT_MAX_OUTPUT_PIXELS,
    ): IntArray {
        require(inputWidth > 0 && inputHeight > 0) { "Input dimensions must be positive" }
        require(scale > 0) { "Scale must be positive" }
        require(maxOutputPixels > 0) { "Output pixel budget must be positive" }
        require(plannedTiles.isNotEmpty()) { "Tile plan must not be empty" }
        require(tileOutputs.size == plannedTiles.size) { "Every planned tile must have exactly one output" }

        val outputWidthLong = inputWidth.toLong() * scale
        val outputHeightLong = inputHeight.toLong() * scale
        val outputPixels = outputWidthLong * outputHeightLong
        require(outputWidthLong <= Int.MAX_VALUE && outputHeightLong <= Int.MAX_VALUE) {
            "Restored dimensions exceed supported integer bounds"
        }
        require(outputPixels <= maxOutputPixels && outputPixels <= Int.MAX_VALUE) {
            "Restored image exceeds the output memory limit"
        }
        val outputWidth = outputWidthLong.toInt()
        val output = IntArray(outputPixels.toInt())

        val expected = plannedTiles.toSet()
        require(expected.size == plannedTiles.size) { "Tile plan contains duplicate tiles" }
        val seen = HashSet<RestorationTile>()
        for (result in tileOutputs) {
            val tile = result.tile
            require(tile in expected && seen.add(tile)) { "Tile output is unexpected or duplicated" }
            val core = tile.core
            val expanded = tile.expanded
            require(
                core.left >= 0 && core.top >= 0 &&
                    core.right <= inputWidth && core.bottom <= inputHeight &&
                    core.left < core.right && core.top < core.bottom
            ) { "Tile core is outside the source image" }
            require(
                expanded.left >= 0 && expanded.top >= 0 &&
                    expanded.right <= inputWidth && expanded.bottom <= inputHeight &&
                    expanded.left <= core.left && expanded.top <= core.top &&
                    expanded.right >= core.right && expanded.bottom >= core.bottom &&
                    expanded.left < expanded.right && expanded.top < expanded.bottom
            ) { "Expanded tile region must contain its core and stay inside the image" }

            val expectedWidth = expanded.right.toLong().minus(expanded.left).times(scale)
            val expectedHeight = expanded.bottom.toLong().minus(expanded.top).times(scale)
            require(result.width.toLong() == expectedWidth && result.height.toLong() == expectedHeight) {
                "Inferred tile dimensions do not match its expanded source region"
            }
            require(result.width > 0 && result.height > 0 &&
                result.pixels.size.toLong() == result.width.toLong() * result.height.toLong()
            ) { "Inferred tile pixel buffer does not match its dimensions" }

            val sourceX = (core.left - expanded.left) * scale
            val sourceY = (core.top - expanded.top) * scale
            val copyWidth = (core.right - core.left) * scale
            val copyHeight = (core.bottom - core.top) * scale
            val destinationX = core.left * scale
            val destinationY = core.top * scale
            for (row in 0 until copyHeight) {
                val sourceOffset = (sourceY + row) * result.width + sourceX
                val destinationOffset = (destinationY + row) * outputWidth + destinationX
                System.arraycopy(result.pixels, sourceOffset, output, destinationOffset, copyWidth)
            }
        }
        require(seen.size == expected.size) { "One or more planned tile outputs are missing" }
        return output
    }
}
