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
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * SAF-backed model storage. Model bytes stay in the folder explicitly selected by the
 * user; only a small URI/checksum index is kept in app-private preferences.
 *
 * Call [rememberFolder] with the result of ACTION_OPEN_DOCUMENT_TREE before [install].
 */
class SafRestorationModelStore(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val preferences = appContext.getSharedPreferences(
        "restoration_model_index_v1",
        Context.MODE_PRIVATE,
    )

    fun rememberFolder(treeUri: Uri) {
        require(treeUri.scheme == "content") { "Choose a valid document folder" }
        appContext.contentResolver.takePersistableUriPermission(
            treeUri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        preferences.edit().putString(KEY_TREE_URI, treeUri.toString()).apply()
    }

    fun selectedFolder(): Uri? =
        preferences.getString(KEY_TREE_URI, null)?.let(Uri::parse)

    suspend fun install(
        model: RestorationModel,
        treeUri: Uri = checkNotNull(selectedFolder()) {
            "Choose a model folder before downloading"
        },
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): Uri = withContext(Dispatchers.IO) {
        require(model.validate()) { "Invalid or unverified model metadata" }
        require(treeUri.scheme == "content") { "Invalid model folder" }
        val permission = appContext.contentResolver.persistedUriPermissions
            .firstOrNull { it.uri == treeUri && it.isReadPermission && it.isWritePermission }
        check(permission != null) { "Model folder access expired. Choose the folder again." }

        val finalName = "${model.id}.model"
        val temporaryName = "${model.id}.download"
        val existing = findChild(treeUri, finalName)
        val treeId = DocumentsContract.getTreeDocumentId(treeUri)
        val parentDocument = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeId)
        val temporary = DocumentsContract.createDocument(
            resolver, parentDocument, "application/octet-stream", temporaryName,
        ) ?: error("The selected folder cannot create files. Choose another folder.")

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
            check(announcedSize <= model.maxBytes) { "Model exceeds the configured size limit" }

            val digest = MessageDigest.getInstance("SHA-256")
            var downloaded = 0L
            val output = resolver.openOutputStream(temporary, "wt")
                ?: error("Cannot write to the selected model folder")
            output.use { sink ->
                connection.inputStream.use { source ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = source.read(buffer)
                        if (count < 0) break
                        downloaded += count
                        check(downloaded <= model.maxBytes) {
                            "Model exceeds the configured size limit"
                        }
                        digest.update(buffer, 0, count)
                        sink.write(buffer, 0, count)
                        onProgress(downloaded, announcedSize.coerceAtLeast(0L))
                    }
                }
                sink.flush()
            }
            check(downloaded > 0L) { "Downloaded model is empty" }
            check(announcedSize < 0L || downloaded == announcedSize) {
                "Model download ended before the announced size"
            }
            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
            check(actualHash.equals(model.sha256, ignoreCase = true)) {
                "Model checksum verification failed"
            }

            // Rename the verified temporary document first. Keep the previous
            // verified document until the replacement has been committed.
            val renamed = DocumentsContract.renameDocument(resolver, temporary, finalName)
                ?: error("The selected folder cannot safely finalize model downloads")
            val size = downloaded
            preferences.edit()
                .putString(modelKey(model.id, "uri"), renamed.toString())
                .putString(modelKey(model.id, "sha256"), model.sha256.lowercase())
                .putLong(modelKey(model.id, "size"), size)
                .putString(modelKey(model.id, "task"), model.task.name)
                .putString(modelKey(model.id, "name"), model.displayName)
                .putString(KEY_TREE_URI, treeUri.toString())
                .apply()
            if (existing != null && existing != renamed) {
                runCatching { DocumentsContract.deleteDocument(resolver, existing) }
            }
            renamed
        } catch (error: Throwable) {
            runCatching { DocumentsContract.deleteDocument(resolver, temporary) }
            throw error
        } finally {
            connection?.disconnect()
        }
    }

    fun installedModelUri(model: RestorationModel): Uri? {
        if (!model.validate()) return null
        val raw = preferences.getString(modelKey(model.id, "uri"), null) ?: return null
        val uri = Uri.parse(raw)
        val tree = selectedFolder() ?: return null
        val permission = resolver.persistedUriPermissions.any {
            it.uri == tree && it.isReadPermission
        }
        if (!permission) return null
        return runCatching {
            resolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                if (descriptor.statSize in 1..model.maxBytes) uri else null
            }
        }.getOrNull()
    }

    suspend fun remove(model: RestorationModel): Boolean = withContext(Dispatchers.IO) {
        require(model.validate())
        val raw = preferences.getString(modelKey(model.id, "uri"), null)
        val deleted = raw?.let { runCatching {
            DocumentsContract.deleteDocument(resolver, Uri.parse(it))
        }.getOrDefault(false) } ?: false
        // Keep the index entry if the provider refused deletion. Otherwise the
        // user would lose the only in-app reference to a model still on disk.
        if (deleted) {
            preferences.edit()
                .remove(modelKey(model.id, "uri"))
                .remove(modelKey(model.id, "sha256"))
                .remove(modelKey(model.id, "size"))
                .remove(modelKey(model.id, "task"))
                .remove(modelKey(model.id, "name"))
                .apply()
        }
        deleted
    }

    /** Stage a SAF document only for the duration of an engine session. */
    suspend fun <T> withTemporaryModelFile(model: RestorationModel, block: suspend (File) -> T): T =
        withContext(Dispatchers.IO) {
            val uri = checkNotNull(installedModelUri(model)) {
                "Model is unavailable. Re-select its folder or download it again."
            }
            val cache = File(appContext.cacheDir, "restoration")
            check(cache.mkdirs() || cache.isDirectory) { "Cannot create temporary model cache" }
            val staged = File.createTempFile("${model.id}-", ".model", cache)
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                var copied = 0L
                val input = resolver.openInputStream(uri)
                    ?: error("Cannot read the selected model document")
                input.use { source ->
                    FileOutputStream(staged).use { sink ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = source.read(buffer)
                            if (count < 0) break
                            copied += count
                            check(copied <= model.maxBytes) { "Installed model exceeds size limit" }
                            digest.update(buffer, 0, count)
                            sink.write(buffer, 0, count)
                        }
                        sink.fd.sync()
                    }
                }
                val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
                check(actualHash.equals(model.sha256, ignoreCase = true)) {
                    "Installed model checksum verification failed"
                }
                block(staged)
            } finally {
                staged.delete()
            }
        }

    private fun findChild(treeUri: Uri, displayName: String): Uri? = runCatching {
        val treeId = DocumentsContract.getTreeDocumentId(treeUri)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeId)
        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                if (cursor.getString(nameColumn) == displayName) {
                    return@use DocumentsContract.buildDocumentUriUsingTree(
                        treeUri, cursor.getString(idColumn),
                    )
                }
            }
            null
        }
    }.getOrNull()

    private fun modelKey(id: String, field: String) = "model.$id.$field"

    private companion object {
        const val KEY_TREE_URI = "selected_tree_uri"
    }
}
