/*
 * Copyright (C) 2025 AKS-Labs (original author)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.fuzzyfly0.searchandtranslate.ai.restoration

import android.graphics.Bitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * Coordinates model availability, memory policy and backend execution.
 *
 * This class deliberately contains no inference implementation. A concrete ONNX/NCNN
 * engine is injected once its model contract and Android runtime have been validated.
 */
class RestorationEngineRunner(
    private val modelStore: RestorationModelStore,
    private val engine: RestorationEngine,
    private val policy: RestorationExecutionPolicy = RestorationExecutionPolicy(),
) {
    suspend fun run(
        model: RestorationModel,
        input: Bitmap,
        onProgress: (Float) -> Unit = {},
    ): Bitmap {
        require(model.validate()) { "Invalid restoration model metadata" }
        policy.validateInput(input.width, input.height)
        coroutineContext.ensureActive()

        val installedModel = checkNotNull(modelStore.installedModel(model)) {
            "Download and verify the restoration model before running"
        }

        var lastProgress = 0f
        val output = try {
            engine.restore(
                modelFile = installedModel,
                input = input,
                task = model.task,
            ) { reported ->
                // Ignore NaN/infinite values and keep progress monotonic. Reserve 100%
                // for a completed inference and a validated output bitmap.
                if (reported.isFinite()) {
                    val next = reported.coerceIn(lastProgress, 0.99f)
                    if (next > lastProgress) {
                        lastProgress = next
                        onProgress(next)
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        }

        try {
            coroutineContext.ensureActive()
            policy.validateOutput(output.width, output.height)
            require(output !== input) {
                "Restoration engine must return a separate output bitmap"
            }
            onProgress(1f)
            return output
        } catch (error: Throwable) {
            if (!output.isRecycled && output !== input) output.recycle()
            throw error
        }
    }
}
