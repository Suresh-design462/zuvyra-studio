package com.example.ui.editor

import android.net.Uri
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.core.model.DrawingPoint
import com.example.core.model.HandwritingStroke
import com.example.core.model.MediaType
import com.example.core.model.TimelineClip
import com.example.core.model.TrackType
import com.example.core.model.TransformConfig
import com.example.core.model.ZuvyraProject
import com.example.engine.AnimationEvaluator
import com.example.engine.OverlayCanvasRenderer
import com.example.engine.ZuvyraVideoEffects
import com.example.ui.EditorInspectorTab
import java.io.File
import kotlin.math.abs

@OptIn(UnstableApi::class)
@Composable
fun LivePreviewPlayer(
    project: ZuvyraProject,
    playheadMs: Long,
    isPlaying: Boolean,
    selectedClip: TimelineClip?,
    activeInspector: EditorInspectorTab = EditorInspectorTab.NONE,
    cameraTrackTarget: Pair<Float, Float> = 0f to 0f,
    showSafeAreaGuides: Boolean,
    showGridGuides: Boolean,
    isFullScreen: Boolean,
    isHandwritingMode: Boolean,
    brushColorArgb: Long,
    brushSize: Float,
    brushOpacity: Float,
    isEraserMode: Boolean,
    onToggleFullScreen: () -> Unit,
    onDirectTransformChange: (String, TransformConfig) -> Unit,
    onCameraTrackTargetChange: (Float, Float) -> Unit = { _, _ -> },
    onCommitHandwritingStroke: (HandwritingStroke) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val aspect = (project.width.toFloat() / project.height.coerceAtLeast(1).toFloat()).coerceIn(0.4f, 2.5f)
    var canvasSize by remember { mutableStateOf(IntSize(1, 1)) }
    val activeDrawPoints = remember { mutableStateListOf<DrawingPoint>() }

    val allClips = remember(project) { project.tracks.flatMap { it.clips } }
    val anySolo = project.tracks.any { it.isSolo }

    // Depth-sorted active visual layers at playhead (back-to-front by world Z coordinate)
    val activeVisualLayers = remember(project, playheadMs, allClips) {
        project.tracks
            .filter { it.isVisible && (it.type == TrackType.VIDEO || it.type == TrackType.IMAGE) }
            .sortedBy { it.order }
            .flatMap { track ->
                track.clips.mapNotNull { clip ->
                    if (clip.mediaUri != null &&
                        playheadMs >= clip.timelineStartMs &&
                        playheadMs <= (clip.timelineStartMs + clip.durationMs)
                    ) {
                        val state = AnimationEvaluator.evaluateClipWorld3D(clip, allClips, playheadMs)
                        Triple(track, clip, state)
                    } else null
                }
            }
            .sortedWith(compareByDescending<Triple<com.example.core.model.TimelineTrack, TimelineClip, com.example.engine.EvaluatedClipState>> { it.third.posZ }.thenBy { it.first.order })
    }

    val activeVideoTriple = remember(activeVisualLayers) {
        activeVisualLayers.firstOrNull { it.second.mediaType == MediaType.VIDEO }
    }
    val activeVideoClip = activeVideoTriple?.second
    val activeVideoTrack = activeVideoTriple?.first

    val activeAudioTrackAndClip = remember(project, playheadMs) {
        project.tracks
            .filter { it.isVisible && it.type == TrackType.AUDIO && !it.isMuted && (!anySolo || it.isSolo) }
            .sortedBy { it.order }
            .firstNotNullOfOrNull { track ->
                track.clips.firstOrNull { clip ->
                    clip.mediaUri != null &&
                        !clip.audioSettings.isMuted &&
                        playheadMs >= clip.timelineStartMs &&
                        playheadMs <= (clip.timelineStartMs + clip.durationMs)
                }?.let { track to it }
            }
    }

    val videoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }
    val audioPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            videoPlayer.release()
            audioPlayer.release()
        }
    }

    // Update video ExoPlayer source & GPU effects (including 3D matrix, Chroma/Alpha Key, Null/Camera rig)
    val camAndNullSignature = remember(allClips) {
        allClips.filter { it.mediaType == MediaType.NULL_OBJECT || it.mediaType == MediaType.CAMERA_3D }
            .map { "${it.id}:${it.transform}:${it.camera3d}:${it.keyframes.size}" }
    }
    LaunchedEffect(
        activeVideoClip?.id,
        activeVideoClip?.mediaUri,
        activeVideoClip?.isReversed,
        activeVideoClip?.reversedCachePath,
        activeVideoClip?.sourceStartMs,
        activeVideoClip?.sourceEndMs,
        activeVideoClip?.colorAdjustments,
        activeVideoClip?.videoEffects,
        activeVideoClip?.transform,
        activeVideoClip?.keyframes,
        activeVideoClip?.animation,
        activeVideoClip?.chromaKey,
        activeVideoClip?.alphaKey,
        activeVideoClip?.layerStyle,
        camAndNullSignature
    ) {
        if (activeVideoClip?.mediaUri != null) {
            val uri = if (activeVideoClip.isReversed && !activeVideoClip.reversedCachePath.isNullOrBlank() && File(activeVideoClip.reversedCachePath).exists()) {
                Uri.fromFile(File(activeVideoClip.reversedCachePath))
            } else {
                Uri.parse(activeVideoClip.mediaUri)
            }
            val item = MediaItem.Builder()
                .setUri(uri)
                .apply {
                    if (!activeVideoClip.isReversed) {
                        setClippingConfiguration(
                            MediaItem.ClippingConfiguration.Builder()
                                .setStartPositionMs(activeVideoClip.sourceStartMs.coerceAtLeast(0L))
                                .setEndPositionMs(activeVideoClip.sourceEndMs.coerceAtLeast(activeVideoClip.sourceStartMs + 100L))
                                .build()
                        )
                    }
                }
                .build()
            val inTrans = project.transitions.firstOrNull { it.toClipId == activeVideoClip.id }
            val outTrans = project.transitions.firstOrNull { it.fromClipId == activeVideoClip.id }
            val effects = ZuvyraVideoEffects.buildClipVideoEffects(activeVideoClip, allClips, inTrans, outTrans)
            videoPlayer.setVideoEffects(effects)
            videoPlayer.setMediaItem(item)
            videoPlayer.prepare()
        } else {
            videoPlayer.stop()
            videoPlayer.clearMediaItems()
        }
    }

    // Sync video volume & speed & playback position
    LaunchedEffect(playheadMs, isPlaying, activeVideoClip?.id, activeVideoTrack?.isMuted, activeVideoClip?.audioDetached) {
        if (activeVideoClip != null) {
            val trackMuted = activeVideoTrack?.isMuted == true || (anySolo && activeVideoTrack?.isSolo != true)
            videoPlayer.volume = if (trackMuted || activeVideoClip.audioDetached || activeVideoClip.audioSettings.isMuted) {
                0f
            } else {
                (activeVideoClip.audioSettings.volume * (activeVideoTrack?.volume ?: 1f)).coerceIn(0f, 1f)
            }
            val localMs = (playheadMs - activeVideoClip.timelineStartMs).coerceIn(0L, activeVideoClip.durationMs)
            val spd = activeVideoClip.speedConfig.constantSpeed.coerceIn(0.1f, 16f)
            videoPlayer.setPlaybackSpeed(spd)
            val targetPos = (localMs * spd).toLong()
            if (!isPlaying || abs(videoPlayer.currentPosition - targetPos) > 240L) {
                videoPlayer.seekTo(targetPos)
            }
            videoPlayer.playWhenReady = isPlaying
        } else {
            videoPlayer.playWhenReady = false
        }
    }

    val activeAudioClip = activeAudioTrackAndClip?.second
    val activeAudioTrack = activeAudioTrackAndClip?.first
    LaunchedEffect(activeAudioClip?.id, activeAudioClip?.mediaUri, activeAudioClip?.sourceStartMs, activeAudioClip?.sourceEndMs) {
        if (activeAudioClip?.mediaUri != null) {
            val item = MediaItem.Builder()
                .setUri(Uri.parse(activeAudioClip.mediaUri))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(activeAudioClip.sourceStartMs.coerceAtLeast(0L))
                        .setEndPositionMs(activeAudioClip.sourceEndMs.coerceAtLeast(activeAudioClip.sourceStartMs + 100L))
                        .build()
                )
                .build()
            audioPlayer.setMediaItem(item)
            audioPlayer.prepare()
        } else {
            audioPlayer.stop()
            audioPlayer.clearMediaItems()
        }
    }

    LaunchedEffect(playheadMs, isPlaying, activeAudioClip?.id, activeAudioTrack?.volume) {
        if (activeAudioClip != null) {
            audioPlayer.volume = (activeAudioClip.audioSettings.volume * (activeAudioTrack?.volume ?: 1f)).coerceIn(0f, 1f)
            val localMs = (playheadMs - activeAudioClip.timelineStartMs).coerceIn(0L, activeAudioClip.durationMs)
            if (!isPlaying || abs(audioPlayer.currentPosition - localMs) > 260L) {
                audioPlayer.seekTo(localMs)
            }
            audioPlayer.playWhenReady = isPlaying
        } else {
            audioPlayer.playWhenReady = false
        }
    }

    val isTrackerInspector = activeInspector == EditorInspectorTab.CAMERA_TRACKER

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF040509)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .then(
                    if (aspect >= 1f) Modifier.fillMaxWidth(0.96f).aspectRatio(aspect)
                    else Modifier.fillMaxHeight(0.95f).aspectRatio(aspect)
                )
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black)
                .border(1.dp, Color(0xFF1F2438), RoundedCornerShape(8.dp))
                .onSizeChanged { canvasSize = it }
                .then(
                    when {
                        isHandwritingMode -> Modifier.pointerInput(brushColorArgb, brushSize, brushOpacity, isEraserMode) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    activeDrawPoints.clear()
                                    activeDrawPoints.add(
                                        DrawingPoint(
                                            (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f),
                                            (offset.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                        )
                                    )
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    activeDrawPoints.add(
                                        DrawingPoint(
                                            (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f),
                                            (change.position.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                        )
                                    )
                                },
                                onDragEnd = {
                                    if (activeDrawPoints.size >= 2) {
                                        onCommitHandwritingStroke(
                                            HandwritingStroke(
                                                points = activeDrawPoints.toList(),
                                                colorArgb = brushColorArgb,
                                                brushSize = brushSize,
                                                opacity = brushOpacity,
                                                isEraser = isEraserMode
                                            )
                                        )
                                    }
                                    activeDrawPoints.clear()
                                }
                            )
                        }
                        isTrackerInspector -> Modifier.pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val nx = ((offset.x / size.width.coerceAtLeast(1).toFloat()) - 0.5f) * 2f
                                val ny = ((offset.y / size.height.coerceAtLeast(1).toFloat()) - 0.5f) * 2f
                                onCameraTrackTargetChange(nx, ny)
                            }
                        }
                        selectedClip != null -> Modifier.pointerInput(selectedClip.id, selectedClip.transform) {
                            detectTransformGestures { _, pan, zoom, rotation ->
                                val cur = selectedClip.transform
                                val dx = pan.x / (size.width.coerceAtLeast(1) * 0.5f)
                                val dy = pan.y / (size.height.coerceAtLeast(1) * 0.5f)
                                onDirectTransformChange(
                                    selectedClip.id,
                                    cur.copy(
                                        posX = (cur.posX + dx).coerceIn(-1.5f, 1.5f),
                                        posY = (cur.posY + dy).coerceIn(-1.5f, 1.5f),
                                        scaleX = (cur.scaleX * zoom).coerceIn(0.1f, 5f),
                                        scaleY = (cur.scaleY * zoom).coerceIn(0.1f, 5f),
                                        rotation = (cur.rotation + rotation) % 360f
                                    )
                                )
                            }
                        }
                        else -> Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (activeVisualLayers.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.MovieCreation,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.16f),
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                // Depth-sorted multi-layer 3D compositing stack
                var videoViewBound = false
                for ((_, clip, eval) in activeVisualLayers) {
                    if (clip.mediaType == MediaType.VIDEO && !videoViewBound) {
                        videoViewBound = true
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = videoPlayer
                                    useController = false
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (clip.mediaType == MediaType.IMAGE && clip.mediaUri != null) {
                        val cutoutFile = clip.maskedMediaCachePath?.let { File(it) }
                        val displayModel: Any = if (clip.bgRemoval.enabled && cutoutFile != null && cutoutFile.exists()) {
                            cutoutFile
                        } else {
                            Uri.parse(clip.mediaUri)
                        }
                        val cAdj = clip.colorAdjustments
                        val cm = ColorMatrix().apply {
                            setToSaturation((1f + cAdj.saturation).coerceIn(0f, 2.5f))
                        }
                        AsyncImage(
                            model = displayModel,
                            contentDescription = clip.displayName,
                            contentScale = ContentScale.Fit,
                            colorFilter = ColorFilter.colorMatrix(cm),
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    translationX = eval.posX * canvasSize.width * 0.5f
                                    translationY = eval.posY * canvasSize.height * 0.5f
                                    val pScale = eval.perspectiveScale
                                    scaleX = eval.scaleX * pScale
                                    scaleY = eval.scaleY * pScale
                                    rotationX = eval.rotationX
                                    rotationY = eval.rotationY
                                    rotationZ = eval.rotationDeg
                                    cameraDistance = (14f * (55f / eval.perspectiveFov.coerceIn(20f, 120f))) * density
                                    alpha = eval.opacity
                                }
                        )
                    }
                }
            }

            // Live 3D Overlays + Handwriting + Gizmos + Camera Tracker Reticle
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawIntoCanvas { canvas ->
                    OverlayCanvasRenderer.renderAllOverlaysOnCanvas(
                        context = context,
                        canvas = canvas.nativeCanvas,
                        project = project,
                        timelineMs = playheadMs,
                        canvasW = size.width,
                        canvasH = size.height
                    )
                }

                // Active live stroke while dragging
                if (activeDrawPoints.size >= 2) {
                    val path = Path().apply {
                        moveTo(activeDrawPoints[0].x * size.width, activeDrawPoints[0].y * size.height)
                        for (i in 1 until activeDrawPoints.size) {
                            lineTo(activeDrawPoints[i].x * size.width, activeDrawPoints[i].y * size.height)
                        }
                    }
                    drawPath(
                        path = path,
                        color = if (isEraserMode) Color.Black.copy(alpha = 0.5f) else Color(brushColorArgb.toInt()).copy(alpha = brushOpacity),
                        style = Stroke(width = brushSize, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }

                // 3D Null Object / Camera Rig / 3D Layer Gizmo Overlay
                if (selectedClip != null && (selectedClip.mediaType == MediaType.NULL_OBJECT || selectedClip.mediaType == MediaType.CAMERA_3D || selectedClip.transform.is3dLayer)) {
                    val eval = AnimationEvaluator.evaluateClipWorld3D(selectedClip, allClips, playheadMs)
                    val gx = size.width * 0.5f + eval.posX * size.width * 0.5f
                    val gy = size.height * 0.5f + eval.posY * size.height * 0.5f
                    val boxR = 36f * eval.perspectiveScale.coerceIn(0.4f, 2f)
                    val gizmoColor = if (selectedClip.mediaType == MediaType.CAMERA_3D) Color(0xFFF59E0B) else Color(0xFF10B981)
                    drawRect(
                        color = gizmoColor.copy(alpha = 0.85f),
                        topLeft = Offset(gx - boxR, gy - boxR),
                        size = Size(boxR * 2f, boxR * 2f),
                        style = Stroke(width = 2.5f)
                    )
                    drawLine(Color(0xFFF43F5E), Offset(gx, gy), Offset(gx + boxR * 0.9f, gy), strokeWidth = 3f)
                    drawLine(Color(0xFF10B981), Offset(gx, gy), Offset(gx, gy - boxR * 0.9f), strokeWidth = 3f)
                    drawCircle(Color(0xFF38BDF8), radius = 6f, center = Offset(gx, gy))
                }

                // Planar Camera Tracker Target Reticle
                if (isTrackerInspector) {
                    val tx = size.width * 0.5f + cameraTrackTarget.first * size.width * 0.5f
                    val ty = size.height * 0.5f + cameraTrackTarget.second * size.height * 0.5f
                    val reticleCol = Color(0xFF38BDF8)
                    drawRect(
                        color = reticleCol,
                        topLeft = Offset(tx - 24f, ty - 24f),
                        size = Size(48f, 48f),
                        style = Stroke(width = 2.2f)
                    )
                    drawLine(reticleCol, Offset(tx - 34f, ty), Offset(tx + 34f, ty), 1.5f)
                    drawLine(reticleCol, Offset(tx, ty - 34f), Offset(tx, ty + 34f), 1.5f)
                    drawCircle(Color(0xFFF59E0B), radius = 4f, center = Offset(tx, ty))
                }

                // Rule of thirds & center guides
                if (showGridGuides) {
                    val guideColor = Color.White.copy(alpha = 0.32f)
                    drawLine(guideColor, Offset(size.width / 3f, 0f), Offset(size.width / 3f, size.height), 1f)
                    drawLine(guideColor, Offset(size.width * 2f / 3f, 0f), Offset(size.width * 2f / 3f, size.height), 1f)
                    drawLine(guideColor, Offset(0f, size.height / 3f), Offset(size.width, size.height / 3f), 1f)
                    drawLine(guideColor, Offset(0f, size.height * 2f / 3f), Offset(size.width, size.height * 2f / 3f), 1f)
                    drawLine(Color(0xFF38BDF8).copy(alpha = 0.4f), Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), 1.5f)
                    drawLine(Color(0xFF38BDF8).copy(alpha = 0.4f), Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), 1.5f)
                }

                // Safe Area Guides
                if (showSafeAreaGuides) {
                    drawRect(
                        color = Color(0xFFF59E0B).copy(alpha = 0.55f),
                        topLeft = Offset(size.width * 0.05f, size.height * 0.05f),
                        size = Size(size.width * 0.90f, size.height * 0.90f),
                        style = Stroke(width = 1.5f)
                    )
                    drawRect(
                        color = Color(0xFF10B981).copy(alpha = 0.55f),
                        topLeft = Offset(size.width * 0.10f, size.height * 0.10f),
                        size = Size(size.width * 0.80f, size.height * 0.80f),
                        style = Stroke(width = 1.5f)
                    )
                }
            }

            // Telemetry Pill (Ratio + Res + 3D Camera Indicator)
            val camEval = AnimationEvaluator.evaluateCamera3DAt(allClips, playheadMs)
            Surface(
                color = Color.Black.copy(alpha = 0.62f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
            ) {
                Text(
                    text = "${project.aspectRatio.label} • ${project.height}p" + if (camEval.isActive) " • 3D CAM" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (camEval.isActive) Color(0xFFF59E0B) else Color.White,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            IconButton(
                onClick = onToggleFullScreen,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
            ) {
                Icon(
                    imageVector = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = "Toggle Fullscreen Preview",
                    tint = Color.White
                )
            }
        }
    }
}
