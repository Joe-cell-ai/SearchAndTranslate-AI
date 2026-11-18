/*
 * Copyright (C) 2025 AKS-Labs (original author)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.fuzzyfly0.searchandtranslate.ai.restoration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RestorationExecutionPolicyTest {
    private val policy = RestorationExecutionPolicy()

    @Test
    fun rejectsInputAbovePixelBudget() {
        assertThrows(IllegalArgumentException::class.java) {
            policy.validateInput(4000, 3000)
        }
    }

    @Test
    fun rejectsOutputAbovePixelBudget() {
        assertThrows(IllegalArgumentException::class.java) {
            policy.validateOutput(8000, 5000)
        }
    }

    @Test
    fun createsSingleTileForSmallImage() {
        val tiles = policy.planTiles(200, 120)

        assertEquals(1, tiles.size)
        assertEquals(0, tiles.single().core.left)
        assertEquals(200, tiles.single().core.right)
        assertEquals(120, tiles.single().core.bottom)
        assertEquals(tiles.single().core, tiles.single().expanded)
    }

    @Test
    fun addsOverlapWithoutChangingCoreCoverage() {
        val tiles = policy.planTiles(500, 300)

        assertEquals(4, tiles.size)
        assertEquals(0, tiles[0].expanded.left)
        assertEquals(272, tiles[0].expanded.right)
        assertEquals(272, tiles[0].expanded.bottom)
        assertEquals(256, tiles[0].core.right)
        assertEquals(500, tiles[3].core.right)
        assertEquals(300, tiles[3].core.bottom)
    }

    @Test
    fun rejectsPlansWithTooManyTiles() {
        val constrained = RestorationExecutionPolicy(
            maxInputPixels = 1_000_000,
            maxOutputPixels = 4_000_000,
            maxTileEdge = 32,
            tileOverlap = 4,
            maxTiles = 2,
        )

        assertThrows(IllegalArgumentException::class.java) {
            constrained.planTiles(96, 96)
        }
    }

    @Test
    fun rejectsInvalidDimensionsAndPolicy() {
        assertThrows(IllegalArgumentException::class.java) {
            policy.validateInput(0, 100)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RestorationExecutionPolicy(tileOverlap = 128)
        }
    }

    @Test
    fun exactTileBoundariesDoNotCreateEmptyExtraTiles() {
        val tiles = policy.planTiles(512, 512)

        assertEquals(4, tiles.size)
        assertEquals(RestorationRect(0, 0, 256, 256), tiles[0].core)
        assertEquals(RestorationRect(256, 0, 512, 256), tiles[1].core)
        assertEquals(RestorationRect(0, 256, 256, 512), tiles[2].core)
        assertEquals(RestorationRect(256, 256, 512, 512), tiles[3].core)
    }

    @Test
    fun onePixelAndIrregularEdgesRemainCoveredExactlyOnce() {
        val width = 513
        val height = 257
        val coverage = IntArray(width * height)
        val tiles = policy.planTiles(width, height)

        tiles.forEach { tile ->
            val core = tile.core
            for (y in core.top until core.bottom) {
                for (x in core.left until core.right) {
                    coverage[y * width + x] += 1
                }
                assertTrue(tile.expanded.top <= y && tile.expanded.bottom > y)
            }
            assertTrue(core.left >= 0 && core.top >= 0)
            assertTrue(core.right <= width && core.bottom <= height)
            assertTrue(tile.expanded.left <= core.left)
            assertTrue(tile.expanded.top <= core.top)
            assertTrue(tile.expanded.right >= core.right)
            assertTrue(tile.expanded.bottom >= core.bottom)
        }

        assertTrue(coverage.all { it == 1 })
        assertEquals(1, coverage.last())
    }

    @Test
    fun acceptsSinglePixelImageAndRejectsNegativeDimensions() {
        val tile = policy.planTiles(1, 1).single()
        assertEquals(RestorationRect(0, 0, 1, 1), tile.core)
        assertThrows(IllegalArgumentException::class.java) {
            policy.validateInput(-1, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            policy.validateOutput(1, -1)
        }
    }

    @Test
    fun tilesStayInsideImageBounds() {
        val tiles = policy.planTiles(513, 257)

        assertTrue(tiles.all {
            it.expanded.left >= 0 &&
                it.expanded.top >= 0 &&
                it.expanded.right <= 513 &&
                it.expanded.bottom <= 257
        })
    }
}
