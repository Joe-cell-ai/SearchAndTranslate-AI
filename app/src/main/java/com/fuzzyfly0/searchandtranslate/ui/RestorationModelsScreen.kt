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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fuzzyfly0.searchandtranslate.ai.restoration.SafRestorationModelStore

/**
 * Model storage setup. The model catalog remains intentionally unavailable until
 * the exact downloadable artifacts and inference contracts pass verification.
 */
@Composable
fun RestorationModelsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { SafRestorationModelStore(context) }
    var selectedFolder by remember { mutableStateOf(store.selectedFolder()) }
    var message by remember { mutableStateOf<String?>(null) }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri: Uri? ->
        if (treeUri != null) {
            runCatching {
                store.rememberFolder(treeUri)
                selectedFolder = treeUri
                message = "Model folder access saved."
            }.onFailure {
                message = it.message ?: "Could not save access to that folder."
            }
        }
    }

    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
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
                    "Choose where optional AI models will be stored. Model files stay outside the APK and can be removed from this folder when model management is enabled.",
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
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Model downloads are not enabled yet", style = MaterialTheme.typography.titleMedium)
                Text(
                    "The Real-ESRGAN ONNX file still needs direct binary inspection and a verified Android inference contract. GFPGAN also needs its own model and runtime validation. Downloads are deliberately disabled rather than offering an unverified model that may fail or damage images.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "No model has been downloaded by this screen. Your selected folder is only remembered for the future verified model catalog.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
