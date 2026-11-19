package com.example.ui.editor

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ControlCamera
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.GroupWork
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SaveAs
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.model.MediaType
import com.example.core.model.TimelineClip
import com.example.core.model.TransitionType
import com.example.core.model.ZuvyraProject
import com.example.core.storage.PreviewQuality
import com.example.core.storage.ProjectEntity
import com.example.ui.EditorInspectorTab
import com.example.ui.EditorViewModel
import com.example.ui.components.ProjectListCard
import com.example.ui.components.StudioFormatter
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun EditorScreen(
    editorVm: EditorViewModel,
    recentProjects: List<ProjectEntity>,
    onCreateNewProjectClick: () -> Unit,
    onOpenExistingProject: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val project by editorVm.project.collectAsStateWithLifecycle()
    val playheadMs by editorVm.playheadMs.collectAsStateWithLifecycle()
    val isPlaying by editorVm.isPlaying.collectAsStateWithLifecycle()
    val selectedClipIds by editorVm.selectedClipIds.collectAsStateWithLifecycle()
    val selectedTransitionId by editorVm.selectedTransitionId.collectAsStateWithLifecycle()
    val activeInspector by editorVm.activeInspector.collectAsStateWithLifecycle()
    val timelineZoom by editorVm.timelineZoom.collectAsStateWithLifecycle()
    val snappingEnabled by editorVm.snappingEnabled.collectAsStateWithLifecycle()
    val showSafeArea by editorVm.showSafeAreaGuides.collectAsStateWithLifecycle()
    val showGrid by editorVm.showGridGuides.collectAsStateWithLifecycle()
    val isFullScreen by editorVm.isFullScreenPreview.collectAsStateWithLifecycle()
    val previewQuality by editorVm.previewQuality.collectAsStateWithLifecycle()
    val isHandwritingMode by editorVm.isHandwritingMode.collectAsStateWithLifecycle()
    val brushColor by editorVm.activeBrushColor.collectAsStateWithLifecycle()
    val brushSize by editorVm.activeBrushSize.collectAsStateWithLifecycle()
    val brushOpacity by editorVm.activeBrushOpacity.collectAsStateWithLifecycle()
    val isEraser by editorVm.activeEraserMode.collectAsStateWithLifecycle()
    val copiedKeyframe by editorVm.copiedKeyframe.collectAsStateWithLifecycle()
    val exportState by editorVm.exportState.collectAsStateWithLifecycle()
    val busyMessage by editorVm.busyMessage.collectAsStateWithLifecycle()
    val canUndo by editorVm.canUndo.collectAsStateWithLifecycle()
    val canRedo by editorVm.canRedo.collectAsStateWithLifecycle()
    val lastSavedLabel by editorVm.lastSavedLabel.collectAsStateWithLifecycle()

    val customPresets by editorVm.customFilterPresets.collectAsStateWithLifecycle()
    val customStickers by editorVm.customStickers.collectAsStateWithLifecycle()
    val customFonts by editorVm.customFonts.collectAsStateWithLifecycle()

    val isRecording by editorVm.isRecordingActive.collectAsStateWithLifecycle()
    val isRecordingPaused by editorVm.isRecordingPaused.collectAsStateWithLifecycle()
    val recordingElapsed by editorVm.recordingElapsedMs.collectAsStateWithLifecycle()
    val recordingAmp by editorVm.recordingAmplitude.collectAsStateWithLifecycle()
    val recordings by editorVm.recordings.collectAsStateWithLifecycle()
    val cameraTrackTarget by editorVm.cameraTrackTarget.collectAsStateWithLifecycle()
    val cameraTrackResultLabel by editorVm.cameraTrackResultLabel.collectAsStateWithLifecycle()

    var showAddMediaSourceDialog by remember { mutableStateOf(false) }
    var showSaveAsDialog by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 25)
    ) { uris ->
        if (uris.isNotEmpty()) editorVm.importAndAddMediaUris(uris)
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) editorVm.importAndAddMediaUris(uris)
    }

    val replaceClipPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { editorVm.replaceSelectedClipMedia(it) }
    }

    val currentProject = project
    if (currentProject == null) {
        EditorEmptyWorkspace(
            recentProjects = recentProjects,
            onCreateNewProjectClick = onCreateNewProjectClick,
            onOpenExistingProject = onOpenExistingProject
        )
        return
    }

    val primarySelectedClip = remember(currentProject, selectedClipIds) {
        val id = selectedClipIds.firstOrNull() ?: return@remember null
        currentProject.tracks.flatMap { it.clips }.firstOrNull { it.id == id }
    }

    val activeTransition = remember(currentProject, selectedTransitionId) {
        selectedTransitionId?.let { id -> currentProject.transitions.firstOrNull { it.id == id } }
    }

    BackHandler(enabled = isFullScreen || activeInspector != EditorInspectorTab.NONE) {
        when {
            isFullScreen -> editorVm.toggleFullScreenPreview()
            activeInspector != EditorInspectorTab.NONE -> editorVm.openInspector(EditorInspectorTab.NONE)
        }
    }

    if (isFullScreen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            LivePreviewPlayer(
                project = currentProject,
                playheadMs = playheadMs,
                isPlaying = isPlaying,
                selectedClip = primarySelectedClip,
                activeInspector = activeInspector,
                showSafeAreaGuides = showSafeArea,
                showGridGuides = showGrid,
                isFullScreen = true,
                isHandwritingMode = isHandwritingMode,
                brushColorArgb = brushColor,
                brushSize = brushSize,
                brushOpacity = brushOpacity,
                isEraserMode = isEraser,
                cameraTrackTarget = cameraTrackTarget,
                onCameraTrackTargetChange = { nx, ny -> editorVm.setCameraTrackTarget(nx, ny) },
                onToggleFullScreen = { editorVm.toggleFullScreenPreview() },
                onDirectTransformChange = { id, tf -> editorVm.updateTransform(id, tf) },
                onCommitHandwritingStroke = { stroke -> editorVm.appendHandwritingStroke(stroke) },
                modifier = Modifier.fillMaxSize()
            )
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    IconButton(onClick = { editorVm.jumpToStart() }) {
                        Icon(Icons.Default.SkipPrevious, "Jump Start", tint = Color.White)
                    }
                    IconButton(onClick = { editorVm.togglePlayPause() }) {
                        Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause", tint = Color.White)
                    }
                    Text(
                        text = StudioFormatter.formatTimecode(playheadMs, currentProject.fps),
                        fontFamily = JetBrainsMonoFontFamily,
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        EditorTopStudioBar(
            projectName = currentProject.name,
            saveStatus = lastSavedLabel,
            canUndo = canUndo,
            canRedo = canRedo,
            showSafeArea = showSafeArea,
            showGrid = showGrid,
            previewQuality = previewQuality,
            onBack = onNavigateBack,
            onUndo = { editorVm.undo() },
            onRedo = { editorVm.redo() },
            onSaveNow = { editorVm.saveNow() },
            onSaveAsClick = { showSaveAsDialog = true },
            onToggleSafeArea = { editorVm.toggleSafeAreaGuides() },
            onToggleGrid = { editorVm.toggleGridGuides() },
            onSelectPreviewQuality = { editorVm.setPreviewQuality(it) },
            onExportClick = { editorVm.openInspector(EditorInspectorTab.EXPORT) }
        )

        if (busyMessage != null) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                    Text(busyMessage ?: "", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val isExpandedWide = maxWidth >= 840.dp

            if (isExpandedWide) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .weight(0.58f)
                            .fillMaxHeight()
                    ) {
                        LivePreviewPlayer(
                            project = currentProject,
                            playheadMs = playheadMs,
                            isPlaying = isPlaying,
                            selectedClip = primarySelectedClip,
                            activeInspector = activeInspector,
                            showSafeAreaGuides = showSafeArea,
                            showGridGuides = showGrid,
                            isFullScreen = false,
                            isHandwritingMode = isHandwritingMode,
                            brushColorArgb = brushColor,
                            brushSize = brushSize,
                            brushOpacity = brushOpacity,
                            isEraserMode = isEraser,
                            cameraTrackTarget = cameraTrackTarget,
                            onCameraTrackTargetChange = { nx, ny -> editorVm.setCameraTrackTarget(nx, ny) },
                            onToggleFullScreen = { editorVm.toggleFullScreenPreview() },
                            onDirectTransformChange = { id, tf -> editorVm.updateTransform(id, tf) },
                            onCommitHandwritingStroke = { stroke -> editorVm.appendHandwritingStroke(stroke) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(0.45f)
                        )
                        EditorTransportBar(
                            playheadMs = playheadMs,
                            totalDurationMs = currentProject.computedDurationMs(),
                            fps = currentProject.fps,
                            isPlaying = isPlaying,
                            snappingEnabled = snappingEnabled,
                            timelineZoom = timelineZoom,
                            onJumpStart = { editorVm.jumpToStart() },
                            onPrevFrame = { editorVm.stepFrame(-1) },
                            onPlayPause = { editorVm.togglePlayPause() },
                            onNextFrame = { editorVm.stepFrame(1) },
                            onJumpEnd = { editorVm.jumpToEnd() },
                            onToggleSnap = { editorVm.toggleSnapping() },
                            onZoomChange = { editorVm.setTimelineZoom(it) }
                        )
                        MultiTrackTimeline(
                            project = currentProject,
                            playheadMs = playheadMs,
                            timelineZoom = timelineZoom,
                            selectedClipIds = selectedClipIds,
                            onZoomChange = { editorVm.setTimelineZoom(it) },
                            onSeekMs = { editorVm.seekTo(it) },
                            onSelectClip = { id, multi -> editorVm.selectClip(id, multi) },
                            onClearSelection = { editorVm.clearSelection() },
                            onMoveSelectedClips = { d -> editorVm.moveSelectedClips(d) },
                            onTrimClipEdges = { id, ds, de -> editorVm.trimClipEdges(id, ds, de) },
                            onAddOrSelectTransition = { fromId, toId, existing ->
                                if (existing != null) editorVm.selectTransition(existing.id)
                                else editorVm.addOrUpdateTransitionBetween(fromId, toId, TransitionType.CROSSFADE)
                            },
                            onToggleTrackVisibility = { editorVm.toggleTrackVisibility(it) },
                            onToggleTrackLock = { editorVm.toggleTrackLock(it) },
                            onToggleTrackMute = { editorVm.toggleTrackMute(it) },
                            onToggleTrackSolo = { editorVm.toggleTrackSolo(it) },
                            onReorderTrack = { id, d -> editorVm.reorderTrack(id, d) },
                            onDuplicateTrack = { editorVm.duplicateTrack(it) },
                            onDeleteTrack = { editorVm.deleteTrack(it) },
                            onAddTrack = { editorVm.addTrack(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(0.55f)
                        )
                        EditorContextualToolDock(
                            selectedClip = primarySelectedClip,
                            selectedCount = selectedClipIds.size,
                            activeInspector = activeInspector,
                            onAddMediaClick = { showAddMediaSourceDialog = true },
                            onAddTextClick = { editorVm.addTextLayer() },
                            onAddStickerClick = { editorVm.openInspector(EditorInspectorTab.STICKER_EDIT) },
                            onAddDrawClick = { editorVm.addHandwritingLayer() },
                            onAddNullClick = { editorVm.addNullObjectLayer() },
                            onAddCamera3DClick = { editorVm.addCamera3DLayer() },
                            onOpenInspector = { editorVm.openInspector(it) },
                            onSplit = { editorVm.splitClipAtPlayhead() },
                            onCutMid = { editorVm.cutOutMiddleAtPlayhead() },
                            onDuplicate = { editorVm.duplicateSelectedClips() },
                            onDelete = { editorVm.deleteSelectedClips() },
                            onGroup = { editorVm.groupSelectedClips() },
                            onReverse = { editorVm.toggleReverseSelectedClip() },
                            onFreezeFrame = { editorVm.freezeFrameAtPlayhead() },
                            onExtractAudio = { editorVm.extractOrDetachAudio(false) },
                            onDetachAudio = { editorVm.extractOrDetachAudio(true) },
                            onReplaceMedia = { replaceClipPicker.launch(arrayOf("video/*", "image/*", "audio/*")) }
                        )
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .weight(0.42f)
                            .fillMaxHeight()
                    ) {
                        if (activeInspector == EditorInspectorTab.NONE) {
                            EditorSideIdleSummary(
                                project = currentProject,
                                selectedClip = primarySelectedClip,
                                onOpenCanvas = { editorVm.openInspector(EditorInspectorTab.CANVAS_SETTINGS) },
                                onOpenMixer = { editorVm.openInspector(EditorInspectorTab.AUDIO_MIXER) },
                                onOpenExport = { editorVm.openInspector(EditorInspectorTab.EXPORT) }
                            )
                        } else {
                            Column(modifier = Modifier.fillMaxSize()) {
                                InspectorHeaderBar(
                                    title = activeInspector.name.replace('_', ' '),
                                    onClose = { editorVm.openInspector(EditorInspectorTab.NONE) }
                                )
                                Box(modifier = Modifier.weight(1f)) {
                                    EditorInspectorRouter(
                                        activeTab = activeInspector,
                                        project = currentProject,
                                        selectedClip = primarySelectedClip,
                                        activeTransition = activeTransition,
                                        playheadMs = playheadMs,
                                        copiedKeyframeExists = copiedKeyframe != null,
                                        customPresets = customPresets,
                                        customStickers = customStickers,
                                        customFonts = customFonts,
                                        brushColor = brushColor,
                                        brushSize = brushSize,
                                        brushOpacity = brushOpacity,
                                        isEraser = isEraser,
                                        isRecording = isRecording,
                                        isRecordingPaused = isRecordingPaused,
                                        recordingElapsed = recordingElapsed,
                                        recordingAmp = recordingAmp,
                                        recordings = recordings,
                                        cameraTrackTarget = cameraTrackTarget,
                                        cameraTrackResultLabel = cameraTrackResultLabel,
                                        exportState = exportState,
                                        editorVm = editorVm
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                val inspectorOpen = activeInspector != EditorInspectorTab.NONE
                Column(modifier = Modifier.fillMaxSize()) {
                    LivePreviewPlayer(
                        project = currentProject,
                        playheadMs = playheadMs,
                        isPlaying = isPlaying,
                        selectedClip = primarySelectedClip,
                        activeInspector = activeInspector,
                        showSafeAreaGuides = showSafeArea,
                        showGridGuides = showGrid,
                        isFullScreen = false,
                        isHandwritingMode = isHandwritingMode,
                        brushColorArgb = brushColor,
                        brushSize = brushSize,
                        brushOpacity = brushOpacity,
                        isEraserMode = isEraser,
                        cameraTrackTarget = cameraTrackTarget,
                        onCameraTrackTargetChange = { nx, ny -> editorVm.setCameraTrackTarget(nx, ny) },
                        onToggleFullScreen = { editorVm.toggleFullScreenPreview() },
                        onDirectTransformChange = { id, tf -> editorVm.updateTransform(id, tf) },
                        onCommitHandwritingStroke = { stroke -> editorVm.appendHandwritingStroke(stroke) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(if (inspectorOpen) 0.28f else 0.42f)
                    )

                    EditorTransportBar(
                        playheadMs = playheadMs,
                        totalDurationMs = currentProject.computedDurationMs(),
                        fps = currentProject.fps,
                        isPlaying = isPlaying,
                        snappingEnabled = snappingEnabled,
                        timelineZoom = timelineZoom,
                        onJumpStart = { editorVm.jumpToStart() },
                        onPrevFrame = { editorVm.stepFrame(-1) },
                        onPlayPause = { editorVm.togglePlayPause() },
                        onNextFrame = { editorVm.stepFrame(1) },
                        onJumpEnd = { editorVm.jumpToEnd() },
                        onToggleSnap = { editorVm.toggleSnapping() },
                        onZoomChange = { editorVm.setTimelineZoom(it) }
                    )

                    if (!inspectorOpen) {
                        MultiTrackTimeline(
                            project = currentProject,
                            playheadMs = playheadMs,
                            timelineZoom = timelineZoom,
                            selectedClipIds = selectedClipIds,
                            onZoomChange = { editorVm.setTimelineZoom(it) },
                            onSeekMs = { editorVm.seekTo(it) },
                            onSelectClip = { id, multi -> editorVm.selectClip(id, multi) },
                            onClearSelection = { editorVm.clearSelection() },
                            onMoveSelectedClips = { d -> editorVm.moveSelectedClips(d) },
                            onTrimClipEdges = { id, ds, de -> editorVm.trimClipEdges(id, ds, de) },
                            onAddOrSelectTransition = { fromId, toId, existing ->
                                if (existing != null) editorVm.selectTransition(existing.id)
                                else editorVm.addOrUpdateTransitionBetween(fromId, toId, TransitionType.CROSSFADE)
                            },
                            onToggleTrackVisibility = { editorVm.toggleTrackVisibility(it) },
                            onToggleTrackLock = { editorVm.toggleTrackLock(it) },
                            onToggleTrackMute = { editorVm.toggleTrackMute(it) },
                            onToggleTrackSolo = { editorVm.toggleTrackSolo(it) },
                            onReorderTrack = { id, d -> editorVm.reorderTrack(id, d) },
                            onDuplicateTrack = { editorVm.duplicateTrack(it) },
                            onDeleteTrack = { editorVm.deleteTrack(it) },
                            onAddTrack = { editorVm.addTrack(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(0.58f)
                        )
                    } else {
                        MultiTrackTimeline(
                            project = currentProject,
                            playheadMs = playheadMs,
                            timelineZoom = timelineZoom,
                            selectedClipIds = selectedClipIds,
                            onZoomChange = { editorVm.setTimelineZoom(it) },
                            onSeekMs = { editorVm.seekTo(it) },
                            onSelectClip = { id, multi -> editorVm.selectClip(id, multi) },
                            onClearSelection = { editorVm.clearSelection() },
                            onMoveSelectedClips = { d -> editorVm.moveSelectedClips(d) },
                            onTrimClipEdges = { id, ds, de -> editorVm.trimClipEdges(id, ds, de) },
                            onAddOrSelectTransition = { fromId, toId, existing ->
                                if (existing != null) editorVm.selectTransition(existing.id)
                                else editorVm.addOrUpdateTransitionBetween(fromId, toId, TransitionType.CROSSFADE)
                            },
                            onToggleTrackVisibility = { editorVm.toggleTrackVisibility(it) },
                            onToggleTrackLock = { editorVm.toggleTrackLock(it) },
                            onToggleTrackMute = { editorVm.toggleTrackMute(it) },
                            onToggleTrackSolo = { editorVm.toggleTrackSolo(it) },
                            onReorderTrack = { id, d -> editorVm.reorderTrack(id, d) },
                            onDuplicateTrack = { editorVm.duplicateTrack(it) },
                            onDeleteTrack = { editorVm.deleteTrack(it) },
                            onAddTrack = { editorVm.addTrack(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(0.24f)
                        )

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            tonalElevation = 4.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(0.48f)
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                InspectorHeaderBar(
                                    title = activeInspector.name.replace('_', ' '),
                                    onClose = { editorVm.openInspector(EditorInspectorTab.NONE) }
                                )
                                Box(modifier = Modifier.weight(1f)) {
                                    EditorInspectorRouter(
                                        activeTab = activeInspector,
                                        project = currentProject,
                                        selectedClip = primarySelectedClip,
                                        activeTransition = activeTransition,
                                        playheadMs = playheadMs,
                                        copiedKeyframeExists = copiedKeyframe != null,
                                        customPresets = customPresets,
                                        customStickers = customStickers,
                                        customFonts = customFonts,
                                        brushColor = brushColor,
                                        brushSize = brushSize,
                                        brushOpacity = brushOpacity,
                                        isEraser = isEraser,
                                        isRecording = isRecording,
                                        isRecordingPaused = isRecordingPaused,
                                        recordingElapsed = recordingElapsed,
                                        recordingAmp = recordingAmp,
                                        recordings = recordings,
                                        cameraTrackTarget = cameraTrackTarget,
                                        cameraTrackResultLabel = cameraTrackResultLabel,
                                        exportState = exportState,
                                        editorVm = editorVm
                                    )
                                }
                            }
                        }
                    }

                    EditorContextualToolDock(
                        selectedClip = primarySelectedClip,
                        selectedCount = selectedClipIds.size,
                        activeInspector = activeInspector,
                        onAddMediaClick = { showAddMediaSourceDialog = true },
                        onAddTextClick = { editorVm.addTextLayer() },
                        onAddStickerClick = { editorVm.openInspector(EditorInspectorTab.STICKER_EDIT) },
                        onAddDrawClick = { editorVm.addHandwritingLayer() },
                        onAddNullClick = { editorVm.addNullObjectLayer() },
                        onAddCamera3DClick = { editorVm.addCamera3DLayer() },
                        onOpenInspector = { editorVm.openInspector(it) },
                        onSplit = { editorVm.splitClipAtPlayhead() },
                        onCutMid = { editorVm.cutOutMiddleAtPlayhead() },
                        onDuplicate = { editorVm.duplicateSelectedClips() },
                        onDelete = { editorVm.deleteSelectedClips() },
                        onGroup = { editorVm.groupSelectedClips() },
                        onReverse = { editorVm.toggleReverseSelectedClip() },
                        onFreezeFrame = { editorVm.freezeFrameAtPlayhead() },
                        onExtractAudio = { editorVm.extractOrDetachAudio(false) },
                        onDetachAudio = { editorVm.extractOrDetachAudio(true) },
                        onReplaceMedia = { replaceClipPicker.launch(arrayOf("video/*", "image/*", "audio/*")) }
                    )
                }
            }
        }
    }

    if (showAddMediaSourceDialog) {
        AlertDialog(
            onDismissRequest = { showAddMediaSourceDialog = false },
            title = { Text("Import Media to Timeline") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            showAddMediaSourceDialog = false
                            photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PermMedia, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Gallery (Videos & Photos)")
                    }
                    OutlinedButton(
                        onClick = {
                            showAddMediaSourceDialog = false
                            documentPickerLauncher.launch(arrayOf("video/*", "image/*", "audio/*"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Audiotrack, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Device Files, Music & Audio")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddMediaSourceDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showSaveAsDialog) {
        var copyName by remember { mutableStateOf("${currentProject.name} Copy") }
        AlertDialog(
            onDismissRequest = { showSaveAsDialog = false },
            title = { Text("Save Project As") },
            text = {
                OutlinedTextField(
                    value = copyName,
                    onValueChange = { copyName = it },
                    label = { Text("New Project Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    editorVm.saveAs(copyName.trim().ifEmpty { "Untitled Copy" })
                    showSaveAsDialog = false
                }) { Text("Save Copy") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveAsDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun EditorTopStudioBar(
    projectName: String,
    saveStatus: String,
    canUndo: Boolean,
    canRedo: Boolean,
    showSafeArea: Boolean,
    showGrid: Boolean,
    previewQuality: PreviewQuality,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSaveNow: () -> Unit,
    onSaveAsClick: () -> Unit,
    onToggleSafeArea: () -> Unit,
    onToggleGrid: () -> Unit,
    onSelectPreviewQuality: (PreviewQuality) -> Unit,
    onExportClick: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("editor_back_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Studio")
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = projectName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = saveStatus,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            IconButton(onClick = onUndo, enabled = canUndo, modifier = Modifier.testTag("editor_undo_button")) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
            }
            IconButton(onClick = onRedo, enabled = canRedo, modifier = Modifier.testTag("editor_redo_button")) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
            }

            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.testTag("editor_more_menu")) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Editor Options")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Save Project Now") },
                        leadingIcon = { Icon(Icons.Default.Save, null) },
                        onClick = { menuOpen = false; onSaveNow() }
                    )
                    DropdownMenuItem(
                        text = { Text("Save As Copy...") },
                        leadingIcon = { Icon(Icons.Default.SaveAs, null) },
                        onClick = { menuOpen = false; onSaveAsClick() }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(if (showGrid) "Hide Thirds Grid" else "Show Thirds Grid") },
                        leadingIcon = { Icon(Icons.Default.GridOn, null) },
                        onClick = { menuOpen = false; onToggleGrid() }
                    )
                    DropdownMenuItem(
                        text = { Text(if (showSafeArea) "Hide Safe-Area Guides" else "Show Safe-Area Guides") },
                        leadingIcon = { Icon(Icons.Default.Visibility, null) },
                        onClick = { menuOpen = false; onToggleSafeArea() }
                    )
                    HorizontalDivider()
                    PreviewQuality.entries.forEach { q ->
                        DropdownMenuItem(
                            text = { Text("Preview: ${q.label}" + if (q == previewQuality) " ✓" else "") },
                            onClick = { menuOpen = false; onSelectPreviewQuality(q) }
                        )
                    }
                }
            }

            Spacer(Modifier.width(4.dp))
            Button(
                onClick = onExportClick,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                    .height(36.dp)
                    .testTag("editor_export_button")
            ) {
                Icon(Icons.Default.FileUpload, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Export", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun EditorTransportBar(
    playheadMs: Long,
    totalDurationMs: Long,
    fps: Int,
    isPlaying: Boolean,
    snappingEnabled: Boolean,
    timelineZoom: Float,
    onJumpStart: () -> Unit,
    onPrevFrame: () -> Unit,
    onPlayPause: () -> Unit,
    onNextFrame: () -> Unit,
    onJumpEnd: () -> Unit,
    onToggleSnap: () -> Unit,
    onZoomChange: (Float) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${StudioFormatter.formatTimecode(playheadMs, fps)} / ${StudioFormatter.formatTimecode(totalDurationMs, fps)}",
                fontFamily = JetBrainsMonoFontFamily,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onJumpStart, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Start", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onPrevFrame, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous Frame", modifier = Modifier.size(20.dp))
                }
                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("editor_play_pause_button")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onNextFrame, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Frame", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onJumpEnd, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.SkipNext, contentDescription = "End", modifier = Modifier.size(18.dp))
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleSnap, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = "Toggle Snapping",
                        tint = if (snappingEnabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = "${String.format(java.util.Locale.US, "%.1fx", timelineZoom)}",
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            val next = when {
                                timelineZoom < 1f -> 1f
                                timelineZoom < 2f -> 2f
                                timelineZoom < 3.5f -> 4f
                                else -> 0.5f
                            }
                            onZoomChange(next)
                        }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun EditorContextualToolDock(
    selectedClip: TimelineClip?,
    selectedCount: Int,
    activeInspector: EditorInspectorTab,
    onAddMediaClick: () -> Unit,
    onAddTextClick: () -> Unit,
    onAddStickerClick: () -> Unit,
    onAddDrawClick: () -> Unit,
    onAddNullClick: () -> Unit,
    onAddCamera3DClick: () -> Unit,
    onOpenInspector: (EditorInspectorTab) -> Unit,
    onSplit: () -> Unit,
    onCutMid: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onGroup: () -> Unit,
    onReverse: () -> Unit,
    onFreezeFrame: () -> Unit,
    onExtractAudio: () -> Unit,
    onDetachAudio: () -> Unit,
    onReplaceMedia: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectedClip == null) {
                DockActionChip(Icons.Default.Add, "+ Media", onClick = onAddMediaClick, tag = "dock_add_media")
                DockActionChip(Icons.Default.TextFields, "+ Text", onClick = onAddTextClick, tag = "dock_add_text")
                DockActionChip(Icons.Default.EmojiEmotions, "+ Sticker", selected = activeInspector == EditorInspectorTab.STICKER_EDIT, onClick = onAddStickerClick)
                DockActionChip(Icons.Default.Draw, "+ Draw", onClick = onAddDrawClick)
                DockActionChip(Icons.Default.ControlCamera, "+ Null", onClick = onAddNullClick, tag = "dock_add_null")
                DockActionChip(Icons.Default.ViewInAr, "+ 3D Cam", onClick = onAddCamera3DClick, tag = "dock_add_camera3d")
                DockActionChip(Icons.Default.Mic, "Voiceover", selected = activeInspector == EditorInspectorTab.VOICE_RECORDER, onClick = { onOpenInspector(EditorInspectorTab.VOICE_RECORDER) })
                DockActionChip(Icons.Default.GraphicEq, "Mixer", selected = activeInspector == EditorInspectorTab.AUDIO_MIXER, onClick = { onOpenInspector(EditorInspectorTab.AUDIO_MIXER) })
                DockActionChip(Icons.Default.AspectRatio, "Canvas", selected = activeInspector == EditorInspectorTab.CANVAS_SETTINGS, onClick = { onOpenInspector(EditorInspectorTab.CANVAS_SETTINGS) })
            } else {
                if (selectedClip.mediaType == MediaType.CAMERA_3D) {
                    DockActionChip(Icons.Default.ViewInAr, "3D Camera", selected = activeInspector == EditorInspectorTab.CAMERA_3D_RIG, onClick = { onOpenInspector(EditorInspectorTab.CAMERA_3D_RIG) })
                }
                if (selectedClip.mediaType == MediaType.TEXT) {
                    DockActionChip(Icons.Default.TextFields, "Edit Text", selected = activeInspector == EditorInspectorTab.TEXT_EDIT, onClick = { onOpenInspector(EditorInspectorTab.TEXT_EDIT) })
                }
                if (selectedClip.mediaType == MediaType.HANDWRITING) {
                    DockActionChip(Icons.Default.Draw, "Draw Pen", selected = activeInspector == EditorInspectorTab.HANDWRITING, onClick = { onOpenInspector(EditorInspectorTab.HANDWRITING) })
                }
                if (selectedClip.mediaType == MediaType.STICKER) {
                    DockActionChip(Icons.Default.EmojiEmotions, "Sticker", selected = activeInspector == EditorInspectorTab.STICKER_EDIT, onClick = { onOpenInspector(EditorInspectorTab.STICKER_EDIT) })
                }
                DockActionChip(Icons.Default.ContentCut, "Split", onClick = onSplit, tag = "dock_split_clip")
                DockActionChip(Icons.Default.ContentCut, "Cut Mid", onClick = onCutMid)
                DockActionChip(Icons.Default.Transform, "Transform", selected = activeInspector == EditorInspectorTab.TRANSFORM, onClick = { onOpenInspector(EditorInspectorTab.TRANSFORM) })
                DockActionChip(Icons.Default.Animation, "Animate", selected = activeInspector == EditorInspectorTab.ANIMATION, onClick = { onOpenInspector(EditorInspectorTab.ANIMATION) })
                if (selectedClip.mediaType == MediaType.VIDEO || selectedClip.mediaType == MediaType.IMAGE) {
                    DockActionChip(Icons.Default.AutoFixHigh, "Key / BG", selected = activeInspector == EditorInspectorTab.KEYING_BG_REMOVAL, onClick = { onOpenInspector(EditorInspectorTab.KEYING_BG_REMOVAL) }, tag = "dock_keying_bg")
                }
                if (selectedClip.mediaType == MediaType.VIDEO) {
                    DockActionChip(Icons.Default.CenterFocusStrong, "Track Cam", selected = activeInspector == EditorInspectorTab.CAMERA_TRACKER, onClick = { onOpenInspector(EditorInspectorTab.CAMERA_TRACKER) }, tag = "dock_track_cam")
                }
                DockActionChip(Icons.Default.Speed, "Speed", selected = activeInspector == EditorInspectorTab.SPEED, onClick = { onOpenInspector(EditorInspectorTab.SPEED) })
                if (selectedClip.mediaType == MediaType.VIDEO || selectedClip.mediaType == MediaType.IMAGE) {
                    DockActionChip(Icons.Default.ColorLens, "Colour/LUT", selected = activeInspector == EditorInspectorTab.COLOR_FILTERS, onClick = { onOpenInspector(EditorInspectorTab.COLOR_FILTERS) })
                    DockActionChip(Icons.Default.BlurOn, "Effects", selected = activeInspector == EditorInspectorTab.EFFECTS, onClick = { onOpenInspector(EditorInspectorTab.EFFECTS) })
                }
                DockActionChip(Icons.Default.Layers, "Style/Blend", selected = activeInspector == EditorInspectorTab.STYLE_BLEND, onClick = { onOpenInspector(EditorInspectorTab.STYLE_BLEND) })
                if (selectedClip.mediaType == MediaType.VIDEO || selectedClip.mediaType == MediaType.AUDIO) {
                    DockActionChip(Icons.Default.Tune, "Audio DSP", selected = activeInspector == EditorInspectorTab.AUDIO_STUDIO, onClick = { onOpenInspector(EditorInspectorTab.AUDIO_STUDIO) })
                }
                if (selectedClip.mediaType == MediaType.VIDEO) {
                    DockActionChip(Icons.Default.Replay, if (selectedClip.isReversed) "Unreverse" else "Reverse", onClick = onReverse)
                    DockActionChip(Icons.Default.Camera, "Freeze", onClick = onFreezeFrame)
                    DockActionChip(Icons.Default.MusicNote, "Extract Audio", onClick = onExtractAudio)
                    DockActionChip(Icons.Default.Audiotrack, "Detach Audio", onClick = onDetachAudio)
                }
                if (selectedClip.mediaUri != null) {
                    DockActionChip(Icons.Default.SwapHoriz, "Replace", onClick = onReplaceMedia)
                }
                DockActionChip(Icons.Default.ContentCopy, "Duplicate", onClick = onDuplicate)
                if (selectedCount >= 2) {
                    DockActionChip(Icons.Default.GroupWork, "Group", onClick = onGroup)
                }
                DockActionChip(Icons.Default.Delete, "Delete", isDanger = true, onClick = onDelete, tag = "dock_delete_clip")
            }
        }
    }
}

@Composable
private fun DockActionChip(
    icon: ImageVector,
    label: String,
    selected: Boolean = false,
    isDanger: Boolean = false,
    tag: String? = null,
    onClick: () -> Unit
) {
    val bg = when {
        selected -> MaterialTheme.colorScheme.primary
        isDanger -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val fg = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        isDanger -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Surface(
        color = bg,
        border = BorderStroke(1.dp, borderColor),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(16.dp))
            Spacer(Modifier.height(2.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = fg, maxLines = 1)
        }
    }
}

@Composable
private fun InspectorHeaderBar(
    title: String,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Close Inspector", modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun EditorInspectorRouter(
    activeTab: EditorInspectorTab,
    project: ZuvyraProject,
    selectedClip: TimelineClip?,
    activeTransition: com.example.core.model.ClipTransition?,
    playheadMs: Long,
    copiedKeyframeExists: Boolean,
    customPresets: List<com.example.core.storage.FilterPresetEntity>,
    customStickers: List<com.example.core.storage.StickerAssetEntity>,
    customFonts: List<com.example.core.storage.CustomFontEntity>,
    brushColor: Long,
    brushSize: Float,
    brushOpacity: Float,
    isEraser: Boolean,
    isRecording: Boolean,
    isRecordingPaused: Boolean,
    recordingElapsed: Long,
    recordingAmp: Float,
    recordings: List<com.example.core.media.LocalRecording>,
    cameraTrackTarget: Pair<Float, Float>,
    cameraTrackResultLabel: String?,
    exportState: com.example.engine.ExportState,
    editorVm: EditorViewModel
) {
    when (activeTab) {
        EditorInspectorTab.TRANSFORM -> if (selectedClip != null) {
            TransformAndKeyframePanel(
                clip = selectedClip,
                allClips = project.tracks.flatMap { it.clips },
                playheadMs = playheadMs,
                hasCopiedKeyframe = copiedKeyframeExists,
                onUpdateTransform = { editorVm.updateTransform(selectedClip.id, it) },
                onSelectParent = { parentId -> editorVm.setClipParent(selectedClip.id, parentId) },
                onResetTransform = { editorVm.resetTransform(selectedClip.id) },
                onRotate90 = { editorVm.rotateClip90(selectedClip.id) },
                onFlipH = { editorVm.flipClipHorizontal(selectedClip.id) },
                onFlipV = { editorVm.flipClipVertical(selectedClip.id) },
                onSetFitMode = { editorVm.setFitMode(selectedClip.id, it) },
                onAddKeyframe = { prop, v -> editorVm.addOrUpdateKeyframeAtPlayhead(selectedClip.id, prop, v) },
                onRemoveKeyframe = { kfId -> editorVm.removeKeyframe(selectedClip.id, kfId) },
                onJumpKeyframe = { fwd -> editorVm.jumpToAdjacentKeyframe(selectedClip.id, fwd) },
                onCopyKeyframe = { editorVm.copyKeyframe(it) },
                onPasteKeyframe = { editorVm.pasteKeyframeAtPlayhead(selectedClip.id) },
                onUpdateKeyframeEasing = { kfId, e, p1x, p1y, p2x, p2y ->
                    editorVm.updateKeyframeEasing(selectedClip.id, kfId, e, p1x, p1y, p2x, p2y)
                }
            )
        }
        EditorInspectorTab.ANIMATION -> if (selectedClip != null) {
            AnimationPanel(
                clip = selectedClip,
                onUpdateAnimation = { editorVm.updateClipAnimation(selectedClip.id, it) }
            )
        }
        EditorInspectorTab.KEYING_BG_REMOVAL -> if (selectedClip != null) {
            KeyingAndBgRemovalPanel(
                clip = selectedClip,
                onUpdateChromaKey = { editorVm.updateChromaKey(selectedClip.id, it) },
                onUpdateAlphaKey = { editorVm.updateAlphaKey(selectedClip.id, it) },
                onUpdateBgRemoval = { editorVm.updateBgRemoval(selectedClip.id, it) },
                onRunBgRemoval = { editorVm.runOfflineBackgroundRemoval(selectedClip.id) },
                onClearBgCutout = { editorVm.clearBgRemovalCutout(selectedClip.id) }
            )
        }
        EditorInspectorTab.CAMERA_3D_RIG -> if (selectedClip != null) {
            Camera3DRigPanel(
                clip = selectedClip,
                onUpdateCamera3D = { editorVm.updateCamera3D(selectedClip.id, it) },
                onAddKeyframe = { prop, v -> editorVm.addOrUpdateKeyframeAtPlayhead(selectedClip.id, prop, v) }
            )
        }
        EditorInspectorTab.CAMERA_TRACKER -> if (selectedClip != null) {
            CameraTrackerPanel(
                clip = selectedClip,
                targetX = cameraTrackTarget.first,
                targetY = cameraTrackTarget.second,
                statusLabel = cameraTrackResultLabel,
                onResetTarget = { editorVm.setCameraTrackTarget(0f, 0f) },
                onRunTrack = { bakeNull -> editorVm.runCameraTracking(selectedClip.id, bakeNull) }
            )
        }
        EditorInspectorTab.SPEED -> if (selectedClip != null) {
            SpeedCurvePanel(
                clip = selectedClip,
                onUpdateSpeed = { editorVm.updateSpeedConfig(selectedClip.id, it) }
            )
        }
        EditorInspectorTab.COLOR_FILTERS -> if (selectedClip != null) {
            ColorAndFiltersPanel(
                clip = selectedClip,
                customPresets = customPresets,
                onUpdateColor = { editorVm.updateColorAdjustments(selectedClip.id, it) },
                onSavePreset = { n, adj -> editorVm.saveCustomFilterPreset(n, adj) },
                onApplyPreset = { editorVm.applyFilterPresetEntity(selectedClip.id, it) },
                onImportCubeLut = { uri -> editorVm.importCubeLutUri(selectedClip.id, uri) }
            )
        }
        EditorInspectorTab.EFFECTS -> if (selectedClip != null) {
            VideoEffectsPanel(
                clip = selectedClip,
                onAddEffect = { editorVm.addVideoEffect(selectedClip.id, it) },
                onUpdateEffects = { editorVm.updateVideoEffects(selectedClip.id, it) }
            )
        }
        EditorInspectorTab.STYLE_BLEND -> if (selectedClip != null) {
            StyleAndBlendPanel(
                clip = selectedClip,
                onUpdateStyle = { editorVm.updateLayerStyle(selectedClip.id, it) }
            )
        }
        EditorInspectorTab.TEXT_EDIT -> if (selectedClip != null) {
            TextEditorPanel(
                clip = selectedClip,
                customFonts = customFonts,
                onUpdateText = { editorVm.updateTextData(selectedClip.id, it) },
                onImportFont = { uri, tag -> editorVm.importCustomFontUri(uri, tag) }
            )
        }
        EditorInspectorTab.HANDWRITING -> if (selectedClip != null) {
            HandwritingPanel(
                clip = selectedClip,
                brushColorArgb = brushColor,
                brushSize = brushSize,
                brushOpacity = brushOpacity,
                isEraser = isEraser,
                onBrushColor = { editorVm.setBrushColor(it) },
                onBrushSize = { editorVm.setBrushSize(it) },
                onBrushOpacity = { editorVm.setBrushOpacity(it) },
                onEraserToggle = { editorVm.setEraserMode(it) },
                onUndoStroke = { editorVm.undoLastHandwritingStroke() },
                onToggleAnimateDraw = { editorVm.setHandwritingAnimateStrokes(selectedClip.id, it) }
            )
        }
        EditorInspectorTab.STICKER_EDIT -> {
            StickersLibraryPanel(
                selectedClip = selectedClip,
                customStickers = customStickers,
                onAddSticker = { data, label -> editorVm.addStickerLayer(data, label) },
                onUpdateSticker = { data -> selectedClip?.let { editorVm.updateStickerData(it.id, data) } },
                onImportCustomSticker = { uri -> editorVm.importCustomStickerUri(uri) }
            )
        }
        EditorInspectorTab.AUDIO_STUDIO -> if (selectedClip != null) {
            AudioStudioPanel(
                clip = selectedClip,
                playheadMs = playheadMs,
                onUpdateAudio = { editorVm.updateClipAudio(selectedClip.id, it) },
                onApplyEqPreset = { editorVm.applyEqPreset(selectedClip.id, it) }
            )
        }
        EditorInspectorTab.AUDIO_MIXER -> {
            MultiTrackMixerPanel(
                tracks = project.tracks,
                onUpdateTrackMix = { id, v, p -> editorVm.updateTrackMix(id, v, p) },
                onToggleMute = { editorVm.toggleTrackMute(it) },
                onToggleSolo = { editorVm.toggleTrackSolo(it) }
            )
        }
        EditorInspectorTab.VOICE_RECORDER -> {
            VoiceRecorderPanel(
                isRecording = isRecording,
                isPaused = isRecordingPaused,
                elapsedMs = recordingElapsed,
                amplitude = recordingAmp,
                recordings = recordings,
                voiceRecorder = editorVm.voiceRecorder,
                onStart = { editorVm.startVoiceRecording() },
                onTogglePause = { editorVm.togglePauseVoiceRecording() },
                onStop = { add -> editorVm.stopVoiceRecording(add) },
                onAddRecording = { editorVm.addRecordingToTimeline(it) },
                onRenameRecording = { f, n -> editorVm.renameRecording(f, n) },
                onDeleteRecording = { editorVm.deleteRecording(it) }
            )
        }
        EditorInspectorTab.TRANSITION_EDIT -> if (activeTransition != null) {
            TransitionEditorPanel(
                transition = activeTransition,
                onUpdateTransition = { editorVm.updateTransition(it) }
            )
        }
        EditorInspectorTab.CANVAS_SETTINGS -> {
            CanvasSettingsPanel(
                project = project,
                onUpdateCanvas = { ratio, res, fps -> editorVm.updateProjectCanvas(ratio, res, fps) }
            )
        }
        EditorInspectorTab.EXPORT -> {
            ExportStudioPanel(
                project = project,
                exportState = exportState,
                exportEngine = editorVm.exportEngine,
                capabilities = editorVm.deviceCapabilities,
                onConfigChange = { editorVm.updateExportConfig(it) },
                onStartExport = { editorVm.startExport(it) },
                onCancelExport = { editorVm.cancelExport() },
                onResetExport = { editorVm.resetExportState() },
                onOpenOutput = { f, u -> editorVm.openExportedFile(f, u) },
                onShareOutput = { f, u -> editorVm.shareExportedFile(f, u) }
            )
        }
        EditorInspectorTab.NONE -> Unit
    }
}

@Composable
private fun EditorSideIdleSummary(
    project: ZuvyraProject,
    selectedClip: TimelineClip?,
    onOpenCanvas: () -> Unit,
    onOpenMixer: () -> Unit,
    onOpenExport: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Inspector", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = if (selectedClip != null) "Selected: ${selectedClip.displayName}" else "No clip selected",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "Resolution: ${project.width}×${project.height} • ${project.fps} FPS • ${project.tracks.size} tracks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        OutlinedButton(onClick = onOpenCanvas, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.AspectRatio, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Project Canvas & FPS")
        }
        OutlinedButton(onClick = onOpenMixer, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.GraphicEq, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Multi-Track Mixer")
        }
        Button(onClick = onOpenExport, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.FileUpload, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Export Studio")
        }
    }
}

@Composable
private fun EditorEmptyWorkspace(
    recentProjects: List<ProjectEntity>,
    onCreateNewProjectClick: () -> Unit,
    onOpenExistingProject: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MovieCreation,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Text("No Active Timeline Open", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "Create a new studio project or pick a local project below to enter the multi-track editor.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onCreateNewProjectClick,
                        modifier = Modifier.testTag("editor_empty_new_project")
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Create New Project")
                    }
                }
            }
        }

        if (recentProjects.isNotEmpty()) {
            item {
                Text("Open Existing Project", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(recentProjects, key = { it.id }) { proj ->
                ProjectListCard(
                    project = proj,
                    onOpen = { onOpenExistingProject(proj.id) },
                    onRename = {},
                    onDuplicate = {},
                    onDelete = {},
                    onSharePackage = {}
                )
            }
        }
    }
}
