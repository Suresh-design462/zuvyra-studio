package com.example.ui.editor

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.core.media.LocalRecording
import com.example.core.media.VoiceRecorderEngine
import com.example.core.model.AspectRatioOption
import com.example.core.model.BlendModeType
import com.example.core.model.ClipAudioSettings
import com.example.core.model.ClipTransition
import com.example.core.model.EqPreset
import com.example.core.model.HandwritingData
import com.example.core.model.LayerStyleConfig
import com.example.core.model.ResolutionPreset
import com.example.core.model.StickerCategory
import com.example.core.model.StickerOverlayData
import com.example.core.model.StickerShapeKind
import com.example.core.model.TextAlignment
import com.example.core.model.TextEffectType
import com.example.core.model.TextOverlayData
import com.example.core.model.TimelineClip
import com.example.core.model.TimelineTrack
import com.example.core.model.TransitionDirection
import com.example.core.model.TransitionType
import com.example.core.model.VolumeEnvelopePoint
import com.example.core.model.ZuvyraProject
import com.example.core.storage.CustomFontEntity
import com.example.core.storage.StickerAssetEntity
import com.example.ui.components.StudioFormatter
import com.example.ui.theme.JetBrainsMonoFontFamily

private val studioColorSwatches = listOf(
    0xFFFFFFFFL, 0xFF000000L, 0xFF8B5CF6L, 0xFF38BDF8L,
    0xFFF43F5EL, 0xFFF59E0BL, 0xFF10B981L, 0xFFE879F9L
)

