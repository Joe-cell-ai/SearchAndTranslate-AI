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
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * Downloads optional model files outside the APK. A model is made visible only after
 * its HTTPS transfer completes and its SHA-256 matches the catalog entry.
 */
class RestorationModelStore(context: Context) {
    private val modelDirectory = File(context.applicationContext.filesDir, "models/restoration")

    suspend fun install(
        model: RestorationModel,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): File = withContext(Dispatchers.IO) {
        require(model.validate()) { "Invalid or unverified model metadata" }
        check(modelDirectory.canonicalFile.path.startsWith(
            contextCanonicalFilesDir(context = modelDirectory.parentFile?.parentFile)
        )) { "Invalid model storage location" }

        modelDirectory.mkdirs()
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
            check(connection.responseCode in 200..299) { "Model server returned HTTP ${connection.responseCode}" }
            val announcedSize = connection.contentLengthLong
            check(announcedSize <= model.maxBytes) { "Model exceeds the configured size limit" }

            var downloaded = 0L
            FileOutputStream(temporary).use { output ->
                connection.inputStream.use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        downloaded += count
                        check(downloaded <= model.maxBytes) { "Model exceeds the configured size limit" }
                        output.write(buffer, 0, count)
                        onProgress(downloaded, announcedSize)
                    }
                }
                output.fd.sync()
            }
            val digest = sha256(temporary)
            check(digest.equals(model.sha256, ignoreCase = true)) { "Model checksum verification failed" }
            if (destination.exists()) check(destination.delete()) { "Could not replace existing model" }
            check(temporary.renameTo(destination)) { "Could not finalize model download" }
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
        return File(modelDirectory, "${model.id}.model").takeIf { it.isFile && it.length() in 1..model.maxBytes }
    }

    suspend fun remove(model: RestorationModel) = withContext(Dispatchers.IO) {
        require(model.validate())
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

    private fun contextCanonicalFilesDir(context: File?): String =
        requireNotNull(context).canonicalPath
}
