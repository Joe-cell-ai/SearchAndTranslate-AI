/*
 * Copyright (C) 2025 AKS-Labs (original author)
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.fuzzyfly0.searchandtranslate.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
import com.fuzzyfly0.searchandtranslate.ai.restoration.SafRestorationModelStore
import kotlinx.coroutines.launch
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
        Button(
            onClick = { message = "Restoration is not available until a verified inference engine and model are integrated." },
            enabled = selectedImage != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Restore image")
        }
        Text(
            "Preview and model selection are ready for integration. The actual inference engine is not connected yet, so no output is fabricated.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

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
                Text("Model downloads are not enabled yet", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Real-ESRGAN still needs direct ONNX binary inspection and a verified Android inference contract. GFPGAN requires its own model and separate runtime validation. Downloads stay disabled until those checks pass.",
                    style = MaterialTheme.typography.bodySmall,
                )
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
