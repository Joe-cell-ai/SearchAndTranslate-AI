/*
 * Copyright (C) 2025 AKS-Labs (original author)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.fuzzyfly0.searchandtranslate.ai.restoration

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealEsrganX4PlusContractTest {
    @Test
    fun acceptsInspectedDynamicFloat32NchwContract() {
        assertTrue(
            RealEsrganX4PlusContract.accepts(
                "input", longArrayOf(1, 3, -1, -1), "tensor(float)",
                "output", longArrayOf(1, 3, -1, -1), "tensor(float)",
            )
        )
    }

    @Test
    fun acceptsDynamicGraphAndKeepsTileSizeAsExecutionPolicy() {
        assertTrue(
            RealEsrganX4PlusContract.accepts(
                "input", longArrayOf(1, 3, -1, -1), "tensor(float)",
                "output", longArrayOf(1, 3, -1, -1), "tensor(float)",
            )
        )
        assertTrue(RealEsrganX4PlusContract.TILE_EDGE == 128)
    }

    @Test
    fun rejectsStaticShapesAndDifferentInputLayout() {
        assertFalse(
            RealEsrganX4PlusContract.accepts(
                "input", longArrayOf(1, 3, 128, 128), "tensor(float)",
                "output", longArrayOf(1, 3, -1, -1), "tensor(float)",
            )
        )
        assertFalse(
            RealEsrganX4PlusContract.accepts(
                "input", longArrayOf(1, 128, 128, 3), "tensor(float)",
                "output", longArrayOf(1, 3, -1, -1), "tensor(float)",
            )
        )
    }

    @Test
    fun rejectsQuantizedTypesAndUnexpectedNames() {
        assertFalse(
            RealEsrganX4PlusContract.accepts(
                "input", longArrayOf(1, 3, -1, -1), "tensor(uint8)",
                "output", longArrayOf(1, 3, -1, -1), "tensor(float)",
            )
        )
        assertFalse(
            RealEsrganX4PlusContract.accepts(
                "images", longArrayOf(1, 3, -1, -1), "tensor(float)",
                "output", longArrayOf(1, 3, -1, -1), "tensor(float)",
            )
        )
    }
}
