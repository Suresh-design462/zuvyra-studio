package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.media.DeviceCapabilities
import com.example.core.model.AspectRatioOption
import com.example.core.model.ResolutionPreset
import com.example.core.storage.InterfaceDensity
import com.example.core.storage.PreviewQuality
import com.example.core.storage.StorageBreakdown
import com.example.core.storage.ZuvyraSettings
import com.example.ui.components.StudioFormatter
import com.example.ui.components.ZuvyraLogoBadge
import com.example.ui.theme.AccentColorOption
import com.example.ui.theme.ThemeMode

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: ZuvyraSettings,
    storage: StorageBreakdown,
    capabilities: DeviceCapabilities,
    onRefreshStorage: () -> Unit,
    onClearTempFiles: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onAccentChange: (AccentColorOption) -> Unit,
    onDensityChange: (InterfaceDensity) -> Unit,
    onDefaultResolutionChange: (ResolutionPreset) -> Unit,
    onDefaultFpsChange: (Int) -> Unit,
    onDefaultAspectChange: (AspectRatioOption) -> Unit,
    onSnappingChange: (Boolean) -> Unit,
    onPreviewQualityChange: (PreviewQuality) -> Unit,
    onAutoSaveChange: (Int) -> Unit,
    onThumbCacheChange: (Int) -> Unit,
    onHardwareAccelChange: (Boolean) -> Unit,
    onBackgroundExportChange: (Boolean) -> Unit
) {
    var showLicencesDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { onRefreshStorage() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Studio Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        // Appearance
        item {
            SettingsSectionCard(title = "Appearance", icon = Icons.Default.Palette) {
                Text("Theme Mode", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { onThemeModeChange(mode) },
                            label = { Text(mode.label) }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Accent Palette", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AccentColorOption.entries.forEach { acc ->
                        val selected = settings.accentColor == acc
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(acc.primary)
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                                .clickable { onAccentChange(acc) }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Interface Density", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InterfaceDensity.entries.forEach { d ->
                        FilterChip(
                            selected = settings.density == d,
                            onClick = { onDensityChange(d) },
                            label = { Text(d.label) }
                        )
                    }
                }
            }
        }

        // Editor Defaults
        item {
            SettingsSectionCard(title = "Editor Defaults", icon = Icons.Default.Tune) {
                Text("Default Resolution", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(ResolutionPreset.P720, ResolutionPreset.P1080, ResolutionPreset.P1440, ResolutionPreset.P2160).forEach { r ->
                        FilterChip(selected = settings.defaultResolution == r, onClick = { onDefaultResolutionChange(r) }, label = { Text(r.label) })
                    }
                }

                Text("Default Frame Rate", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(24, 30, 50, 60).forEach { fps ->
                        FilterChip(selected = settings.defaultFps == fps, onClick = { onDefaultFpsChange(fps) }, label = { Text("$fps FPS") })
                    }
                }

                Text("Default Aspect Ratio", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(AspectRatioOption.RATIO_16_9, AspectRatioOption.RATIO_9_16, AspectRatioOption.RATIO_1_1, AspectRatioOption.RATIO_4_5).forEach { ratio ->
                        FilterChip(selected = settings.defaultAspectRatio == ratio, onClick = { onDefaultAspectChange(ratio) }, label = { Text(ratio.label) })
                    }
                }

                SettingsToggleRow(
                    title = "Timeline Snapping",
                    subtitle = "Snap clip edges to playhead and adjacent clips",
                    checked = settings.timelineSnapping,
                    onCheckedChange = onSnappingChange
                )

                Text("Auto-Save Interval", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(10, 15, 30, 60).forEach { sec ->
                        FilterChip(selected = settings.autoSaveIntervalSec == sec, onClick = { onAutoSaveChange(sec) }, label = { Text("${sec}s") })
                    }
                }
                Text("Default Export Location: ${settings.defaultExportFolderLabel}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Performance & Hardware Capabilities
        item {
            SettingsSectionCard(title = "Performance & Hardware", icon = Icons.Default.Memory) {
                Text(
                    "RAM: ${capabilities.totalRamMb} MB • CPU Cores: ${capabilities.cpuCores} • Thermal: ${capabilities.thermalStatusLabel}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Hardware Encoders: ${capabilities.supportedVideoEncoders.joinToString { it.label }} • 4K Encode: ${if (capabilities.supports4kEncoding) "Supported" else "Limited"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text("Preview Quality", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PreviewQuality.entries.forEach { q ->
                        FilterChip(selected = settings.previewQuality == q, onClick = { onPreviewQualityChange(q) }, label = { Text(q.label) })
                    }
                }

                Text("Thumbnail Cache Limit", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(128, 256, 512, 1024).forEach { mb ->
                        FilterChip(selected = settings.thumbnailCacheLimitMb == mb, onClick = { onThumbCacheChange(mb) }, label = { Text("$mb MB") })
                    }
                }

                SettingsToggleRow(
                    title = "Hardware-Accelerated Rendering",
                    subtitle = "Use MediaCodec & OpenGL ES GPU shaders",
                    checked = settings.preferHardwareAcceleration,
                    onCheckedChange = onHardwareAccelChange
                )
                SettingsToggleRow(
                    title = "Background Export Processing",
                    subtitle = "Continue rendering while navigating inside studio",
                    checked = settings.enableBackgroundExport,
                    onCheckedChange = onBackgroundExportChange
                )
            }
        }

        // Storage Breakdown
        item {
            SettingsSectionCard(title = "Local Storage", icon = Icons.Default.Storage) {
                StorageRow("Projects & Recovery", storage.projectsBytes)
                StorageRow("Thumbnail & Temp Cache", storage.cacheBytes)
                StorageRow("Exported Videos", storage.exportsBytes)
                StorageRow("Voiceovers, Freeze Frames & Derived", storage.tempAndDerivedBytes)
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                StorageRow("Total App Storage Used", storage.totalBytes, bold = true)
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = onClearTempFiles,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_clear_cache_button")
                ) {
                    Icon(Icons.Default.CleaningServices, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Clear Temporary & Cache Files")
                }
            }
        }

        // About & Privacy
        item {
            SettingsSectionCard(title = "About ZUVYRA", icon = Icons.Default.Info) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ZuvyraLogoBadge(size = 54.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("ZUVYRA Studio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Version 2.0.0 — 3D Studio & Offline VFX", style = MaterialTheme.typography.bodySmall)
                        Text("Developer: SURESH DIDEL", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "100% Offline Privacy: Zero internet permission, zero accounts, zero analytics, zero ads, zero watermarks.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                OutlinedButton(
                    onClick = { showLicencesDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open-Source Licences & Acknowledgements")
                }
            }
        }
    }

    if (showLicencesDialog) {
        AlertDialog(
            onDismissRequest = { showLicencesDialog = false },
            title = { Text("Open-Source Licences") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("• Jetpack Compose, Navigation, Room & DataStore — Apache License 2.0")
                    Text("• AndroidX Media3 (ExoPlayer, Transformer, Effect, Common) — Apache License 2.0")
                    Text("• Bundled ML Kit Selfie Segmentation (On-Device) — Google ML Kit Terms")
                    Text("• Kotlin & Kotlinx Coroutines / Serialization — Apache License 2.0")
                    Text("• Coil Image Loading — Apache License 2.0")
                    Text("• Bundled Fonts: Space Grotesk, Plus Jakarta Sans, JetBrains Mono — SIL Open Font License 1.1")
                }
            },
            confirmButton = { TextButton(onClick = { showLicencesDialog = false }) { Text("Close") } }
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun StorageRow(label: String, bytes: Long, bold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium)
        Text(
            StudioFormatter.formatBytes(bytes),
            style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        )
    }
}
