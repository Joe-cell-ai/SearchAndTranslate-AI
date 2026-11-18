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
