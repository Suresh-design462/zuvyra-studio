package com.example.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ControlCamera
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.core.model.ClipTransition
import com.example.core.model.MediaType
import com.example.core.model.TimelineClip
import com.example.core.model.TimelineTrack
import com.example.core.model.TrackType
import com.example.core.model.TransitionType
import com.example.core.model.ZuvyraProject
import com.example.ui.components.StudioFormatter
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ZuvyraAmber
import com.example.ui.theme.ZuvyraCyan
import com.example.ui.theme.ZuvyraEmerald
import com.example.ui.theme.ZuvyraIndigo
import com.example.ui.theme.ZuvyraMagenta
import com.example.ui.theme.ZuvyraViolet
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun MultiTrackTimeline(
    project: ZuvyraProject,
    playheadMs: Long,
    timelineZoom: Float,
    selectedClipIds: Set<String>,
    onZoomChange: (Float) -> Unit,
    onSeekMs: (Long) -> Unit,
    onSelectClip: (String, Boolean) -> Unit,
    onClearSelection: () -> Unit,
    onMoveSelectedClips: (Long) -> Unit,
    onTrimClipEdges: (String, Long, Long) -> Unit,
    onAddOrSelectTransition: (String, String, ClipTransition?) -> Unit,
    onToggleTrackVisibility: (String) -> Unit,
    onToggleTrackLock: (String) -> Unit,
    onToggleTrackMute: (String) -> Unit,
    onToggleTrackSolo: (String) -> Unit,
    onReorderTrack: (String, Int) -> Unit,
    onDuplicateTrack: (String) -> Unit,
    onDeleteTrack: (String) -> Unit,
    onAddTrack: (TrackType) -> Unit,
    modifier: Modifier = Modifier
) {
    val pxPerSec = with(LocalDensity.current) { (64f * timelineZoom).dp.toPx() }
    val dpPerSec = 64f * timelineZoom
    val totalMs = maxOf(project.computedDurationMs() + 8000L, 20_000L)
    val timelineWidthDp = ((totalMs / 1000f) * dpPerSec).dp
    val headerWidth = 98.dp
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()
    val sortedTracks = remember(project.tracks) { project.tracks.sortedBy { it.order } }

    var showAddTrackMenu by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .pointerInput(timelineZoom) {
                detectTransformGestures { _, _, zoom, _ ->
                    if (abs(zoom - 1f) > 0.01f) {
                        onZoomChange(timelineZoom * zoom)
                    }
                }
            }
    ) {
        // Ruler Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            // Add Track Button in Top-Left Corner
            Box(
                modifier = Modifier
                    .width(headerWidth)
                    .fillMaxHeight()
                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                    .clickable { showAddTrackMenu = true },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, contentDescription = "Add Track", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                    Text("Track", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                DropdownMenu(expanded = showAddTrackMenu, onDismissRequest = { showAddTrackMenu = false }) {
                    TrackType.entries.forEach { tType ->
                        DropdownMenuItem(
                            text = { Text("Add ${tType.name.lowercase().replaceFirstChar { it.uppercase() }} Track") },
                            onClick = { showAddTrackMenu = false; onAddTrack(tType) }
                        )
                    }
                }
            }

            // Scrollable Time Ruler + Scrubbing
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .horizontalScroll(hScroll)
                    .pointerInput(pxPerSec) {
                        detectTapGestures { offset ->
                            val tappedMs = ((offset.x / pxPerSec) * 1000f).toLong().coerceAtLeast(0L)
                            onSeekMs(tappedMs)
                        }
                    }
                    .pointerInput(pxPerSec) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val dragMs = ((change.position.x / pxPerSec) * 1000f).toLong().coerceAtLeast(0L)
                            onSeekMs(dragMs)
                        }
                    }
            ) {
                Box(modifier = Modifier.width(timelineWidthDp).fillMaxHeight()) {
                    val stepSec = when {
                        timelineZoom >= 2.5f -> 1
                        timelineZoom >= 0.8f -> 2
                        else -> 5
                    }
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val totalSec = (totalMs / 1000L).toInt()
                        for (s in 0..totalSec) {
                            val x = s * pxPerSec
                            val isMajor = s % stepSec == 0
                            drawLine(
                                color = Color(0xFF94A3B8).copy(alpha = if (isMajor) 0.7f else 0.3f),
                                start = Offset(x, if (isMajor) size.height * 0.4f else size.height * 0.7f),
                                end = Offset(x, size.height),
                                strokeWidth = if (isMajor) 1.5f else 1f
                            )
                        }
                    }
                    val totalSec = (totalMs / 1000L).toInt()
                    for (s in 0..totalSec step stepSec) {
                        Text(
                            text = StudioFormatter.formatDurationShort(s * 1000L),
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.offset(x = (s * dpPerSec + 3f).dp, y = 2.dp)
                        )
                    }

                    // Playhead Cap on Ruler
                    val playXdp = ((playheadMs / 1000f) * dpPerSec).dp
                    Box(
                        modifier = Modifier
                            .offset(x = playXdp - 6.dp)
                            .size(width = 12.dp, height = 14.dp)
                            .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                            .background(MaterialTheme.colorScheme.error)
                    )
                }
            }
        }

        // Tracks Area + Playhead Vertical Line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(vScroll)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                sortedTracks.forEach { track ->
                    TimelineTrackRow(
                        track = track,
                        allTransitions = project.transitions,
                        headerWidth = headerWidth,
                        timelineWidthDp = timelineWidthDp,
                        dpPerSec = dpPerSec,
                        pxPerSec = pxPerSec,
                        hScroll = hScroll,
                        selectedClipIds = selectedClipIds,
                        onSelectClip = onSelectClip,
                        onClearSelection = onClearSelection,
                        onMoveSelectedClips = onMoveSelectedClips,
                        onTrimClipEdges = onTrimClipEdges,
                        onAddOrSelectTransition = onAddOrSelectTransition,
                        onToggleVisibility = { onToggleTrackVisibility(track.id) },
                        onToggleLock = { onToggleTrackLock(track.id) },
                        onToggleMute = { onToggleTrackMute(track.id) },
                        onToggleSolo = { onToggleTrackSolo(track.id) },
                        onMoveUp = { onReorderTrack(track.id, -1) },
                        onMoveDown = { onReorderTrack(track.id, 1) },
                        onDuplicate = { onDuplicateTrack(track.id) },
                        onDelete = { onDeleteTrack(track.id) }
                    )
                }
            }

            // Global Playhead Line across all tracks
            val playOffsetDp = ((playheadMs / 1000f) * dpPerSec).dp
            Box(
                modifier = Modifier
                    .padding(start = headerWidth)
                    .fillMaxWidth()
                    .matchParentSize()
                    .horizontalScroll(hScroll, enabled = false)
            ) {
                Box(
                    modifier = Modifier
                        .offset(x = playOffsetDp - 1.dp)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.error)
                )
            }
        }
    }
}

