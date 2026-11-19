package com.example.ui.editor

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.media.DeviceCapabilities
import com.example.core.model.AspectRatioOption
import com.example.core.model.BitratePreset
import com.example.core.model.ExportConfig
import com.example.core.model.ExportFormat
import com.example.core.model.ResolutionPreset
import com.example.core.model.VideoCodecOption
import com.example.core.model.ZuvyraProject
import com.example.engine.ExportState
import com.example.engine.ZuvyraExportEngine
import com.example.ui.components.StudioFormatter
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportStudioPanel(
    project: ZuvyraProject,
    exportState: ExportState,
    exportEngine: ZuvyraExportEngine,
    capabilities: DeviceCapabilities,
    onConfigChange: (ExportConfig) -> Unit,
    onStartExport: (ExportConfig) -> Unit,
    onCancelExport: () -> Unit,
    onResetExport: () -> Unit,
    onOpenOutput: (File, Uri?) -> Unit,
    onShareOutput: (File, Uri?) -> Unit
) {
    var cfg by remember(project.id) { mutableStateOf(project.exportConfig) }
    val (w, h) = exportEngine.resolveExportDimensions(project, cfg)
    val estBytes = exportEngine.estimateFileSizeBytes(project, cfg)
    val estBitrateKbps = exportEngine.estimateBitrateKbps(cfg, w, h)

    fun update(next: ExportConfig) {
        cfg = next
        onConfigChange(next)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Export & Hardware Rendering Engine", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        when (exportState) {
            is ExportState.Running -> {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(exportState.stageLabel, style = MaterialTheme.typography.titleSmall)
                        LinearProgressIndicator(
                            progress = { exportState.progressPercent / 100f },
                            modifier = Modifier.fillMaxWidth().height(10.dp)
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("${exportState.progressPercent}%", fontFamily = JetBrainsMonoFontFamily, style = MaterialTheme.typography.titleMedium)
                            OutlinedButton(onClick = onCancelExport) {
                                Icon(Icons.Default.Cancel, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Cancel Export")
                            }
                        }
                    }
                }
            }
            is ExportState.Completed -> {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(8.dp))
                            Text("Export Verified & Finalized!", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "${exportState.outputFile.name} • ${StudioFormatter.formatBytes(exportState.fileSizeBytes)} • ${StudioFormatter.formatDurationShort(exportState.durationMs)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (exportState.galleryUri != null) {
                            Text("Saved to Movies/ZUVYRA in device Gallery", style = MaterialTheme.typography.labelSmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onOpenOutput(exportState.outputFile, exportState.galleryUri) }) {
                                Icon(Icons.Default.PlayCircle, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Open")
                            }
                            OutlinedButton(onClick = { onShareOutput(exportState.outputFile, exportState.galleryUri) }) {
                                Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Share")
                            }
                            TextButton(onClick = onResetExport) { Text("Done") }
                        }
                    }
                }
            }
            is ExportState.Failed -> {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Text("Export Failed", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Text(exportState.message, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = onResetExport) { Text("Dismiss") }
                    }
                }
            }
            else -> {
                Text("Format", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExportFormat.entries.forEach { fmt ->
                        FilterChip(selected = cfg.format == fmt, onClick = { update(cfg.copy(format = fmt)) }, label = { Text(fmt.label) })
                    }
                }

                if (cfg.format == ExportFormat.MP4 || cfg.format == ExportFormat.MOV) {
                    Text("Video Codec", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        VideoCodecOption.entries.forEach { codec ->
                            val supported = codec in capabilities.supportedVideoEncoders
                            FilterChip(
                                selected = cfg.codec == codec,
                                enabled = supported,
                                onClick = { update(cfg.copy(codec = codec)) },
                                label = { Text(if (supported) codec.label else "${codec.label} (N/A)") }
                            )
                        }
                    }
                }

                Text("Resolution ($w × $h)", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ResolutionPreset.entries.forEach { res ->
                        val allowed = res != ResolutionPreset.P2160 || capabilities.supports4kEncoding
                        FilterChip(
                            selected = cfg.resolution == res,
                            enabled = allowed,
                            onClick = { update(cfg.copy(resolution = res)) },
                            label = { Text(res.label) }
                        )
                    }
                }

                Text("Aspect Ratio", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AspectRatioOption.entries.filter { it != AspectRatioOption.CUSTOM }.forEach { ratio ->
                        FilterChip(selected = cfg.aspectRatio == ratio, onClick = { update(cfg.copy(aspectRatio = ratio)) }, label = { Text(ratio.label) })
                    }
                }

                Text("Frame Rate", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(24, 25, 30, 50, 60).forEach { fps ->
                        FilterChip(selected = cfg.fps == fps, onClick = { update(cfg.copy(fps = fps)) }, label = { Text("$fps") })
                    }
                }

                if (cfg.format == ExportFormat.MP4 || cfg.format == ExportFormat.MOV) {
                    Text("Bitrate (${estBitrateKbps / 1000f} Mbps)", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        BitratePreset.entries.forEach { bp ->
                            FilterChip(selected = cfg.bitratePreset == bp, onClick = { update(cfg.copy(bitratePreset = bp)) }, label = { Text(bp.label) })
                        }
                    }
                    if (cfg.bitratePreset == BitratePreset.CUSTOM) {
                        LabeledSlider("Custom Video Bitrate (Mbps)", cfg.customBitrateKbps / 1000f, 1f..60f) {
                            update(cfg.copy(customBitrateKbps = (it * 1000).toInt()))
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Save copy to Device Movies/ZUVYRA", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = cfg.exportToGallery, onCheckedChange = { update(cfg.copy(exportToGallery = it)) })
                    }
                }

                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Estimated Output Size", style = MaterialTheme.typography.labelMedium)
                            Text("Zero watermark • Hardware Media3 pipeline", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            text = StudioFormatter.formatBytes(estBytes),
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                Button(
                    onClick = { onStartExport(cfg) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("export_start_button")
                ) {
                    Icon(Icons.Default.Movie, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Export Video Now", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