@Composable
private fun ColorSwatchRow(
    label: String,
    selectedArgb: Long,
    onSelect: (Long) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            studioColorSwatches.forEach { argb ->
                val selected = selectedArgb == argb
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(argb.toInt()))
                        .border(
                            width = if (selected) 2.5.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else Color.Gray,
                            shape = CircleShape
                        )
                        .clickable { onSelect(argb) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StyleAndBlendPanel(
    clip: TimelineClip,
    onUpdateStyle: (LayerStyleConfig) -> Unit
) {
    val s = clip.layerStyle
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Blending, Stroke, Border, Shadow & Glow", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Blend Mode", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BlendModeType.entries.forEach { mode ->
                FilterChip(
                    selected = s.blendMode == mode,
                    onClick = { onUpdateStyle(s.copy(blendMode = mode)) },
                    label = { Text(mode.name.replace('_', ' ')) }
                )
            }
        }
        HorizontalDivider()
        Text("Stroke & Outline", style = MaterialTheme.typography.labelLarge)
        LabeledSlider("Stroke Width", s.strokeWidth, 0f..20f) { onUpdateStyle(s.copy(strokeWidth = it)) }
        LabeledSlider("Stroke Opacity", s.strokeOpacity, 0f..1f) { onUpdateStyle(s.copy(strokeOpacity = it)) }
        ColorSwatchRow("Stroke Color", s.strokeColorArgb) { onUpdateStyle(s.copy(strokeColorArgb = it)) }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Inner Outline", style = MaterialTheme.typography.bodySmall)
            Switch(checked = s.innerOutline, onCheckedChange = { onUpdateStyle(s.copy(innerOutline = it)) })
        }

        HorizontalDivider()
        Text("Border", style = MaterialTheme.typography.labelLarge)
        LabeledSlider("Border Thickness", s.borderThickness, 0f..24f) { onUpdateStyle(s.copy(borderThickness = it)) }
        LabeledSlider("Corner Radius", s.borderCornerRadius, 0f..64f) { onUpdateStyle(s.copy(borderCornerRadius = it)) }
        ColorSwatchRow("Border Color", s.borderColorArgb) { onUpdateStyle(s.copy(borderColorArgb = it)) }

        HorizontalDivider()
        Text("Shadow & Glow", style = MaterialTheme.typography.labelLarge)
        LabeledSlider("Shadow Blur", s.shadowBlur, 0f..32f) { onUpdateStyle(s.copy(shadowBlur = it)) }
        LabeledSlider("Shadow Opacity", s.shadowOpacity, 0f..1f) { onUpdateStyle(s.copy(shadowOpacity = it)) }
        LabeledSlider("Shadow Offset X", s.shadowOffsetX, -30f..30f) { onUpdateStyle(s.copy(shadowOffsetX = it)) }
        LabeledSlider("Shadow Offset Y", s.shadowOffsetY, -30f..30f) { onUpdateStyle(s.copy(shadowOffsetY = it)) }
        LabeledSlider("Glow Intensity", s.glowIntensity, 0f..1f) { onUpdateStyle(s.copy(glowIntensity = it)) }
        LabeledSlider("Glow Radius", s.glowRadius, 2f..40f) { onUpdateStyle(s.copy(glowRadius = it)) }
        ColorSwatchRow("Glow Color", s.glowColorArgb) { onUpdateStyle(s.copy(glowColorArgb = it)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TextEditorPanel(
    clip: TimelineClip,
    customFonts: List<CustomFontEntity>,
    onUpdateText: (TextOverlayData) -> Unit,
    onImportFont: (Uri, String) -> Unit
) {
    val t = clip.textData ?: TextOverlayData()
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onImportFont(it, "Custom TTF/OTF") }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Text & Typography Studio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = t.text,
            onValueChange = { onUpdateText(t.copy(text = it)) },
            label = { Text("Text Content (English / हिन्दी / Any Script)") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3
        )

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onUpdateText(t.copy(isBold = !t.isBold)) }) {
                Icon(Icons.Default.FormatBold, "Bold", tint = if (t.isBold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { onUpdateText(t.copy(isItalic = !t.isItalic)) }) {
                Icon(Icons.Default.FormatItalic, "Italic", tint = if (t.isItalic) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { onUpdateText(t.copy(isUnderline = !t.isUnderline)) }) {
                Icon(Icons.Default.FormatUnderlined, "Underline", tint = if (t.isUnderline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextAlignment.entries.forEach { al ->
                FilterChip(selected = t.alignment == al, onClick = { onUpdateText(t.copy(alignment = al)) }, label = { Text(al.name) })
            }
        }

        // Font selector + Custom TTF/OTF Importer
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Fonts", style = MaterialTheme.typography.labelLarge)
            OutlinedButton(onClick = { fontPicker.launch(arrayOf("*/*")) }) {
                Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("Import TTF/OTF")
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Space Grotesk", "Plus Jakarta Sans", "JetBrains Mono", "Sans Serif", "Serif", "Monospace").forEach { fName ->
                FilterChip(
                    selected = t.fontFamilyName == fName && t.customFontPath == null,
                    onClick = { onUpdateText(t.copy(fontFamilyName = fName, customFontPath = null)) },
                    label = { Text(fName) }
                )
            }
            customFonts.forEach { cf ->
                FilterChip(
                    selected = t.customFontPath == cf.filePath,
                    onClick = { onUpdateText(t.copy(fontFamilyName = cf.name, customFontPath = cf.filePath)) },
                    label = { Text(cf.name) }
                )
            }
        }

        LabeledSlider("Font Size (sp)", t.fontSizeSp, 12f..120f) { onUpdateText(t.copy(fontSizeSp = it)) }
        LabeledSlider("Letter Spacing", t.letterSpacingEm, -0.1f..0.6f) { onUpdateText(t.copy(letterSpacingEm = it)) }
        LabeledSlider("Line Spacing", t.lineHeightMultiplier, 0.8f..2.5f) { onUpdateText(t.copy(lineHeightMultiplier = it)) }
        LabeledSlider("Opacity", t.textOpacity, 0f..1f) { onUpdateText(t.copy(textOpacity = it)) }

        ColorSwatchRow("Text Color", t.colorArgb) { onUpdateText(t.copy(colorArgb = it, useGradient = false)) }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Gradient Fill", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = t.useGradient, onCheckedChange = { onUpdateText(t.copy(useGradient = it)) })
        }

        LabeledSlider("Stroke / Outline Width", t.strokeWidth, 0f..12f) { onUpdateText(t.copy(strokeWidth = it)) }
        ColorSwatchRow("Stroke Color", t.strokeColorArgb) { onUpdateText(t.copy(strokeColorArgb = it)) }
        LabeledSlider("Shadow Blur", t.shadowBlur, 0f..24f) { onUpdateText(t.copy(shadowBlur = it)) }
        LabeledSlider("Glow Radius", t.glowRadius, 0f..28f) { onUpdateText(t.copy(glowRadius = it)) }
        LabeledSlider("Background Opacity", t.backgroundOpacity, 0f..1f) { onUpdateText(t.copy(backgroundOpacity = it)) }
        LabeledSlider("Background Corner Radius", t.backgroundCornerRadius, 0f..40f) { onUpdateText(t.copy(backgroundCornerRadius = it)) }
        LabeledSlider("Background Padding", t.backgroundPaddingDp, 0f..32f) { onUpdateText(t.copy(backgroundPaddingDp = it)) }
        LabeledSlider("Border Width", t.borderWidth, 0f..10f) { onUpdateText(t.copy(borderWidth = it)) }

        Text("Text Animation Effect", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextEffectType.entries.forEach { eff ->
                FilterChip(selected = t.textEffect == eff, onClick = { onUpdateText(t.copy(textEffect = eff)) }, label = { Text(eff.name) })
            }
        }
    }
}

@Composable
fun HandwritingPanel(
    clip: TimelineClip,
    brushColorArgb: Long,
    brushSize: Float,
    brushOpacity: Float,
    isEraser: Boolean,
    onBrushColor: (Long) -> Unit,
    onBrushSize: (Float) -> Unit,
    onBrushOpacity: (Float) -> Unit,
    onEraserToggle: (Boolean) -> Unit,
    onUndoStroke: () -> Unit,
    onToggleAnimateDraw: (Boolean) -> Unit
) {
    val hw = clip.handwritingData ?: HandwritingData()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Handwriting Studio (${hw.strokes.size} strokes)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = onUndoStroke) {
                Icon(Icons.Default.Undo, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Undo Stroke")
            }
        }
        Text("Draw directly on the video preview above with your finger or stylus.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)

        ColorSwatchRow("Brush Color", brushColorArgb, onBrushColor)
        LabeledSlider("Brush Size", brushSize, 2f..40f) { onBrushSize(it) }
        LabeledSlider("Brush Opacity", brushOpacity, 0.1f..1f) { onBrushOpacity(it) }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Eraser Mode", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = isEraser, onCheckedChange = onEraserToggle)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Animate Drawing Over Time", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = hw.animateStrokes, onCheckedChange = onToggleAnimateDraw)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StickersLibraryPanel(
    selectedClip: TimelineClip?,
    customStickers: List<StickerAssetEntity>,
    onAddSticker: (StickerOverlayData, String) -> Unit,
    onUpdateSticker: (StickerOverlayData) -> Unit,
    onImportCustomSticker: (Uri) -> Unit
) {
    val stickerPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onImportCustomSticker(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Local Stickers & Graphics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = { stickerPicker.launch(arrayOf("image/png", "image/webp", "image/*")) }) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("Import PNG/WEBP")
            }
        }

        Text("Shapes & Graphics", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            StickerShapeKind.entries.forEach { kind ->
                OutlinedButton(onClick = {
                    onAddSticker(
                        StickerOverlayData(category = StickerCategory.SHAPE, shapeKind = kind),
                        kind.name.replace('_', ' ')
                    )
                }) { Text(kind.name.replace('_', ' ')) }
            }
        }

        Text("Emojis", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("🔥", "✨", "🎬", "❤️", "⚡", "🎵", "⭐", "🚀", "👑", "💯").forEach { em ->
                OutlinedButton(onClick = {
                    onAddSticker(StickerOverlayData(category = StickerCategory.EMOJI, emoji = em), em)
                }) { Text(em) }
            }
        }

        if (customStickers.isNotEmpty()) {
            Text("Personal Sticker Collection", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                customStickers.forEach { st ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            onAddSticker(StickerOverlayData(category = StickerCategory.CUSTOM_IMAGE, customImageUri = st.uriString), st.name)
                        },
                        label = { Text(st.name) }
                    )
                }
            }
        }

        selectedClip?.stickerData?.let { cur ->
            HorizontalDivider()
            Text("Selected Sticker Style", style = MaterialTheme.typography.labelLarge)
            ColorSwatchRow("Primary Color", cur.primaryColorArgb) { onUpdateSticker(cur.copy(primaryColorArgb = it)) }
            ColorSwatchRow("Stroke Color", cur.strokeColorArgb) { onUpdateSticker(cur.copy(strokeColorArgb = it)) }
            LabeledSlider("Stroke Width", cur.strokeWidth, 0f..14f) { onUpdateSticker(cur.copy(strokeWidth = it)) }
            LabeledSlider("Shadow Blur", cur.shadowBlur, 0f..20f) { onUpdateSticker(cur.copy(shadowBlur = it)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransitionEditorPanel(
    transition: ClipTransition,
    onUpdateTransition: (ClipTransition) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Clip Transition", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TransitionType.entries.forEach { type ->
                FilterChip(
                    selected = transition.type == type,
                    onClick = { onUpdateTransition(transition.copy(type = type)) },
                    label = { Text(type.name.replace('_', ' ')) }
                )
            }
        }
        LabeledSlider("Duration (ms)", transition.durationMs.toFloat(), 150f..2500f, { "${it.toInt()} ms" }) {
            onUpdateTransition(transition.copy(durationMs = it.toLong()))
        }
        LabeledSlider("Intensity", transition.intensity, 0.2f..2f) {
            onUpdateTransition(transition.copy(intensity = it))
        }
        Text("Direction", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TransitionDirection.entries.forEach { dir ->
                FilterChip(selected = transition.direction == dir, onClick = { onUpdateTransition(transition.copy(direction = dir)) }, label = { Text(dir.name) })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AudioStudioPanel(
    clip: TimelineClip,
    playheadMs: Long,
    onUpdateAudio: (ClipAudioSettings) -> Unit,
    onApplyEqPreset: (EqPreset) -> Unit
) {
    val a = clip.audioSettings
    val eq = a.eqSettings
    val fx = a.audioEffects
    val localMs = (playheadMs - clip.timelineStartMs).coerceIn(0L, clip.durationMs)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Audio Editing Studio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        LabeledSlider("Volume", a.volume, 0f..2f, { "${(it * 100).toInt()}%" }) { onUpdateAudio(a.copy(volume = it)) }
        LabeledSlider("Stereo Pan", a.pan, -1f..1f, { if (it < -0.05f) "L ${(-it * 100).toInt()}" else if (it > 0.05f) "R ${(it * 100).toInt()}" else "Center" }) {
            onUpdateAudio(a.copy(pan = it))
        }
        LabeledSlider("Fade In (ms)", a.fadeInMs.toFloat(), 0f..4000f, { "${it.toInt()} ms" }) { onUpdateAudio(a.copy(fadeInMs = it.toLong())) }
        LabeledSlider("Fade Out (ms)", a.fadeOutMs.toFloat(), 0f..4000f, { "${it.toInt()} ms" }) { onUpdateAudio(a.copy(fadeOutMs = it.toLong())) }

        // Volume Envelope Automation Curve
        HorizontalDivider()
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Volume Envelope (${a.envelopePoints.size} pts)", style = MaterialTheme.typography.labelLarge)
            Row {
                OutlinedButton(onClick = {
                    val pts = (a.envelopePoints.filterNot { kotlin.math.abs(it.timeOffsetMs - localMs) < 80L } +
                        VolumeEnvelopePoint(localMs, a.volume)).sortedBy { it.timeOffsetMs }
                    onUpdateAudio(a.copy(envelopePoints = pts))
                }) {
                    Icon(Icons.Default.AddCircle, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Point")
                }
                if (a.envelopePoints.isNotEmpty()) {
                    TextButton(onClick = { onUpdateAudio(a.copy(envelopePoints = emptyList())) }) { Text("Clear") }
                }
            }
        }
        if (a.envelopePoints.isNotEmpty()) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
            ) {
                val sorted = a.envelopePoints.sortedBy { it.timeOffsetMs }
                val dur = clip.durationMs.coerceAtLeast(1L).toFloat()
                val path = Path()
                sorted.forEachIndexed { i, pt ->
                    val x = (pt.timeOffsetMs / dur).coerceIn(0f, 1f) * size.width
                    val y = size.height - ((pt.volume / 2f).coerceIn(0f, 1f) * size.height)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    drawCircle(Color(0xFF8B5CF6), radius = 5f, center = Offset(x, y))
                }
                drawPath(path, Color(0xFF10B981), style = Stroke(2.5f))
            }
        }

        // Graphic Equalizer
        HorizontalDivider()
        Text("Equalizer & Filters", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EqPreset.entries.forEach { preset ->
                FilterChip(selected = eq.preset == preset, onClick = { onApplyEqPreset(preset) }, label = { Text(preset.name.replace('_', ' ')) })
            }
        }
        LabeledSlider("Bass (120Hz dB)", eq.bassDb, -12f..12f) { onUpdateAudio(a.copy(eqSettings = eq.copy(preset = EqPreset.CUSTOM, bassDb = it))) }
        LabeledSlider("Mid (1kHz dB)", eq.midDb, -12f..12f) { onUpdateAudio(a.copy(eqSettings = eq.copy(preset = EqPreset.CUSTOM, midDb = it))) }
        LabeledSlider("Treble (6kHz dB)", eq.trebleDb, -12f..12f) { onUpdateAudio(a.copy(eqSettings = eq.copy(preset = EqPreset.CUSTOM, trebleDb = it))) }
        LabeledSlider("High-Pass Filter (Hz)", eq.highPassHz, 20f..2000f, { "${it.toInt()} Hz" }) {
            onUpdateAudio(a.copy(eqSettings = eq.copy(preset = EqPreset.CUSTOM, highPassHz = it)))
        }
        LabeledSlider("Low-Pass Filter (Hz)", eq.lowPassHz, 500f..20000f, { "${it.toInt()} Hz" }) {
            onUpdateAudio(a.copy(eqSettings = eq.copy(preset = EqPreset.CUSTOM, lowPassHz = it)))
        }

        // Audio Effects & Advanced Offline STFT Spectral Noise Remover
        HorizontalDivider()
        Text("Advanced Offline Noise Remover (STFT DSP)", style = MaterialTheme.typography.labelLarge)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("512-pt STFT Spectral Subtraction + Wiener", style = MaterialTheme.typography.bodySmall)
            Switch(
                checked = fx.spectralDenoiseEnabled,
                onCheckedChange = { onUpdateAudio(a.copy(audioEffects = fx.copy(spectralDenoiseEnabled = it))) }
            )
        }
        LabeledSlider("Noise Reduction Strength", fx.noiseReductionStrength, 0f..1f, { "${(it * 100).toInt()}%" }) {
            onUpdateAudio(a.copy(audioEffects = fx.copy(noiseReductionStrength = it)))
        }
        Text("Mains Hum Notch Filter", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0 to "Off", 50 to "50 Hz Hum", 60 to "60 Hz Hum").forEach { (hz, label) ->
                FilterChip(
                    selected = fx.humFilterHz == hz,
                    onClick = { onUpdateAudio(a.copy(audioEffects = fx.copy(humFilterHz = hz))) },
                    label = { Text(label) }
                )
            }
        }
        LabeledSlider("Voice Presence Clarity (2.8 kHz)", fx.voicePresenceBoost, 0f..1f, { "+${(it * 7f).toInt()} dB" }) {
            onUpdateAudio(a.copy(audioEffects = fx.copy(voicePresenceBoost = it)))
        }

        HorizontalDivider()
        Text("Spatial & Pitch Audio FX", style = MaterialTheme.typography.labelLarge)
        LabeledSlider("Reverb Mix", fx.reverbMix, 0f..1f) { onUpdateAudio(a.copy(audioEffects = fx.copy(reverbMix = it))) }
        LabeledSlider("Echo Delay (ms)", fx.echoDelayMs.toFloat(), 0f..550f, { "${it.toInt()} ms" }) {
            onUpdateAudio(a.copy(audioEffects = fx.copy(echoDelayMs = it.toInt(), echoFeedback = if (it > 10 && fx.echoFeedback == 0f) 0.35f else fx.echoFeedback)))
        }
        LabeledSlider("Echo Feedback", fx.echoFeedback, 0f..0.7f) { onUpdateAudio(a.copy(audioEffects = fx.copy(echoFeedback = it))) }
        LabeledSlider("Pitch Shift (Semitones)", fx.pitchSemitones, -12f..12f, { "${it.toInt()} st" }) {
            onUpdateAudio(a.copy(audioEffects = fx.copy(pitchSemitones = it)))
        }
        Text(
            "Runs 100% offline via real-time 512-point Hann STFT spectral subtraction, minimum-statistics noise floor tracking, and biquad hum notch filtering.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun MultiTrackMixerPanel(
    tracks: List<TimelineTrack>,
    onUpdateTrackMix: (String, Float, Float) -> Unit,
    onToggleMute: (String) -> Unit,
    onToggleSolo: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Multi-Track Audio Mixer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        tracks.sortedBy { it.order }.forEach { track ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(track.name, style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = track.isMuted, onClick = { onToggleMute(track.id) }, label = { Text("M") })
                            FilterChip(selected = track.isSolo, onClick = { onToggleSolo(track.id) }, label = { Text("S") })
                        }
                    }
                    LabeledSlider("Track Volume", track.volume, 0f..2f, { "${(it * 100).toInt()}%" }) {
                        onUpdateTrackMix(track.id, it, track.pan)
                    }
                    LabeledSlider("Pan", track.pan, -1f..1f) {
                        onUpdateTrackMix(track.id, track.volume, it)
                    }
                }
            }
        }
    }
}

