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
import java.net.URL

/** Model metadata is supplied only after the model format, license and checksum are verified. */
data class RestorationModel(
    val id: String,
    val displayName: String,
    val task: RestorationTask,
    val downloadUrl: String,
    val sha256: String,
    val maxBytes: Long,
) {
    fun validate(): Boolean {
        val uri = runCatching { URL(downloadUrl) }.getOrNull() ?: return false
        return id.matches(Regex("[a-z0-9][a-z0-9._-]{1,63}")) &&
            displayName.isNotBlank() &&
            uri.protocol == "https" &&
            sha256.matches(Regex("[a-fA-F0-9]{64}")) &&
            maxBytes in 1..MAX_MODEL_BYTES
    }

    companion object {
        const val MAX_MODEL_BYTES = 1_073_741_824L // hard ceiling: 1 GiB
    }
}

enum class RestorationTask { FACE_RESTORATION, IMAGE_UPSCALING }

/** A backend must enforce its own input-size/tile limits and release native resources. */
interface RestorationEngine {
    suspend fun restore(
        input: Bitmap,
        task: RestorationTask,
        onProgress: (Float) -> Unit = {},
    ): Bitmap
}