@Composable
private fun TimelineTrackRow(
    track: TimelineTrack,
    allTransitions: List<ClipTransition>,
    headerWidth: androidx.compose.ui.unit.Dp,
    timelineWidthDp: androidx.compose.ui.unit.Dp,
    dpPerSec: Float,
    pxPerSec: Float,
    hScroll: androidx.compose.foundation.ScrollState,
    selectedClipIds: Set<String>,
    onSelectClip: (String, Boolean) -> Unit,
    onClearSelection: () -> Unit,
    onMoveSelectedClips: (Long) -> Unit,
    onTrimClipEdges: (String, Long, Long) -> Unit,
    onAddOrSelectTransition: (String, String, ClipTransition?) -> Unit,
    onToggleVisibility: () -> Unit,
    onToggleLock: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSolo: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        // Track Header Control Column
        Column(
            modifier = Modifier
                .width(headerWidth)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = when (track.type) {
                        TrackType.VIDEO -> Icons.Default.Videocam
                        TrackType.IMAGE -> Icons.Default.Image
                        TrackType.TEXT -> Icons.Default.TextFields
                        TrackType.STICKER -> Icons.Default.EmojiEmotions
                        TrackType.HANDWRITING -> Icons.Default.Draw
                        TrackType.AUDIO -> Icons.Default.Audiotrack
                        TrackType.NULL_OBJECT -> Icons.Default.ControlCamera
                        TrackType.CAMERA_3D -> Icons.Default.ViewInAr
                    },
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = track.name,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(18.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Track Menu", modifier = Modifier.size(12.dp))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Move Up") }, leadingIcon = { Icon(Icons.Default.ArrowUpward, null) }, onClick = { menuOpen = false; onMoveUp() })
                        DropdownMenuItem(text = { Text("Move Down") }, leadingIcon = { Icon(Icons.Default.ArrowDownward, null) }, onClick = { menuOpen = false; onMoveDown() })
                        DropdownMenuItem(text = { Text(if (track.isSolo) "Unsolo" else "Solo Track") }, onClick = { menuOpen = false; onToggleSolo() })
                        DropdownMenuItem(text = { Text("Duplicate Track") }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) }, onClick = { menuOpen = false; onDuplicate() })
                        DropdownMenuItem(text = { Text("Delete Track", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }, onClick = { menuOpen = false; onDelete() })
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = onToggleVisibility, modifier = Modifier.size(20.dp)) {
                    Icon(
                        imageVector = if (track.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = "Track Visibility",
                        modifier = Modifier.size(13.dp),
                        tint = if (track.isVisible) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                    )
                }
                IconButton(onClick = onToggleLock, modifier = Modifier.size(20.dp)) {
                    Icon(
                        imageVector = if (track.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = "Lock Track",
                        modifier = Modifier.size(13.dp),
                        tint = if (track.isLocked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onToggleMute, modifier = Modifier.size(20.dp)) {
                    Icon(
                        imageVector = if (track.isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = "Mute Track",
                        modifier = Modifier.size(13.dp),
                        tint = if (track.isMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Clips Lane
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(if (track.isLocked) MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.5f) else Color.Transparent)
                .horizontalScroll(hScroll)
                .clickable { onClearSelection() }
        ) {
            Box(modifier = Modifier.width(timelineWidthDp).fillMaxHeight()) {
                val sortedClips = track.clips.sortedBy { it.timelineStartMs }
                sortedClips.forEach { clip ->
                    TimelineClipView(
                        clip = clip,
                        isSelected = clip.id in selectedClipIds,
                        isTrackLocked = track.isLocked,
                        dpPerSec = dpPerSec,
                        pxPerSec = pxPerSec,
                        onSelect = { multi -> onSelectClip(clip.id, multi) },
                        onDragMoveMs = { deltaMs -> onMoveSelectedClips(deltaMs) },
                        onTrimLeftMs = { d -> onTrimClipEdges(clip.id, d, 0L) },
                        onTrimRightMs = { d -> onTrimClipEdges(clip.id, 0L, d) }
                    )
                }

                // Transition Diamonds between adjacent clips
                for (i in 0 until sortedClips.size - 1) {
                    val left = sortedClips[i]
                    val right = sortedClips[i + 1]
                    val gap = abs(right.timelineStartMs - (left.timelineStartMs + left.durationMs))
                    if (gap < 200L && (left.mediaType == MediaType.VIDEO || left.mediaType == MediaType.IMAGE)) {
                        val existingTrans = allTransitions.firstOrNull { it.fromClipId == left.id && it.toClipId == right.id }
                        val junctionDp = ((right.timelineStartMs / 1000f) * dpPerSec).dp
                        val active = existingTrans != null && existingTrans.type != TransitionType.NONE
                        Surface(
                            color = if (active) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .offset(x = junctionDp - 11.dp, y = 18.dp)
                                .size(22.dp)
                                .border(1.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                .clickable { onAddOrSelectTransition(left.id, right.id, existingTrans) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Transition",
                                    tint = if (active) Color.Black else Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimelineClipView(
    clip: TimelineClip,
    isSelected: Boolean,
    isTrackLocked: Boolean,
    dpPerSec: Float,
    pxPerSec: Float,
    onSelect: (Boolean) -> Unit,
    onDragMoveMs: (Long) -> Unit,
    onTrimLeftMs: (Long) -> Unit,
    onTrimRightMs: (Long) -> Unit
) {
    val startDp = ((clip.timelineStartMs / 1000f) * dpPerSec).dp
    val widthDp = (((clip.durationMs / 1000f) * dpPerSec).coerceAtLeast(24f)).dp

    val clipColor = when (clip.mediaType) {
        MediaType.VIDEO -> ZuvyraIndigo
        MediaType.IMAGE -> ZuvyraEmerald
        MediaType.TEXT -> ZuvyraAmber
        MediaType.STICKER -> ZuvyraMagenta
        MediaType.HANDWRITING -> ZuvyraCyan
        MediaType.AUDIO -> ZuvyraViolet
        MediaType.NULL_OBJECT -> Color(0xFF14B8A6)
        MediaType.CAMERA_3D -> Color(0xFFF97316)
    }

    Box(
        modifier = Modifier
            .offset(x = startDp, y = 4.dp)
            .width(widthDp)
            .height(50.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(clipColor.copy(alpha = 0.28f))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Color.White else clipColor.copy(alpha = 0.85f),
                shape = RoundedCornerShape(8.dp)
            )
            .combinedClickable(
                onClick = { onSelect(false) },
                onLongClick = { onSelect(true) }
            )
            .pointerInput(clip.id, isTrackLocked, pxPerSec) {
                if (!isTrackLocked) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val deltaMs = ((dragAmount.x / pxPerSec) * 1000f).roundToInt().toLong()
                        if (deltaMs != 0L) onDragMoveMs(deltaMs)
                    }
                }
            }
    ) {
        // Waveform or Keyframe Diamonds
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (clip.waveformPeaks.isNotEmpty()) {
                val count = clip.waveformPeaks.size
                val barW = size.width / count.toFloat()
                clip.waveformPeaks.forEachIndexed { idx, amp ->
                    val barH = (amp * size.height * 0.6f).coerceAtLeast(3f)
                    val x = idx * barW + barW * 0.2f
                    drawLine(
                        color = Color.White.copy(alpha = 0.5f),
                        start = Offset(x, size.height - 4f),
                        end = Offset(x, size.height - 4f - barH),
                        strokeWidth = (barW * 0.55f).coerceIn(1.5f, 4f)
                    )
                }
            }

            // Keyframe diamond markers
            clip.keyframes.forEach { kf ->
                val frac = (kf.timeOffsetMs.toFloat() / clip.durationMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
                val kx = frac * size.width
                val ky = size.height - 8f
                val path = Path().apply {
                    moveTo(kx, ky - 4f)
                    lineTo(kx + 4f, ky)
                    lineTo(kx, ky + 4f)
                    lineTo(kx - 4f, ky)
                    close()
                }
                drawPath(path, color = Color(0xFFF59E0B))
            }
        }

        // Clip Label + 3D / Parented Badges
        val badgeSuffix = buildString {
            if (clip.isReversed) append(" ⟲")
            if (clip.transform.parentClipId != null) append(" ↳Parent")
            if (clip.bgRemoval.enabled || clip.chromaKey.enabled) append(" •Key")
        }
        Text(
            text = clip.displayName + badgeSuffix,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(horizontal = if (isSelected) 16.dp else 6.dp, vertical = 3.dp)
        )

        // Left & Right Trim Handles when Selected
        if (isSelected && !isTrackLocked) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(14.dp)
                    .fillMaxHeight()
                    .background(Color.White.copy(alpha = 0.9f))
                    .pointerInput(clip.id, pxPerSec) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val dMs = ((dragAmount.x / pxPerSec) * 1000f).roundToInt().toLong()
                            if (dMs != 0L) onTrimLeftMs(dMs)
                        }
                    }
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(14.dp)
                    .fillMaxHeight()
                    .background(Color.White.copy(alpha = 0.9f))
                    .pointerInput(clip.id, pxPerSec) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val dMs = ((dragAmount.x / pxPerSec) * 1000f).roundToInt().toLong()
                            if (dMs != 0L) onTrimRightMs(dMs)
                        }
                    }
            )
        }
    }
}
