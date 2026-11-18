package com.fuzzyfly0.searchandtranslate.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fuzzyfly0.searchandtranslate.AssistEntryActivity

@Composable
fun CircleSearchHomeScreen(
    onOpenSettings: () -> Unit,
    onOpenRestoration: () -> Unit,
    onOpenLaunchMethods: () -> Unit,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxSize().background(colors.background).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Search & Translate", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("See it. Select it. Understand it.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            Surface(shape = CircleShape, color = colors.surfaceContainerHigh) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.clickable(onClick = onOpenSettings).padding(12.dp))
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth().height(250.dp),
            shape = RoundedCornerShape(30.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
        ) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(listOf(colors.primaryContainer, colors.tertiaryContainer))
                ).padding(22.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = CircleShape, color = colors.surface.copy(alpha = 0.78f)) {
                        Icon(Icons.Default.ImageSearch, contentDescription = null, tint = colors.primary, modifier = Modifier.size(76.dp).padding(18.dp))
                    }
                    Text("Search anything on your screen", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = colors.onSurface)
                    Text("Circle, highlight or select content to search and translate.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    Button(
                        onClick = {
                            runCatching { context.startActivity(Intent(context, AssistEntryActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        },
                        shape = RoundedCornerShape(50),
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null)
                        Text("  Start screen search")
                    }
                }
            }
        }
        Text("Explore", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FeatureCard(Modifier.weight(1f), "Translate", "Text on screen", Icons.Default.Language, onClick = {
                runCatching {
                    context.startActivity(
                        Intent(context, dev.davidv.translator.MainActivity::class.java)
                            .putExtra(dev.davidv.translator.MainActivity.EXTRA_OPEN_SETTINGS, true)
                            .putExtra(dev.davidv.translator.MainActivity.EXTRA_SETTINGS_SECTION, "translator")
                    )
                }
            })
            FeatureCard(Modifier.weight(1f), "History", "Recent activity", Icons.Default.History, onClick = onOpenSettings)
        }
        FeatureCard(Modifier.fillMaxWidth(), "AI image tools", "Upscale and restore images with optional on-device models", Icons.Default.Tune, onClick = onOpenRestoration)
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onOpenLaunchMethods, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Text("Set up launch methods & permissions")
        }
    }
}

@Composable
private fun FeatureCard(
    modifier: Modifier,
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(11.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
