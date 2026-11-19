package com.example.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.model.AlphaKeyConfig
import com.example.core.model.AlphaKeyMode
import com.example.core.model.BgRemovalConfig
import com.example.core.model.BgRemovalMode
import com.example.core.model.Camera3DConfig
import com.example.core.model.CameraMotionPreset
import com.example.core.model.ChromaKeyConfig
import com.example.core.model.KeyframeProperty
import com.example.core.model.TimelineClip
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.io.File
import java.util.Locale

private val chromaPresetSwatches = listOf(
    0xFF00D838L to "Green",
    0xFF0048FFL to "Blue",
    0xFF00C2D1L to "Cyan",
    0xFFE11D48L to "Red",
    0xFFD946EFL to "Magenta"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeyingAndBgRemovalPanel(
    clip: TimelineClip,
    onUpdateChromaKey: (ChromaKeyConfig) -> Unit,
    onUpdateAlphaKey: (AlphaKeyConfig) -> Unit,
    onUpdateBgRemoval: (BgRemovalConfig) -> Unit,
    onRunBgRemoval: () -> Unit,
    onClearBgCutout: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    val chroma = clip.chromaKey
    val alpha = clip.alphaKey
    val bg = clip.bgRemoval

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Keying & Offline Background Removal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Chroma Key") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Alpha Key") })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("BG Removal") })
        }

        when (tab) {
            0 -> {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Enable GPU Chroma Key", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Switch(checked = chroma.enabled, onCheckedChange = { onUpdateChromaKey(chroma.copy(enabled = it)) })
                }
                Text("Key Color Swatch", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    chromaPresetSwatches.forEach { (argb, label) ->
                        val selected = chroma.keyColorArgb == argb
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(argb.toInt()))
                                    .border(if (selected) 2.5.dp else 1.dp, if (selected) Color.White else Color.Gray, CircleShape)
                                    .clickable { onUpdateChromaKey(chroma.copy(keyColorArgb = argb, enabled = true)) }
                            )
                            Text(label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                LabeledSlider("Color Similarity", chroma.similarity, 0.02f..0.85f, { "${(it * 100).toInt()}%" }) {
                    onUpdateChromaKey(chroma.copy(similarity = it))
                }
                LabeledSlider("Edge Smoothness", chroma.smoothness, 0.0f..0.5f, { "${(it * 100).toInt()}%" }) {
                    onUpdateChromaKey(chroma.copy(smoothness = it))
                }
                LabeledSlider("Spill Suppression", chroma.spillSuppression, 0f..1f, { "${(it * 100).toInt()}%" }) {
                    onUpdateChromaKey(chroma.copy(spillSuppression = it))
                }
                LabeledSlider("Edge Choke / Expand", chroma.edgeChoke, -0.35f..0.35f) {
                    onUpdateChromaKey(chroma.copy(edgeChoke = it))
                }
            }

            1 -> {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Enable GPU Alpha / Luma Key", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Switch(checked = alpha.enabled, onCheckedChange = { onUpdateAlphaKey(alpha.copy(enabled = it)) })
                }
                Text("Alpha Key Mode", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AlphaKeyMode.entries.forEach { mode ->
                        FilterChip(
                            selected = alpha.mode == mode,
                            onClick = { onUpdateAlphaKey(alpha.copy(mode = mode, enabled = true)) },
                            label = { Text(mode.name.replace('_', ' ')) }
                        )
                    }
                }
                LabeledSlider("Threshold", alpha.threshold, 0f..1f, { "${(it * 100).toInt()}%" }) {
                    onUpdateAlphaKey(alpha.copy(threshold = it))
                }
                LabeledSlider("Matte Feather", alpha.feather, 0f..0.6f, { "${(it * 100).toInt()}%" }) {
                    onUpdateAlphaKey(alpha.copy(feather = it))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Invert Key Matte", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = alpha.invert, onCheckedChange = { onUpdateAlphaKey(alpha.copy(invert = it)) })
                }
            }

            else -> {
                val hasCutout = !clip.maskedMediaCachePath.isNullOrBlank() && File(clip.maskedMediaCachePath).exists()
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (hasCutout) "Offline Cutout Ready" else "On-Device Subject Segmentation & Edge Matting",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (hasCutout) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Uses bundled ML Kit Selfie Segmentation & Sobel border saliency matting 100% offline.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Apply Cutout Matte", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = bg.enabled, onCheckedChange = { onUpdateBgRemoval(bg.copy(enabled = it)) })
                }

                Text("Extraction Mode", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BgRemovalMode.entries.forEach { mode ->
                        FilterChip(
                            selected = bg.mode == mode,
                            onClick = { onUpdateBgRemoval(bg.copy(mode = mode)) },
                            label = {
                                Text(
                                    when (mode) {
                                        BgRemovalMode.OFFLINE_PORTRAIT_ML -> "Portrait ML (Offline)"
                                        BgRemovalMode.SALIENCY_AUTO -> "Saliency Auto"
                                        BgRemovalMode.EDGE_MATTING -> "Edge Matting"
                                    }
                                )
                            }
                        )
                    }
                }

                LabeledSlider("Edge Feather", bg.edgeFeather, 0f..1f) { onUpdateBgRemoval(bg.copy(edgeFeather = it)) }
                LabeledSlider("Edge Refine Contrast", bg.edgeRefineStrength, 0f..1f) { onUpdateBgRemoval(bg.copy(edgeRefineStrength = it)) }
                LabeledSlider("Halo Decontamination", bg.softHaloRemoval, 0f..1f) { onUpdateBgRemoval(bg.copy(softHaloRemoval = it)) }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Diagnostic Mask Overlay Preview", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = bg.maskPreviewOverlay, onCheckedChange = { onUpdateBgRemoval(bg.copy(maskPreviewOverlay = it)) })
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRunBgRemoval, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.AutoFixHigh, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Extract Cutout Offline")
                    }
                    if (hasCutout) {
                        OutlinedButton(onClick = onClearBgCutout) {
                            Icon(Icons.Default.LayersClear, null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Camera3DRigPanel(
    clip: TimelineClip,
    onUpdateCamera3D: (Camera3DConfig) -> Unit,
    onAddKeyframe: (KeyframeProperty, Float) -> Unit
) {
    val cam = clip.camera3d ?: Camera3DConfig()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("3D Camera Motion Rig", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = { onUpdateCamera3D(Camera3DConfig()) }) {
                Icon(Icons.Default.RestartAlt, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Reset")
            }
        }

        Text("Camera Motion Preset", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CameraMotionPreset.entries.forEach { preset ->
                FilterChip(
                    selected = cam.preset == preset,
                    onClick = { onUpdateCamera3D(cam.copy(preset = preset)) },
                    label = { Text(preset.name.replace('_', ' ')) }
                )
            }
        }

        LabeledSlider("Motion Intensity", cam.motionIntensity, 0f..2f) { onUpdateCamera3D(cam.copy(motionIntensity = it)) }
        LabeledSlider("3D Depth Parallax", cam.depthParallax, 0f..2.5f) { onUpdateCamera3D(cam.copy(depthParallax = it)) }
        LabeledSlider("Handheld Organic Shake", cam.handheldShake, 0f..1f) { onUpdateCamera3D(cam.copy(handheldShake = it)) }

        HorizontalDivider()
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Lens & Camera Pose", style = MaterialTheme.typography.labelLarge)
            OutlinedButton(onClick = { onAddKeyframe(KeyframeProperty.CAMERA_FOV, cam.fovDeg) }) {
                Icon(Icons.Default.Diamond, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(4.dp))
                Text("Keyframe FOV")
            }
        }

        LabeledSlider("Lens FOV (°)", cam.fovDeg, 25f..110f, { "${cam.fovDeg.toInt()}°" }) {
            onUpdateCamera3D(cam.copy(fovDeg = it))
        }
        LabeledSlider("Camera X", cam.posX, -1.5f..1.5f) { onUpdateCamera3D(cam.copy(posX = it)) }
        LabeledSlider("Camera Y", cam.posY, -1.5f..1.5f) { onUpdateCamera3D(cam.copy(posY = it)) }
        LabeledSlider("Camera Z (Dolly)", cam.posZ, -2.5f..2.5f) { onUpdateCamera3D(cam.copy(posZ = it)) }
        LabeledSlider("Pitch (X°)", cam.rotX, -60f..60f, { "${it.toInt()}°" }) { onUpdateCamera3D(cam.copy(rotX = it)) }
        LabeledSlider("Yaw (Y°)", cam.rotY, -60f..60f, { "${it.toInt()}°" }) { onUpdateCamera3D(cam.copy(rotY = it)) }
        LabeledSlider("Roll (Z°)", cam.rotZ, -180f..180f, { "${it.toInt()}°" }) { onUpdateCamera3D(cam.copy(rotZ = it)) }
    }
}

@Composable
fun CameraTrackerPanel(
    clip: TimelineClip,
    targetX: Float,
    targetY: Float,
    statusLabel: String?,
    onResetTarget: () -> Unit,
    onRunTrack: (applyToNewNull: Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Offline Planar Camera Tracker", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Tap anywhere on the Video Preview above to position the tracking reticle on a high-contrast feature.",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = String.format(Locale.US, "Target: X=%.2f  Y=%.2f", targetX, targetY),
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    TextButton(onClick = onResetTarget) {
                        Text("Center")
                    }
                }
                if (!statusLabel.isNullOrBlank()) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Button(
            onClick = { onRunTrack(true) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.CenterFocusStrong, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Track & Bake to Linked Null Object")
        }

        OutlinedButton(
            onClick = { onRunTrack(false) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Diamond, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Track Motion Keyframes Directly on Clip")
        }

        Text(
            text = "Keyframes tracked for this clip: ${clip.keyframes.count { it.property == KeyframeProperty.POS_X || it.property == KeyframeProperty.POS_Y }}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
