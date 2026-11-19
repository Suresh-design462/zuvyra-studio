package com.example.ui.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.model.BuiltInFilterType
import com.example.core.model.ClipAnimationConfig
import com.example.core.model.ColorAdjustments
import com.example.core.model.EasingType
import com.example.core.model.FitMode
import com.example.core.model.HslAdjustment
import com.example.core.model.HslColorChannel
import com.example.core.model.InAnimationType
import com.example.core.model.Keyframe
import com.example.core.model.KeyframeProperty
import com.example.core.model.OutAnimationType
import com.example.core.model.OverallAnimationType
import com.example.core.model.SpeedCurveConfig
import com.example.core.model.SpeedCurvePreset
import com.example.core.model.TimelineClip
import com.example.core.model.TransformConfig
import com.example.core.model.VideoEffectItem
import com.example.core.model.VideoEffectType
import com.example.core.storage.FilterPresetEntity
import com.example.engine.AnimationEvaluator
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.util.Locale

@Composable
fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    formatValue: (Float) -> String = { String.format(Locale.US, "%.2f", it) },
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(formatValue(value), fontFamily = JetBrainsMonoFontFamily, style = MaterialTheme.typography.labelSmall)
        }
        Slider(value = value.coerceIn(range), onValueChange = onValueChange, valueRange = range)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransformAndKeyframePanel(
    clip: TimelineClip,
    allClips: List<TimelineClip> = emptyList(),
    playheadMs: Long,
    hasCopiedKeyframe: Boolean,
    onUpdateTransform: (TransformConfig) -> Unit,
    onResetTransform: () -> Unit,
    onRotate90: () -> Unit,
    onFlipH: () -> Unit,
    onFlipV: () -> Unit,
    onSetFitMode: (FitMode) -> Unit,
    onSelectParent: (String?) -> Unit = {},
    onAddKeyframe: (KeyframeProperty, Float) -> Unit,
    onRemoveKeyframe: (String) -> Unit,
    onJumpKeyframe: (Boolean) -> Unit,
    onCopyKeyframe: (Keyframe) -> Unit,
    onPasteKeyframe: () -> Unit,
    onUpdateKeyframeEasing: (String, EasingType, Float, Float, Float, Float) -> Unit
) {
    val t = clip.transform
    val localMs = (playheadMs - clip.timelineStartMs).coerceIn(0L, clip.durationMs)
    var activeProp by remember { mutableStateOf(KeyframeProperty.POS_X) }
    val nearestKf = remember(clip.keyframes, localMs, activeProp) {
        clip.keyframes.filter { it.property == activeProp }.minByOrNull { kotlin.math.abs(it.timeOffsetMs - localMs) }
    }
    val parentCandidates = remember(allClips, clip.id) {
        allClips.filter { it.id != clip.id && it.mediaType != com.example.core.model.MediaType.CAMERA_3D }
    }
    val childLinks = remember(allClips, clip.id) {
        allClips.filter { it.transform.parentClipId == clip.id }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (clip.mediaType == com.example.core.model.MediaType.NULL_OBJECT) "Null Object & 3D Rig"
                else "3D Transform & Motion Control",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = onResetTransform) {
                Icon(Icons.Default.RestartAlt, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Reset")
            }
        }

        if (clip.mediaType == com.example.core.model.MediaType.NULL_OBJECT) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f))) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Null Controller (${childLinks.size} linked child layers)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (childLinks.isEmpty()) "Select another clip and choose this Null Object as its Parent Layer to drive multiple layers together."
                        else "Driving: ${childLinks.joinToString { it.displayName }}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // Quick Flip / Rotate / Fit / 3D Mode
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = onRotate90) {
                Icon(Icons.Default.RotateRight, null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("90°")
            }
            OutlinedButton(onClick = onFlipH) {
                Icon(Icons.Default.Flip, null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("Flip H")
            }
            OutlinedButton(onClick = onFlipV) { Text("Flip V") }
            FilterChip(
                selected = t.is3dLayer,
                onClick = { onUpdateTransform(t.copy(is3dLayer = !t.is3dLayer)) },
                label = { Text("3D Axis Gizmo") }
            )
            FitMode.entries.forEach { mode ->
                FilterChip(selected = t.fitMode == mode, onClick = { onSetFitMode(mode) }, label = { Text(mode.name) })
            }
        }

        // Null Object / Parent Layer Linking
        if (parentCandidates.isNotEmpty() && clip.mediaType != com.example.core.model.MediaType.CAMERA_3D) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Parent Layer / Null Object", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = t.parentClipId == null,
                        onClick = { onSelectParent(null) },
                        label = { Text("None (World)") }
                    )
                    parentCandidates.forEach { cand ->
                        val prefix = if (cand.mediaType == com.example.core.model.MediaType.NULL_OBJECT) "Null: " else ""
                        FilterChip(
                            selected = t.parentClipId == cand.id,
                            onClick = { onSelectParent(cand.id) },
                            label = { Text("$prefix${cand.displayName}") }
                        )
                    }
                }
            }
        }

        // Keyframe Controls Bar
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Keyframes (${clip.keyframes.size})", style = MaterialTheme.typography.labelLarge)
                    Row {
                        IconButton(onClick = { onJumpKeyframe(false) }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.ChevronLeft, "Previous Keyframe")
                        }
                        IconButton(onClick = {
                            val currentVal = when (activeProp) {
                                KeyframeProperty.POS_X -> t.posX
                                KeyframeProperty.POS_Y -> t.posY
                                KeyframeProperty.POS_Z -> t.posZ
                                KeyframeProperty.SCALE_X -> t.scaleX
                                KeyframeProperty.SCALE_Y -> t.scaleY
                                KeyframeProperty.SCALE_Z -> t.scaleZ
                                KeyframeProperty.ROTATION -> t.rotation
                                KeyframeProperty.ROTATION_X -> t.rotationX
                                KeyframeProperty.ROTATION_Y -> t.rotationY
                                KeyframeProperty.OPACITY -> t.opacity
                                KeyframeProperty.VOLUME -> clip.audioSettings.volume
                                KeyframeProperty.CAMERA_FOV -> clip.camera3d?.fovDeg ?: t.perspectiveFov
                            }
                            onAddKeyframe(activeProp, currentVal)
                        }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.Diamond, "Add Keyframe", tint = MaterialTheme.colorScheme.tertiary)
                        }
                        IconButton(onClick = { onJumpKeyframe(true) }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.ChevronRight, "Next Keyframe")
                        }
                        nearestKf?.let { kf ->
                            IconButton(onClick = { onCopyKeyframe(kf) }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Default.ContentCopy, "Copy Keyframe", modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { onRemoveKeyframe(kf.id) }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Default.Delete, "Delete Keyframe", modifier = Modifier.size(16.dp))
                            }
                        }
                        if (hasCopiedKeyframe) {
                            IconButton(onClick = onPasteKeyframe, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Default.ContentPaste, "Paste Keyframe", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KeyframeProperty.entries.forEach { prop ->
                        FilterChip(
                            selected = activeProp == prop,
                            onClick = { activeProp = prop },
                            label = { Text(prop.name.replace('_', ' ')) }
                        )
                    }
                }

                // Easing & Cubic Bezier Curve Preview
                nearestKf?.let { kf ->
                    Text("Easing for ${kf.property.name} @ ${kf.timeOffsetMs}ms", style = MaterialTheme.typography.labelSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        EasingType.entries.forEach { easing ->
                            FilterChip(
                                selected = kf.easing == easing,
                                onClick = {
                                    onUpdateKeyframeEasing(kf.id, easing, kf.bezierP1x, kf.bezierP1y, kf.bezierP2x, kf.bezierP2y)
                                },
                                label = { Text(easing.name.replace('_', ' ')) }
                            )
                        }
                    }
                    if (kf.easing == EasingType.CUBIC_BEZIER) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Canvas(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.5f))
                            ) {
                                val path = Path().apply {
                                    moveTo(0f, size.height)
                                    cubicTo(
                                        kf.bezierP1x * size.width, (1f - kf.bezierP1y) * size.height,
                                        kf.bezierP2x * size.width, (1f - kf.bezierP2y) * size.height,
                                        size.width, 0f
                                    )
                                }
                                drawPath(path, color = Color(0xFF38BDF8), style = Stroke(width = 3f))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                LabeledSlider("P1 (X/Y)", kf.bezierP1y, 0f..1f) {
                                    onUpdateKeyframeEasing(kf.id, kf.easing, kf.bezierP1x, it, kf.bezierP2x, kf.bezierP2y)
                                }
                                LabeledSlider("P2 (X/Y)", kf.bezierP2y, 0f..1f) {
                                    onUpdateKeyframeEasing(kf.id, kf.easing, kf.bezierP1x, kf.bezierP1y, kf.bezierP2x, it)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3D Position X/Y/Z
        Text("3D Position & Depth", style = MaterialTheme.typography.labelLarge)
        LabeledSlider("Position X", t.posX, -1.5f..1.5f) { onUpdateTransform(t.copy(posX = it)) }
        LabeledSlider("Position Y", t.posY, -1.5f..1.5f) { onUpdateTransform(t.copy(posY = it)) }
        LabeledSlider("Position Z (Depth)", t.posZ, -2.5f..2.5f) { onUpdateTransform(t.copy(posZ = it, is3dLayer = true)) }

        // 3D Scale X/Y/Z
        Text("3D Scale Axes", style = MaterialTheme.typography.labelLarge)
        LabeledSlider("Scale X", t.scaleX, 0.1f..4f) { onUpdateTransform(t.copy(scaleX = it)) }
        LabeledSlider("Scale Y", t.scaleY, 0.1f..4f) { onUpdateTransform(t.copy(scaleY = it)) }
        LabeledSlider("Scale Z (3D Depth)", t.scaleZ, 0.1f..4f) { onUpdateTransform(t.copy(scaleZ = it, is3dLayer = true)) }

        // 3D Rotations & Perspective
        Text("3D Orientation & Lens", style = MaterialTheme.typography.labelLarge)
        LabeledSlider("Rotation X (Pitch °)", t.rotationX, -180f..180f, { "${it.toInt()}°" }) {
            onUpdateTransform(t.copy(rotationX = it, is3dLayer = true))
        }
        LabeledSlider("Rotation Y (Yaw °)", t.rotationY, -180f..180f, { "${it.toInt()}°" }) {
            onUpdateTransform(t.copy(rotationY = it, is3dLayer = true))
        }
        LabeledSlider("Rotation Z (Roll °)", t.rotation, -360f..360f, { "${it.toInt()}°" }) {
            onUpdateTransform(t.copy(rotation = it))
        }
        LabeledSlider("Perspective FOV (°)", t.perspectiveFov, 25f..110f, { "${it.toInt()}°" }) {
            onUpdateTransform(t.copy(perspectiveFov = it, is3dLayer = true))
        }

        HorizontalDivider()
        LabeledSlider("Opacity", t.opacity, 0f..1f, { "${(it * 100).toInt()}%" }) { onUpdateTransform(t.copy(opacity = it)) }
        LabeledSlider("Anchor X", t.anchorX, 0f..1f) { onUpdateTransform(t.copy(anchorX = it)) }
        LabeledSlider("Anchor Y", t.anchorY, 0f..1f) { onUpdateTransform(t.copy(anchorY = it)) }
        LabeledSlider("Width Ratio", t.widthRatio, 0.2f..2f) { onUpdateTransform(t.copy(widthRatio = it)) }
        LabeledSlider("Height Ratio", t.heightRatio, 0.2f..2f) { onUpdateTransform(t.copy(heightRatio = it)) }

        HorizontalDivider()
        Text("Non-Destructive Crop", style = MaterialTheme.typography.labelLarge)
        LabeledSlider("Crop Left", t.cropLeft, 0f..0.45f) { onUpdateTransform(t.copy(cropLeft = it)) }
        LabeledSlider("Crop Right", t.cropRight, 0f..0.45f) { onUpdateTransform(t.copy(cropRight = it)) }
        LabeledSlider("Crop Top", t.cropTop, 0f..0.45f) { onUpdateTransform(t.copy(cropTop = it)) }
        LabeledSlider("Crop Bottom", t.cropBottom, 0f..0.45f) { onUpdateTransform(t.copy(cropBottom = it)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnimationPanel(
    clip: TimelineClip,
    onUpdateAnimation: (ClipAnimationConfig) -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    val anim = clip.animation

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("In / Overall / Out Animations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("In") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Overall") })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Out") })
        }

        when (tab) {
            0 -> {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    InAnimationType.entries.forEach { preset ->
                        FilterChip(
                            selected = anim.inType == preset,
                            onClick = { onUpdateAnimation(anim.copy(inType = preset)) },
                            label = { Text(preset.name.replace('_', ' ')) }
                        )
                    }
                }
                LabeledSlider("In Duration (ms)", anim.inDurationMs.toFloat(), 100f..2500f, { "${it.toInt()} ms" }) {
                    onUpdateAnimation(anim.copy(inDurationMs = it.toLong()))
                }
                LabeledSlider("In Intensity", anim.inIntensity, 0.1f..2f) {
                    onUpdateAnimation(anim.copy(inIntensity = it))
                }
            }
            1 -> {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OverallAnimationType.entries.forEach { preset ->
                        FilterChip(
                            selected = anim.overallType == preset,
                            onClick = { onUpdateAnimation(anim.copy(overallType = preset)) },
                            label = { Text(preset.name.replace('_', ' ')) }
                        )
                    }
                }
                LabeledSlider("Loop Speed", anim.overallSpeed, 0.2f..3f) {
                    onUpdateAnimation(anim.copy(overallSpeed = it))
                }
                LabeledSlider("Intensity", anim.overallIntensity, 0.1f..2f) {
                    onUpdateAnimation(anim.copy(overallIntensity = it))
                }
            }
            else -> {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutAnimationType.entries.forEach { preset ->
                        FilterChip(
                            selected = anim.outType == preset,
                            onClick = { onUpdateAnimation(anim.copy(outType = preset)) },
                            label = { Text(preset.name.replace('_', ' ')) }
                        )
                    }
                }
                LabeledSlider("Out Duration (ms)", anim.outDurationMs.toFloat(), 100f..2500f, { "${it.toInt()} ms" }) {
                    onUpdateAnimation(anim.copy(outDurationMs = it.toLong()))
                }
                LabeledSlider("Out Intensity", anim.outIntensity, 0.1f..2f) {
                    onUpdateAnimation(anim.copy(outIntensity = it))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpeedCurvePanel(
    clip: TimelineClip,
    onUpdateSpeed: (SpeedCurveConfig) -> Unit
) {
    val cfg = clip.speedConfig
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Speed Control & Speed Curve", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        LabeledSlider("Standard Speed (0.1x – 16x)", cfg.constantSpeed, 0.1f..16f, { String.format(Locale.US, "%.2fx", it) }) {
            onUpdateSpeed(cfg.copy(preset = SpeedCurvePreset.LINEAR, constantSpeed = it, points = listOf(com.example.core.model.SpeedPoint(0f, it), com.example.core.model.SpeedPoint(1f, it))))
        }

        Text("Speed Curve Presets", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SpeedCurvePreset.entries.forEach { preset ->
                FilterChip(
                    selected = cfg.preset == preset,
                    onClick = {
                        val pts = AnimationEvaluator.presetSpeedPoints(preset)
                        onUpdateSpeed(cfg.copy(preset = preset, points = pts))
                    },
                    label = { Text(preset.name.replace('_', ' ')) }
                )
            }
        }

        // Visual Speed Graph
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0A0B10))
                .padding(10.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(90.dp)) {
                val pts = cfg.points.sortedBy { it.normalizedTime }
                if (pts.size >= 2) {
                    val path = Path()
                    pts.forEachIndexed { idx, p ->
                        val x = p.normalizedTime * size.width
                        val y = size.height - ((p.speedMultiplier.coerceIn(0.1f, 6f) / 6f) * size.height)
                        if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        drawCircle(Color(0xFF8B5CF6), radius = 6f, center = Offset(x, y))
                    }
                    drawPath(path, color = Color(0xFF38BDF8), style = Stroke(width = 3f))
                }
            }
        }

        // Adjustable Curve Points
        if (cfg.preset != SpeedCurvePreset.LINEAR) {
            cfg.points.forEachIndexed { idx, pt ->
                LabeledSlider(
                    label = "Point ${idx + 1} (${(pt.normalizedTime * 100).toInt()}%)",
                    value = pt.speedMultiplier,
                    range = 0.1f..8f,
                    formatValue = { String.format(Locale.US, "%.2fx", it) }
                ) { newMult ->
                    val updated = cfg.points.toMutableList()
                    updated[idx] = pt.copy(speedMultiplier = newMult)
                    onUpdateSpeed(cfg.copy(preset = SpeedCurvePreset.CUSTOM, points = updated))
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Preserve Audio Pitch", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = cfg.preservePitch, onCheckedChange = { onUpdateSpeed(cfg.copy(preservePitch = it)) })
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Mute Audio During Speed Ramp", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = cfg.muteAudioOnSpeedChange, onCheckedChange = { onUpdateSpeed(cfg.copy(muteAudioOnSpeedChange = it)) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorAndFiltersPanel(
    clip: TimelineClip,
    customPresets: List<FilterPresetEntity>,
    onUpdateColor: (ColorAdjustments) -> Unit,
    onSavePreset: (String, ColorAdjustments) -> Unit,
    onApplyPreset: (FilterPresetEntity) -> Unit,
    onImportCubeLut: (Uri) -> Unit
) {
    val c = clip.colorAdjustments
    var tab by remember { mutableIntStateOf(0) }
    var activeHsl by remember { mutableStateOf(HslColorChannel.RED) }
    var showSaveDialog by remember { mutableStateOf(false) }

    val lutLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onImportCubeLut(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Colour Correction & Filters", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row {
                IconButton(onClick = { showSaveDialog = true }) {
                    Icon(Icons.Default.BookmarkAdd, contentDescription = "Save Preset")
                }
                IconButton(onClick = { onUpdateColor(ColorAdjustments()) }) {
                    Icon(Icons.Default.RestartAlt, contentDescription = "Reset Colour")
                }
            }
        }

        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Filters & LUT") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Adjust") })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("HSL") })
        }

        when (tab) {
            0 -> {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BuiltInFilterType.entries.forEach { f ->
                        FilterChip(
                            selected = c.filterType == f,
                            onClick = { onUpdateColor(c.copy(filterType = f)) },
                            label = { Text(f.name.replace('_', ' ')) }
                        )
                    }
                }
                if (c.filterType != BuiltInFilterType.NONE) {
                    LabeledSlider("Filter Intensity", c.filterIntensity, 0f..1f, { "${(it * 100).toInt()}%" }) {
                        onUpdateColor(c.copy(filterIntensity = it))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { lutLauncher.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Import .cube LUT")
                    }
                    if (!c.customLutPath.isNullOrBlank()) {
                        TextButton(onClick = { onUpdateColor(c.copy(customLutPath = null)) }) {
                            Text("Clear LUT")
                        }
                    }
                }
                if (customPresets.isNotEmpty()) {
                    Text("Saved Local Presets", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        customPresets.forEach { preset ->
                            FilterChip(selected = false, onClick = { onApplyPreset(preset) }, label = { Text(preset.name) })
                        }
                    }
                }
            }
            1 -> {
                LabeledSlider("Brightness", c.brightness, -1f..1f) { onUpdateColor(c.copy(brightness = it)) }
                LabeledSlider("Contrast", c.contrast, -1f..1f) { onUpdateColor(c.copy(contrast = it)) }
                LabeledSlider("Saturation", c.saturation, -1f..1f) { onUpdateColor(c.copy(saturation = it)) }
                LabeledSlider("Exposure", c.exposure, -1f..1f) { onUpdateColor(c.copy(exposure = it)) }
                LabeledSlider("Temperature", c.temperature, -1f..1f) { onUpdateColor(c.copy(temperature = it)) }
                LabeledSlider("Tint", c.tint, -1f..1f) { onUpdateColor(c.copy(tint = it)) }
                LabeledSlider("Highlights", c.highlights, -1f..1f) { onUpdateColor(c.copy(highlights = it)) }
                LabeledSlider("Shadows", c.shadows, -1f..1f) { onUpdateColor(c.copy(shadows = it)) }
                LabeledSlider("Whites", c.whites, -1f..1f) { onUpdateColor(c.copy(whites = it)) }
                LabeledSlider("Blacks", c.blacks, -1f..1f) { onUpdateColor(c.copy(blacks = it)) }
                LabeledSlider("Fade", c.fade, 0f..1f) { onUpdateColor(c.copy(fade = it)) }
                LabeledSlider("Sharpness", c.sharpness, 0f..1f) { onUpdateColor(c.copy(sharpness = it)) }
                LabeledSlider("Vignette", c.vignette, 0f..1f) { onUpdateColor(c.copy(vignette = it)) }
            }
            else -> {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HslColorChannel.entries.forEach { ch ->
                        FilterChip(selected = activeHsl == ch, onClick = { activeHsl = ch }, label = { Text(ch.name) })
                    }
                }
                val adj = c.hslChannels[activeHsl] ?: HslAdjustment()
                LabeledSlider("${activeHsl.name} Hue", adj.hueShift, -1f..1f) {
                    onUpdateColor(c.copy(hslChannels = c.hslChannels + (activeHsl to adj.copy(hueShift = it))))
                }
                LabeledSlider("${activeHsl.name} Saturation", adj.saturation, -1f..1f) {
                    onUpdateColor(c.copy(hslChannels = c.hslChannels + (activeHsl to adj.copy(saturation = it))))
                }
                LabeledSlider("${activeHsl.name} Luminance", adj.luminance, -1f..1f) {
                    onUpdateColor(c.copy(hslChannels = c.hslChannels + (activeHsl to adj.copy(luminance = it))))
                }
            }
        }
    }

    if (showSaveDialog) {
        var presetName by remember { mutableStateOf("My Grade") }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save Filter Preset Locally") },
            text = {
                OutlinedTextField(value = presetName, onValueChange = { presetName = it }, label = { Text("Preset Name") }, singleLine = true)
            },
            confirmButton = {
                Button(onClick = { onSavePreset(presetName, c); showSaveDialog = false }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoEffectsPanel(
    clip: TimelineClip,
    onAddEffect: (VideoEffectType) -> Unit,
    onUpdateEffects: (List<VideoEffectItem>) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("GPU Video Effects Stack (${clip.videoEffects.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        if (clip.videoEffects.isNotEmpty()) {
            clip.videoEffects.forEachIndexed { idx, fx ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = fx.enabled,
                                onCheckedChange = { en ->
                                    onUpdateEffects(clip.videoEffects.map { if (it.id == fx.id) it.copy(enabled = en) else it })
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(fx.type.name.replace('_', ' '), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                if (idx > 0) {
                                    val list = clip.videoEffects.toMutableList()
                                    val item = list.removeAt(idx)
                                    list.add(idx - 1, item)
                                    onUpdateEffects(list)
                                }
                            }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.ArrowUpward, null, modifier = Modifier.size(16.dp)) }
                            IconButton(onClick = {
                                if (idx < clip.videoEffects.lastIndex) {
                                    val list = clip.videoEffects.toMutableList()
                                    val item = list.removeAt(idx)
                                    list.add(idx + 1, item)
                                    onUpdateEffects(list)
                                }
                            }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.ArrowDownward, null, modifier = Modifier.size(16.dp)) }
                            IconButton(onClick = {
                                onUpdateEffects(clip.videoEffects + fx.copy(id = java.util.UUID.randomUUID().toString()))
                            }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp)) }
                            IconButton(onClick = {
                                onUpdateEffects(clip.videoEffects.filterNot { it.id == fx.id })
                            }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp)) }
                        }
                        LabeledSlider("Intensity", fx.intensity, 0f..1f, { "${(it * 100).toInt()}%" }) { newInt ->
                            onUpdateEffects(clip.videoEffects.map { if (it.id == fx.id) it.copy(intensity = newInt) else it })
                        }
                    }
                }
            }
            HorizontalDivider()
        }

        Text("Add Effect", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            VideoEffectType.entries.forEach { fxType ->
                OutlinedButton(onClick = { onAddEffect(fxType) }) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(fxType.name.replace('_', ' '))
                }
            }
        }
    }
}
