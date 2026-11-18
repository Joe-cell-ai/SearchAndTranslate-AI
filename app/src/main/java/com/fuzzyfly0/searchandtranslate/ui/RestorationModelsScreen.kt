/*
 * Copyright (C) 2025 AKS-Labs (original author)
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.fuzzyfly0.searchandtranslate.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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

private fun formatModelSize(bytes: Long): String {
    if (bytes <= 0L) return "Size unknown"
    val mib = bytes / (1024.0 * 1024.0)
    return if (mib >= 1024.0) "${"%.2f".format(mib / 1024.0)} GiB"
    else "${"%.1f".format(mib)} MiB"
}
