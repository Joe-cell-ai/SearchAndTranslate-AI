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
import org.junit.Assert.assertThrows
import org.junit.Test

class RestorationTileStitcherTest {
    @Test
    fun cropsOverlapAndStitchesCoresWithoutSeams() {
        val first = RestorationTile(
            core = RestorationRect(0, 0, 2, 1),
            expanded = RestorationRect(0, 0, 3, 1),
        )
        val second = RestorationTile(
            core = RestorationRect(2, 0, 4, 1),
            expanded = RestorationRect(1, 0, 4, 1),
        )
        val result = RestorationTileStitcher.stitch(
            inputWidth = 4,
            inputHeight = 1,
            scale = 1,
            plannedTiles = listOf(first, second),
            tileOutputs = listOf(
                RestorationTileStitcher.TileOutput(first, intArrayOf(10, 11, 12), 3, 1),
                RestorationTileStitcher.TileOutput(second, intArrayOf(21, 22, 23), 3, 1),
            ),
        )
        assertArrayEquals(intArrayOf(10, 11, 22, 23), result)
    }

    @Test
    fun scalesCoreCoordinatesForFourTimesOutput() {
        val tile = RestorationTile(
            core = RestorationRect(0, 0, 2, 1),
            expanded = RestorationRect(0, 0, 2, 1),
        )
        val pixels = IntArray(8) { it + 1 }
        val result = RestorationTileStitcher.stitch(
            inputWidth = 2,
            inputHeight = 1,
            scale = 2,
            plannedTiles = listOf(tile),
            tileOutputs = listOf(RestorationTileStitcher.TileOutput(tile, pixels, 4, 2)),
        )
        assertArrayEquals(pixels, result)
    }

    @Test
    fun rejectsMissingDuplicateAndMismatchedTileOutputs() {
        val tile = RestorationTile(
            core = RestorationRect(0, 0, 1, 1),
            expanded = RestorationRect(0, 0, 1, 1),
        )
        val valid = RestorationTileStitcher.TileOutput(tile, intArrayOf(7), 1, 1)
        assertThrows(IllegalArgumentException::class.java) {
            RestorationTileStitcher.stitch(1, 1, 1, listOf(tile), emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            RestorationTileStitcher.stitch(1, 1, 1, listOf(tile), listOf(valid, valid))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RestorationTileStitcher.stitch(
                1, 1, 1, listOf(tile),
                listOf(RestorationTileStitcher.TileOutput(tile, intArrayOf(7, 8), 2, 1)),
            )
        }
    }

    @Test
    fun incrementalSessionCopiesTilesImmediatelyAndRequiresCompletePlan() {
        val first = RestorationTile(
            core = RestorationRect(0, 0, 2, 1),
            expanded = RestorationRect(0, 0, 3, 1),
        )
        val second = RestorationTile(
            core = RestorationRect(2, 0, 4, 1),
            expanded = RestorationRect(1, 0, 4, 1),
        )
        val session = RestorationTileStitcher.Session(
            inputWidth = 4,
            inputHeight = 1,
            scale = 1,
            plannedTiles = listOf(first, second),
        )
        session.add(RestorationTileStitcher.TileOutput(first, intArrayOf(10, 11, 12), 3, 1))
        assertThrows(IllegalArgumentException::class.java) { session.finish() }
        session.add(RestorationTileStitcher.TileOutput(second, intArrayOf(21, 22, 23), 3, 1))
        assertArrayEquals(intArrayOf(10, 11, 22, 23), session.finish())
        assertThrows(IllegalArgumentException::class.java) {
            session.add(RestorationTileStitcher.TileOutput(second, intArrayOf(21, 22, 23), 3, 1))
        }
    }

    @Test
    fun enforcesOutputPixelBudget() {
        val tile = RestorationTile(
            core = RestorationRect(0, 0, 2, 2),
            expanded = RestorationRect(0, 0, 2, 2),
        )
        assertThrows(IllegalArgumentException::class.java) {
            RestorationTileStitcher.stitch(
                2, 2, 2, listOf(tile),
                listOf(RestorationTileStitcher.TileOutput(tile, IntArray(16), 4, 4)),
                maxOutputPixels = 15,
            )
        }
    }
}
