/*
 * Copyright (C) 2025 AKS-Labs (original author)
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.fuzzyfly0.searchandtranslate.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.content.ContentValues
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fuzzyfly0.searchandtranslate.ai.restoration.RestorationTask
import com.fuzzyfly0.searchandtranslate.ai.restoration.RestorationModel
import com.fuzzyfly0.searchandtranslate.ai.restoration.RestorationEngineRunner
import com.fuzzyfly0.searchandtranslate.ai.restoration.RealEsrganOnnxEngine
import com.fuzzyfly0.searchandtranslate.ai.restoration.SafRestorationModelStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manage user-selected model storage. Downloads remain disabled until each model
 * artifact and its Android inference contract have been independently verified.
 */
@Composable
fun RestorationModelsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { SafRestorationModelStore(context) }
    val scope = rememberCoroutineScope()
    var selectedFolder by remember { mutableStateOf(store.selectedFolder()) }
    var installedModels by remember { mutableStateOf(store.installedModels()) }
    var message by remember { mutableStateOf<String?>(null) }
    var selectedImage by remember { mutableStateOf<Bitmap?>(null) }
    var selectedTask by remember { mutableStateOf(RestorationTask.IMAGE_UPSCALING) }
    var selectedImageName by remember { mutableStateOf<String?>(null) }
    var restoredImage by remember { mutableStateOf<Bitmap?>(null) }
    var isWorking by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    val realEsrganModel = remember {
        RestorationModel(
            id = "realesrgan-x4plus",
            displayName = "Real-ESRGAN x4plus",
            task = RestorationTask.IMAGE_UPSCALING,
            downloadUrl = "https://huggingface.co/skillsafe-ai/realesrgan-x4plus/resolve/107475e49b7d46412cc966481efccc3500955801/model.onnx",
            sha256 = "4851ec156207d271f5328605d0582eeb851e656227da8aca093ced9e60789291",
            maxBytes = RestorationModel.MAX_MODEL_BYTES,
        )
    }
    val runner = remember(store) { RestorationEngineRunner(store, RealEsrganOnnxEngine()) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { imageUri: Uri? ->
        if (imageUri != null) {
            scope.launch {
                val decoded = runCatching {
                    withContext(Dispatchers.IO) { decodePreviewBitmap(context, imageUri) }
                }
                decoded.onSuccess { bitmap ->
                    selectedImage?.takeIf { it !== bitmap && !it.isRecycled }?.recycle()
                    selectedImage = bitmap
                    restoredImage = null
                    selectedImageName = imageUri.lastPathSegment ?: "Selected image"
                    message = "Image loaded locally. It has not been uploaded."
                }.onFailure {
                    message = it.message ?: "Could not open this image."
                }
            }
        }
    }

    fun refreshModels() {
        installedModels = store.installedModels()
        selectedFolder = store.selectedFolder()
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri: Uri? ->
        if (treeUri != null) {
            runCatching {
                store.rememberFolder(treeUri)
                refreshModels()
                message = "Model folder access saved."
            }.onFailure {
                message = it.message ?: "Could not save access to that folder."
            }
        }
    }

    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("AI restoration", style = MaterialTheme.typography.headlineSmall)
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Model storage", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Optional AI models are stored in the folder you choose, not inside the app APK.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    selectedFolder?.lastPathSegment ?: "No model folder selected",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { folderPicker.launch(selectedFolder) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Text(if (selectedFolder == null) "Choose model folder" else "Change model folder")
                }
            }
        }

        Text("Choose an image", style = MaterialTheme.typography.titleMedium)
        Text(
            "Select a photo from this device. Restoration will run on-device; this selection does not upload it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = { imagePicker.launch("image/*") },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (selectedImage == null) "Select image" else "Choose another image")
        }
        selectedImage?.let { bitmap ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(selectedImageName ?: "Selected image", style = MaterialTheme.typography.titleSmall)
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Selected original image preview",
                        modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                        contentScale = ContentScale.Fit,
                    )
                    Text(
                        "${bitmap.width} × ${bitmap.height}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        Text("Restoration mode", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selectedTask == RestorationTask.IMAGE_UPSCALING,
                onClick = { selectedTask = RestorationTask.IMAGE_UPSCALING },
                label = { Text("Image upscaling") },
            )
            FilterChip(
                selected = selectedTask == RestorationTask.FACE_RESTORATION,
                onClick = { selectedTask = RestorationTask.FACE_RESTORATION },
                label = { Text("Face restoration") },
            )
        }
        if (selectedTask == RestorationTask.FACE_RESTORATION) {
            Text(
                "Face restoration is not available yet. The verified Real-ESRGAN model below performs image upscaling only; GFPGAN will be added separately.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(
            onClick = {
                val input = selectedImage ?: return@Button
                scope.launch {
                    isWorking = true
                    progress = 0f
                    message = null
                    try {
                        if (selectedTask != RestorationTask.IMAGE_UPSCALING) {
                            error("Face restoration is not available yet. Choose Image upscaling.")
                        }
                        if (store.installedModelUri(realEsrganModel) == null) {
                            check(selectedFolder != null) { "Choose a model folder before downloading the model." }
                            message = "Downloading and verifying Real-ESRGAN model…"
                            store.install(realEsrganModel) { downloaded, total ->
                                progress = if (total > 0L) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
                            }
                            refreshModels()
                        }
                        message = "Restoring image on this device…"
                        progress = 0f
                        val output = runner.run(realEsrganModel, input) { value -> progress = value }
                        restoredImage = output
                        message = "Restoration complete. The image has not been uploaded."
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        message = error.message ?: "Restoration failed."
                    } finally {
                        isWorking = false
                    }
                }
            },
            enabled = selectedImage != null && !isWorking,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (isWorking) "Working…" else "Restore image")
        }
        if (isWorking) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Text("Progress: ${(progress * 100).toInt()}%")
        }
        restoredImage?.let { output ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Restored result", style = MaterialTheme.typography.titleMedium)
                    Image(
                        bitmap = output.asImageBitmap(),
                        contentDescription = "Restored image preview",
                        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                        contentScale = ContentScale.Fit,
                    )
                    Text("${output.width} × ${output.height}", style = MaterialTheme.typography.labelMedium)
                    Button(
                        onClick = {
                            scope.launch {
                                message = runCatching {
                                    saveRestoredBitmap(context, output)
                                    "Saved to Pictures/SearchAndTranslate."
                                }.getOrElse { "Could not save image: ${it.message}" }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Save to Gallery") }
                }
            }
        }

        Text("Installed models", style = MaterialTheme.typography.titleMedium)
        if (installedModels.isEmpty()) {
            Text(
                "No models are registered in this app yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f, fill = true),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(installedModels, key = { it.id }) { model ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(model.displayName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${model.task.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }} · ${formatModelSize(model.sizeBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                if (model.isAccessible) "Available in selected folder" else "Unavailable — re-select the original folder",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (model.isAccessible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            )
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        val deleted = runCatching {
                                            store.removeInstalledModel(model.id)
                                        }.getOrDefault(false)
                                        refreshModels()
                                        message = if (deleted) "Model deleted." else "Could not delete this model. Check folder access and try again."
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Delete model")
                            }
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Available model", style = MaterialTheme.typography.titleSmall)
                Text("Real-ESRGAN x4plus · 4× image upscaling · downloaded on demand to your selected folder.", style = MaterialTheme.typography.bodySmall)
                Text("The model is checksum-verified before use. Model weights are not bundled in the APK.", style = MaterialTheme.typography.bodySmall)
            }
        }
        message?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}

private suspend fun saveRestoredBitmap(context: android.content.Context, bitmap: Bitmap) = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "restored-${System.currentTimeMillis()}.png")
        put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= 29) {
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SearchAndTranslate")
            put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: error("Gallery could not create an image entry")
    try {
        resolver.openOutputStream(uri, "w")?.use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Image encoding failed" }
        } ?: error("Gallery output stream is unavailable")
        if (Build.VERSION.SDK_INT >= 29) {
            val completed = ContentValues().apply { put(android.provider.MediaStore.Images.Media.IS_PENDING, 0) }
            resolver.update(uri, completed, null, null)
        }
    } catch (error: Throwable) {
        resolver.delete(uri, null, null)
        throw error
    }
}

private fun decodePreviewBitmap(context: android.content.Context, uri: Uri): Bitmap {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        ?: error("Cannot read selected image")
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Selected file is not a supported image" }
    val pixels = bounds.outWidth.toLong() * bounds.outHeight.toLong()
    require(pixels <= 100_000_000L) { "Image is too large to preview safely" }
    var sample = 1
    while ((bounds.outWidth.toLong() / sample) * (bounds.outHeight.toLong() / sample) > 8_000_000L) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        ?: error("Could not decode selected image")
}

private fun formatModelSize(bytes: Long): String {
    if (bytes <= 0L) return "Size unknown"
    val mib = bytes / (1024.0 * 1024.0)
    return if (mib >= 1024.0) "${"%.2f".format(mib / 1024.0)} GiB"
    else "${"%.1f".format(mib)} MiB"
}
