/*
 * Copyright (C) 2025 AKS-Labs (original author)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.fuzzyfly0.searchandtranslate.ai.restoration

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * Downloads optional model files outside the APK. A model is made visible only after
 * its HTTPS transfer completes and its SHA-256 matches the catalog entry.
 */
class RestorationModelStore(context: Context) {
    private val filesDirectory = context.applicationContext.filesDir.canonicalFile
    private val modelDirectory = File(filesDirectory, "models/restoration")

    private fun ensureSafeModelDirectory() {
        val expected = File(filesDirectory, "models/restoration").canonicalFile
        check(modelDirectory.canonicalFile == expected) { "Invalid model storage location" }
        check(expected.parentFile == File(filesDirectory, "models").canonicalFile) {
            "Invalid model storage location"
        }
    }

    suspend fun install(
        model: RestorationModel,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): File = withContext(Dispatchers.IO) {
        require(model.validate()) { "Invalid or unverified model metadata" }
        ensureSafeModelDirectory()
        check(modelDirectory.mkdirs() || modelDirectory.isDirectory) {
            "Could not create model storage directory"
        }

        val destination = File(modelDirectory, "${model.id}.model")
        val temporary = File(modelDirectory, "${model.id}.download")
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(model.downloadUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                requestMethod = "GET"
            }
            connection.connect()
            check(connection.responseCode in 200..299) {
                "Model server returned HTTP ${connection.responseCode}"
            }
            val announcedSize = connection.contentLengthLong
            check(announcedSize <= model.maxBytes) {
                "Model exceeds the configured size limit"
            }

            var downloaded = 0L
            FileOutputStream(temporary).use { output ->
                connection.inputStream.use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        downloaded += count
                        check(downloaded <= model.maxBytes) {
                            "Model exceeds the configured size limit"
                        }
                        output.write(buffer, 0, count)
                        onProgress(downloaded, announcedSize.coerceAtLeast(0L))
                    }
                }
                output.fd.sync()
            }
            check(downloaded > 0L) { "Downloaded model is empty" }
            check(announcedSize < 0L || downloaded == announcedSize) {
                "Model download ended before the announced size"
            }
            val digest = sha256(temporary)
            check(digest.equals(model.sha256, ignoreCase = true)) {
                "Model checksum verification failed"
            }

            // Atomic replacement prevents a failed update from destroying the last
            // verified model. Both files live in the same app-private directory.
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
            destination
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        } finally {
            connection?.disconnect()
        }
    }

    fun installedModel(model: RestorationModel): File? {
        if (!model.validate()) return null
        ensureSafeModelDirectory()
        return File(modelDirectory, "${model.id}.model")
            .takeIf { it.isFile && it.length() in 1..model.maxBytes }
    }

    suspend fun remove(model: RestorationModel) = withContext(Dispatchers.IO) {
        require(model.validate())
        ensureSafeModelDirectory()
        File(modelDirectory, "${model.id}.model").delete()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