@Composable
fun VoiceRecorderPanel(
    isRecording: Boolean,
    isPaused: Boolean,
    elapsedMs: Long,
    amplitude: Float,
    recordings: List<LocalRecording>,
    voiceRecorder: VoiceRecorderEngine,
    onStart: () -> Unit,
    onTogglePause: () -> Unit,
    onStop: (Boolean) -> Unit,
    onAddRecording: (java.io.File) -> Unit,
    onRenameRecording: (java.io.File, String) -> Unit,
    onDeleteRecording: (java.io.File) -> Unit
) {
    val context = LocalContext.current
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDeniedMsg by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<LocalRecording?>(null) }
    var playingFile by remember { mutableStateOf<String?>(null) }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasMicPermission = granted
        if (granted) {
            permissionDeniedMsg = false
            onStart()
        } else {
            permissionDeniedMsg = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Offline Voiceover Studio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = StudioFormatter.formatTimecode(elapsedMs),
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (isRecording && !isPaused) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                LinearProgressIndicator(
                    progress = { amplitude },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                )
                if (permissionDeniedMsg) {
                    Text(
                        "Microphone permission is required only for voice recording. Please grant it to record voiceovers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!isRecording) {
                        Button(
                            onClick = {
                                if (hasMicPermission) onStart() else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Mic, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Record Voice")
                        }
                    } else {
                        OutlinedButton(onClick = onTogglePause) {
                            Icon(if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, null)
                            Spacer(Modifier.width(4.dp))
                            Text(if (isPaused) "Resume" else "Pause")
                        }
                        Button(onClick = { onStop(true) }) {
                            Icon(Icons.Default.Stop, null)
                            Spacer(Modifier.width(4.dp))
                            Text("Stop & Add")
                        }
                    }
                }
            }
        }

        if (recordings.isNotEmpty()) {
            Text("Saved Voiceovers (${recordings.size})", style = MaterialTheme.typography.labelLarge)
            recordings.forEach { rec ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            if (playingFile == rec.file.absolutePath) {
                                voiceRecorder.stopPlayback()
                                playingFile = null
                            } else {
                                playingFile = rec.file.absolutePath
                                voiceRecorder.playPreview(rec.file) { playingFile = null }
                            }
                        }) {
                            Icon(if (playingFile == rec.file.absolutePath) Icons.Default.Stop else Icons.Default.PlayArrow, null)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(rec.name, style = MaterialTheme.typography.labelLarge)
                            Text(StudioFormatter.formatDurationShort(rec.durationMs), style = MaterialTheme.typography.labelSmall)
                        }
                        IconButton(onClick = { renameTarget = rec }) {
                            Icon(Icons.Default.DriveFileRenameOutline, "Rename", modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { onDeleteRecording(rec.file) }) {
                            Icon(Icons.Default.Delete, "Delete", modifier = Modifier.size(18.dp))
                        }
                        TextButton(onClick = { onAddRecording(rec.file) }) { Text("Add") }
                    }
                }
            }
        }
    }

    renameTarget?.let { target ->
        var newName by remember { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename Recording") },
            text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true) },
            confirmButton = { Button(onClick = { onRenameRecording(target.file, newName); renameTarget = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CanvasSettingsPanel(
    project: ZuvyraProject,
    onUpdateCanvas: (AspectRatioOption, ResolutionPreset, Int) -> Unit
) {
    var ratio by remember(project.id) { mutableStateOf(project.aspectRatio) }
    var res by remember(project.id) { mutableStateOf(project.exportConfig.resolution) }
    var fps by remember(project.id) { mutableStateOf(project.fps) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Project Canvas & Frame Rate", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Aspect Ratio", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AspectRatioOption.entries.filter { it != AspectRatioOption.CUSTOM }.forEach { opt ->
                FilterChip(selected = ratio == opt, onClick = { ratio = opt; onUpdateCanvas(opt, res, fps) }, label = { Text(opt.label) })
            }
        }
        Text("Canvas Resolution", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(ResolutionPreset.P480, ResolutionPreset.P720, ResolutionPreset.P1080, ResolutionPreset.P1440, ResolutionPreset.P2160).forEach { r ->
                FilterChip(selected = res == r, onClick = { res = r; onUpdateCanvas(ratio, r, fps) }, label = { Text(r.label) })
            }
        }
        Text("Timeline Frame Rate", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(24, 25, 30, 50, 60).forEach { rate ->
                FilterChip(selected = fps == rate, onClick = { fps = rate; onUpdateCanvas(ratio, res, rate) }, label = { Text("$rate FPS") })
            }
        }
    }
}
