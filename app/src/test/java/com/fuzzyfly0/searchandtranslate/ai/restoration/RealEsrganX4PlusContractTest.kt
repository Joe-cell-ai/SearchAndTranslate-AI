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
    fun acceptsPublishedFloat32StaticNchwContract() {
        assertTrue(
            RealEsrganX4PlusContract.accepts(
                inputName = "input",
                inputShape = longArrayOf(1, 3, 128, 128),
                inputType = "tensor(float)",
                outputName = "output",
                outputShape = longArrayOf(1, 3, 512, 512),
                outputType = "tensor(float)",
            )
        )
    }

    @Test
    fun rejectsDifferentInputLayoutOrTileSize() {
        assertFalse(
            RealEsrganX4PlusContract.accepts(
                "input", longArrayOf(1, 128, 128, 3), "tensor(float)",
                "output", longArrayOf(1, 3, 512, 512), "tensor(float)",
            )
        )
        assertFalse(
            RealEsrganX4PlusContract.accepts(
                "input", longArrayOf(1, 3, 256, 256), "tensor(float)",
                "output", longArrayOf(1, 3, 1024, 1024), "tensor(float)",
            )
        )
    }

    @Test
    fun rejectsQuantizedOrUnexpectedTensorTypes() {
        assertFalse(
            RealEsrganX4PlusContract.accepts(
                "input", longArrayOf(1, 3, 128, 128), "tensor(uint8)",
                "output", longArrayOf(1, 3, 512, 512), "tensor(float)",
            )
        )
    }

    @Test
    fun rejectsUnexpectedTensorNamesAndOutputShape() {
        assertFalse(
            RealEsrganX4PlusContract.accepts(
                "images", longArrayOf(1, 3, 128, 128), "tensor(float)",
                "output", longArrayOf(1, 3, 512, 512), "tensor(float)",
            )
        )
        assertFalse(
            RealEsrganX4PlusContract.accepts(
                "input", longArrayOf(1, 3, 128, 128), "tensor(float)",
                "output", longArrayOf(1, 3, 128, 128), "tensor(float)",
            )
        )
    }
}
