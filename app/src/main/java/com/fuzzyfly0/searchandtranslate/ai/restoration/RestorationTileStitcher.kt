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
 * Incremental tile stitcher. Each tile's core is copied into the destination
 * immediately, so callers can release its inference buffers before processing
 * the next tile. The final output canvas is the only full-resolution pixel array.
 */
object RestorationTileStitcher {
    data class TileOutput(
        val tile: RestorationTile,
        val pixels: IntArray,
        val width: Int,
        val height: Int,
    )

    class Session(
        inputWidth: Int,
        inputHeight: Int,
        private val scale: Int,
        plannedTiles: List<RestorationTile>,
        maxOutputPixels: Long = RestorationExecutionPolicy.DEFAULT_MAX_OUTPUT_PIXELS,
    ) {
        private val expected: Set<RestorationTile>
        private val seen = HashSet<RestorationTile>()
        private val outputWidth: Int
        private val output: IntArray

        init {
            require(inputWidth > 0 && inputHeight > 0) { "Input dimensions must be positive" }
            require(scale > 0) { "Scale must be positive" }
            require(maxOutputPixels > 0) { "Output pixel budget must be positive" }
            require(plannedTiles.isNotEmpty()) { "Tile plan must not be empty" }
            val width = inputWidth.toLong() * scale
            val height = inputHeight.toLong() * scale
            val count = width * height
            require(width <= Int.MAX_VALUE && height <= Int.MAX_VALUE) {
                "Restored dimensions exceed supported integer bounds"
            }
            require(count <= maxOutputPixels && count <= Int.MAX_VALUE) {
                "Restored image exceeds the output memory limit"
            }
            outputWidth = width.toInt()
            output = IntArray(count.toInt())
            expected = plannedTiles.toSet()
            require(expected.size == plannedTiles.size) { "Tile plan contains duplicate tiles" }
            this.inputWidth = inputWidth
            this.inputHeight = inputHeight
        }

        private val inputWidth: Int
        private val inputHeight: Int

        /** Copies this tile's core. The supplied pixel array is not retained. */
        fun add(result: TileOutput) {
            val tile = result.tile
            require(tile in expected && seen.add(tile)) {
                "Tile output is unexpected or duplicated"
            }
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

            val expectedWidth = (expanded.right - expanded.left).toLong() * scale
            val expectedHeight = (expanded.bottom - expanded.top).toLong() * scale
            require(result.width > 0 && result.height > 0 &&
                result.width.toLong() == expectedWidth &&
                result.height.toLong() == expectedHeight &&
                result.pixels.size.toLong() == expectedWidth * expectedHeight
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

        /** May be called once all planned tiles have been added. */
        fun finish(): IntArray {
            require(seen.size == expected.size) { "One or more planned tile outputs are missing" }
            return output
        }
    }

    /** Compatibility helper for callers/tests that already hold all tile outputs. */
    fun stitch(
        inputWidth: Int,
        inputHeight: Int,
        scale: Int,
        plannedTiles: List<RestorationTile>,
        tileOutputs: List<TileOutput>,
        maxOutputPixels: Long = RestorationExecutionPolicy.DEFAULT_MAX_OUTPUT_PIXELS,
    ): IntArray {
        require(tileOutputs.size == plannedTiles.size) {
            "Every planned tile must have exactly one output"
        }
        val session = Session(inputWidth, inputHeight, scale, plannedTiles, maxOutputPixels)
        tileOutputs.forEach(session::add)
        return session.finish()
    }
}
