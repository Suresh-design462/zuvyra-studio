package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ZuvyraApplication
import com.example.core.media.LocalRecording
import com.example.core.media.MediaInspectResult
import com.example.core.media.MediaInspector
import com.example.core.media.OfflineMediaProcessor
import com.example.core.media.OfflineVisionAndTracking
import com.example.core.model.AlphaKeyConfig
import com.example.core.model.AspectRatioOption
import com.example.core.model.AudioEffectSettings
import com.example.core.model.BgRemovalConfig
import com.example.core.model.BlendModeType
import com.example.core.model.BuiltInFilterType
import com.example.core.model.Camera3DConfig
import com.example.core.model.ChromaKeyConfig
import com.example.core.model.ClipAnimationConfig
import com.example.core.model.ClipAudioSettings
import com.example.core.model.ClipTransition
import com.example.core.model.ColorAdjustments
import com.example.core.model.EasingType
import com.example.core.model.EqPreset
import com.example.core.model.EqSettings
import com.example.core.model.ExportConfig
import com.example.core.model.FitMode
import com.example.core.model.HandwritingData
import com.example.core.model.HandwritingStroke
import com.example.core.model.Keyframe
import com.example.core.model.KeyframeProperty
import com.example.core.model.LayerStyleConfig
import com.example.core.model.MediaType
import com.example.core.model.ResolutionPreset
import com.example.core.model.SpeedCurveConfig
import com.example.core.model.StickerCategory
import com.example.core.model.StickerOverlayData
import com.example.core.model.StickerShapeKind
import com.example.core.model.TextOverlayData
import com.example.core.model.TimelineClip
import com.example.core.model.TimelineTrack
import com.example.core.model.TrackType
import com.example.core.model.TransformConfig
import com.example.core.model.TransitionType
import com.example.core.model.VideoEffectItem
import com.example.core.model.VideoEffectType
import com.example.core.model.ZuvyraProject
import com.example.core.storage.CustomFontEntity
import com.example.core.storage.FilterPresetEntity
import com.example.core.storage.MediaAssetEntity
import com.example.core.storage.PreviewQuality
import com.example.core.storage.ProjectRepository
import com.example.core.storage.StickerAssetEntity
import com.example.engine.ExportState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.abs

enum class EditorInspectorTab {
    NONE,
    TRANSFORM,
    ANIMATION,
    SPEED,
    COLOR_FILTERS,
    EFFECTS,
    STYLE_BLEND,
    KEYING_BG_REMOVAL,
    CAMERA_3D_RIG,
    CAMERA_TRACKER,
    TEXT_EDIT,
    HANDWRITING,
    STICKER_EDIT,
    AUDIO_STUDIO,
    AUDIO_MIXER,
    VOICE_RECORDER,
    TRANSITION_EDIT,
    CANVAS_SETTINGS,
    EXPORT
}

class EditorViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as ZuvyraApplication).container
    val repo = container.projectRepository
    val thumbnailEngine = container.thumbnailEngine
    val voiceRecorder = container.voiceRecorder
    val exportEngine = container.exportEngine
    val deviceCapabilities = container.deviceCapabilities

    private val _project = MutableStateFlow<ZuvyraProject?>(null)
    val project: StateFlow<ZuvyraProject?> = _project.asStateFlow()

    private val _playheadMs = MutableStateFlow(0L)
    val playheadMs: StateFlow<Long> = _playheadMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _selectedClipIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedClipIds: StateFlow<Set<String>> = _selectedClipIds.asStateFlow()

    private val _selectedTransitionId = MutableStateFlow<String?>(null)
    val selectedTransitionId: StateFlow<String?> = _selectedTransitionId.asStateFlow()

    private val _activeInspector = MutableStateFlow(EditorInspectorTab.NONE)
    val activeInspector: StateFlow<EditorInspectorTab> = _activeInspector.asStateFlow()

    private val _timelineZoom = MutableStateFlow(1f) // 1f = 60dp per second
    val timelineZoom: StateFlow<Float> = _timelineZoom.asStateFlow()

    private val _snappingEnabled = MutableStateFlow(true)
    val snappingEnabled: StateFlow<Boolean> = _snappingEnabled.asStateFlow()

    private val _showSafeAreaGuides = MutableStateFlow(false)
    val showSafeAreaGuides: StateFlow<Boolean> = _showSafeAreaGuides.asStateFlow()

    private val _showGridGuides = MutableStateFlow(false)
    val showGridGuides: StateFlow<Boolean> = _showGridGuides.asStateFlow()

    private val _isFullScreenPreview = MutableStateFlow(false)
    val isFullScreenPreview: StateFlow<Boolean> = _isFullScreenPreview.asStateFlow()

    private val _previewQuality = MutableStateFlow(PreviewQuality.AUTO)
    val previewQuality: StateFlow<PreviewQuality> = _previewQuality.asStateFlow()

    private val _isHandwritingMode = MutableStateFlow(false)
    val isHandwritingMode: StateFlow<Boolean> = _isHandwritingMode.asStateFlow()

    private val _activeBrushColor = MutableStateFlow(0xFF38BDF8L)
    val activeBrushColor: StateFlow<Long> = _activeBrushColor.asStateFlow()

    private val _activeBrushSize = MutableStateFlow(8f)
    val activeBrushSize: StateFlow<Float> = _activeBrushSize.asStateFlow()

    private val _activeBrushOpacity = MutableStateFlow(1f)
    val activeBrushOpacity: StateFlow<Float> = _activeBrushOpacity.asStateFlow()

    private val _activeEraserMode = MutableStateFlow(false)
    val activeEraserMode: StateFlow<Boolean> = _activeEraserMode.asStateFlow()

    private val _copiedKeyframe = MutableStateFlow<Keyframe?>(null)
    val copiedKeyframe: StateFlow<Keyframe?> = _copiedKeyframe.asStateFlow()

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    private val _busyMessage = MutableStateFlow<String?>(null)
    val busyMessage: StateFlow<String?> = _busyMessage.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _cameraTrackTarget = MutableStateFlow(0f to 0f)
    val cameraTrackTarget: StateFlow<Pair<Float, Float>> = _cameraTrackTarget.asStateFlow()

    private val _cameraTrackResultLabel = MutableStateFlow<String?>(null)
    val cameraTrackResultLabel: StateFlow<String?> = _cameraTrackResultLabel.asStateFlow()

    private val _recordings = MutableStateFlow<List<LocalRecording>>(emptyList())
    val recordings: StateFlow<List<LocalRecording>> = _recordings.asStateFlow()

    private val _recordingElapsedMs = MutableStateFlow(0L)
    val recordingElapsedMs: StateFlow<Long> = _recordingElapsedMs.asStateFlow()

    private val _recordingAmplitude = MutableStateFlow(0f)
    val recordingAmplitude: StateFlow<Float> = _recordingAmplitude.asStateFlow()

    private val _isRecordingActive = MutableStateFlow(false)
    val isRecordingActive: StateFlow<Boolean> = _isRecordingActive.asStateFlow()

    private val _isRecordingPaused = MutableStateFlow(false)
    val isRecordingPaused: StateFlow<Boolean> = _isRecordingPaused.asStateFlow()

    val customFilterPresets: StateFlow<List<FilterPresetEntity>> = repo.customFilterPresets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customStickers: StateFlow<List<StickerAssetEntity>> = repo.customStickers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customFonts: StateFlow<List<CustomFontEntity>> = repo.customFonts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val undoStack = ArrayDeque<ZuvyraProject>()
    private val redoStack = ArrayDeque<ZuvyraProject>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val _lastSavedLabel = MutableStateFlow("Saved")
    val lastSavedLabel: StateFlow<String> = _lastSavedLabel.asStateFlow()

    private var autoSaveJob: Job? = null
    private var playbackJob: Job? = null
    private var recorderMeterJob: Job? = null

    init {
        refreshRecordings()
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun showToast(msg: String) {
        _toastMessage.value = msg
    }

    fun openProject(projectId: String, preferRecovery: Boolean = false) {
        viewModelScope.launch {
            val loaded = repo.loadProject(projectId, preferRecovery)
            if (loaded != null) {
                undoStack.clear()
                redoStack.clear()
                syncUndoRedoFlags()
                _project.value = loaded
                _playheadMs.value = 0L
                _selectedClipIds.value = emptySet()
                _activeInspector.value = EditorInspectorTab.NONE
                _lastSavedLabel.value = if (preferRecovery) "Recovered unsaved session" else "Loaded"
                startAutoSaveLoop()
            } else {
                _toastMessage.value = "Could not load project file."
            }
        }
    }

    fun setLoadedProject(project: ZuvyraProject) {
        undoStack.clear()
        redoStack.clear()
        syncUndoRedoFlags()
        _project.value = project
        _playheadMs.value = 0L
        _selectedClipIds.value = emptySet()
        _activeInspector.value = EditorInspectorTab.NONE
        startAutoSaveLoop()
    }

    private fun startAutoSaveLoop() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            while (true) {
                delay(15_000L)
                val current = _project.value ?: continue
                runCatching {
                    repo.saveAutoRecovery(current)
                    _lastSavedLabel.value = "Auto-saved"
                }
            }
        }
    }

    fun saveNow() {
        val current = _project.value ?: return
        viewModelScope.launch {
            val updated = refreshProjectThumbnailIfNeeded(current)
            _project.value = updated
            repo.saveProject(updated)
            _lastSavedLabel.value = "Saved"
            _toastMessage.value = "Project saved locally"
        }
    }

    fun saveAs(newName: String) {
        val current = _project.value ?: return
        viewModelScope.launch {
            val saved = repo.saveAsProject(current, newName)
            _project.value = saved
            _lastSavedLabel.value = "Saved as ${saved.name}"
            _toastMessage.value = "Saved new copy \"${saved.name}\""
        }
    }

    private suspend fun refreshProjectThumbnailIfNeeded(proj: ZuvyraProject): ZuvyraProject {
        val firstVisual = proj.tracks
            .flatMap { it.clips }
            .firstOrNull { it.mediaUri != null && (it.mediaType == MediaType.VIDEO || it.mediaType == MediaType.IMAGE) }
        if (firstVisual?.mediaUri != null) {
            val path = thumbnailEngine.saveProjectThumbnail(
                Uri.parse(firstVisual.mediaUri),
                firstVisual.mediaType,
                proj.id
            )
            if (path != null) return proj.copy(thumbnailPath = path)
        }
        return proj
    }

    private fun mutateProject(recordUndo: Boolean = true, transform: (ZuvyraProject) -> ZuvyraProject) {
        val current = _project.value ?: return
        if (recordUndo) {
            undoStack.addLast(current)
            if (undoStack.size > 50) undoStack.removeFirst()
            redoStack.clear()
            syncUndoRedoFlags()
        }
        val next = transform(current).copy(updatedAt = System.currentTimeMillis())
        _project.value = next
        _lastSavedLabel.value = "Unsaved edits"
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val current = _project.value ?: return
        redoStack.addLast(current)
        _project.value = undoStack.removeLast()
        syncUndoRedoFlags()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val current = _project.value ?: return
        undoStack.addLast(current)
        _project.value = redoStack.removeLast()
        syncUndoRedoFlags()
    }

    private fun syncUndoRedoFlags() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    // Playback & Transport
    fun togglePlayPause() {
        if (_isPlaying.value) {
            pausePlayback()
        } else {
            startPlayback()
        }
    }

    fun startPlayback() {
        val dur = _project.value?.computedDurationMs() ?: 0L
        if (dur <= 0L) return
        if (_playheadMs.value >= dur) _playheadMs.value = 0L
        _isPlaying.value = true
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            var lastTick = System.currentTimeMillis()
            while (_isPlaying.value) {
                delay(16L)
                val now = System.currentTimeMillis()
                val delta = (now - lastTick).coerceIn(1L, 100L)
                lastTick = now
                val totalDur = _project.value?.computedDurationMs() ?: 0L
                val nextMs = _playheadMs.value + delta
                if (nextMs >= totalDur) {
                    _playheadMs.value = totalDur
                    _isPlaying.value = false
                } else {
                    _playheadMs.value = nextMs
                }
            }
        }
    }

    fun pausePlayback() {
        _isPlaying.value = false
        playbackJob?.cancel()
    }

    fun seekTo(ms: Long) {
        val maxMs = (_project.value?.computedDurationMs() ?: 0L).coerceAtLeast(0L)
        _playheadMs.value = ms.coerceIn(0L, maxMs)
    }

    fun stepFrame(deltaFrames: Int) {
        pausePlayback()
        val fps = (_project.value?.fps ?: 30).coerceAtLeast(1)
        val frameMs = (1000.0 / fps).toLong().coerceAtLeast(16L)
        seekTo(_playheadMs.value + deltaFrames * frameMs)
    }

    fun jumpToStart() {
        pausePlayback()
        seekTo(0L)
    }

    fun jumpToEnd() {
        pausePlayback()
        seekTo(_project.value?.computedDurationMs() ?: 0L)
    }

    // Selection & UI Toggles
    fun selectClip(clipId: String, multiSelect: Boolean = false) {
        _selectedTransitionId.value = null
        if (!multiSelect) {
            _selectedClipIds.value = setOf(clipId)
        } else {
            val cur = _selectedClipIds.value
            _selectedClipIds.value = if (clipId in cur) cur - clipId else cur + clipId
        }
        val primary = selectedClip()
        _isHandwritingMode.value = primary?.mediaType == MediaType.HANDWRITING
    }

    fun clearSelection() {
        _selectedClipIds.value = emptySet()
        _selectedTransitionId.value = null
        _isHandwritingMode.value = false
        if (_activeInspector.value !in setOf(EditorInspectorTab.CANVAS_SETTINGS, EditorInspectorTab.EXPORT, EditorInspectorTab.AUDIO_MIXER, EditorInspectorTab.VOICE_RECORDER)) {
            _activeInspector.value = EditorInspectorTab.NONE
        }
    }

    fun selectTransition(transitionId: String) {
        _selectedClipIds.value = emptySet()
        _selectedTransitionId.value = transitionId
        _activeInspector.value = EditorInspectorTab.TRANSITION_EDIT
    }

    fun openInspector(tab: EditorInspectorTab) {
        _activeInspector.value = if (_activeInspector.value == tab) EditorInspectorTab.NONE else tab
        if (tab == EditorInspectorTab.HANDWRITING) {
            _isHandwritingMode.value = true
        }
    }

    fun setTimelineZoom(zoom: Float) {
        _timelineZoom.value = zoom.coerceIn(0.25f, 6f)
    }

    fun toggleSnapping() { _snappingEnabled.value = !_snappingEnabled.value }
    fun toggleSafeAreaGuides() { _showSafeAreaGuides.value = !_showSafeAreaGuides.value }
    fun toggleGridGuides() { _showGridGuides.value = !_showGridGuides.value }
    fun toggleFullScreenPreview() { _isFullScreenPreview.value = !_isFullScreenPreview.value }
    fun setPreviewQuality(q: PreviewQuality) { _previewQuality.value = q }

    fun selectedClip(): TimelineClip? {
        val id = _selectedClipIds.value.firstOrNull() ?: return null
        return _project.value?.tracks?.flatMap { it.clips }?.firstOrNull { it.id == id }
    }

    // Add Media & Layers
    fun addMediaAssetToTimeline(asset: MediaAssetEntity) {
        addMediaUriToTimeline(Uri.parse(asset.uriString), MediaType.valueOf(asset.mediaType), asset.displayName, asset.durationMs)
    }

    fun importAndAddMediaUris(uris: List<Uri>) {
        viewModelScope.launch {
            _busyMessage.value = "Inspecting ${uris.size} media file(s)..."
            val errors = mutableListOf<String>()
            for (uri in uris) {
                when (val res = MediaInspector.inspectUri(getApplication(), uri)) {
                    is MediaInspectResult.Success -> {
                        val entity = MediaAssetEntity(
                            id = UUID.randomUUID().toString(),
                            uriString = res.uri.toString(),
                            displayName = res.displayName,
                            mimeType = res.mimeType,
                            mediaType = res.mediaType.name,
                            durationMs = res.durationMs,
                            width = res.width,
                            height = res.height,
                            sizeBytes = res.sizeBytes,
                            thumbnailPath = null,
                            folderLabel = "Editor Import",
                            importedAt = System.currentTimeMillis()
                        )
                        repo.registerMediaAsset(entity)
                        addMediaUriToTimeline(res.uri, res.mediaType, res.displayName, res.durationMs)
                    }
                    is MediaInspectResult.UnsupportedOrError -> errors.add("${res.displayName}: ${res.reason}")
                }
            }
            _busyMessage.value = null
            if (errors.isNotEmpty()) {
                _toastMessage.value = errors.first()
            }
        }
    }

    fun addMediaUriToTimeline(uri: Uri, mediaType: MediaType, displayName: String, durationMs: Long) {
        val targetTrackType = when (mediaType) {
            MediaType.VIDEO -> TrackType.VIDEO
            MediaType.IMAGE -> TrackType.IMAGE
            MediaType.AUDIO -> TrackType.AUDIO
            MediaType.TEXT -> TrackType.TEXT
            MediaType.STICKER -> TrackType.STICKER
            MediaType.HANDWRITING -> TrackType.HANDWRITING
            MediaType.NULL_OBJECT -> TrackType.NULL_OBJECT
            MediaType.CAMERA_3D -> TrackType.CAMERA_3D
        }

        val clipId = UUID.randomUUID().toString()
        mutateProject { proj ->
            val existingTrack = proj.tracks.firstOrNull { it.type == targetTrackType && !it.isLocked }
                ?: if (targetTrackType == TrackType.IMAGE) proj.tracks.firstOrNull { it.type == TrackType.VIDEO && !it.isLocked } else null

            val targetTrack = existingTrack ?: TimelineTrack(
                id = UUID.randomUUID().toString(),
                name = "${targetTrackType.name.lowercase().replaceFirstChar { it.uppercase() }} ${proj.tracks.size + 1}",
                type = targetTrackType,
                order = proj.tracks.size
            )
            val insertAt = targetTrack.clips.maxOfOrNull { it.timelineStartMs + it.durationMs } ?: 0L
            val newClip = TimelineClip(
                id = clipId,
                trackId = targetTrack.id,
                mediaUri = uri.toString(),
                mediaType = mediaType,
                displayName = displayName,
                timelineStartMs = insertAt,
                durationMs = durationMs.coerceAtLeast(500L),
                sourceStartMs = 0L,
                sourceEndMs = durationMs.coerceAtLeast(500L),
                sourceDurationMs = durationMs.coerceAtLeast(500L)
            )
            val updatedTracks = if (existingTrack != null) {
                proj.tracks.map { if (it.id == targetTrack.id) it.copy(clips = it.clips + newClip) else it }
            } else {
                proj.tracks + targetTrack.copy(clips = listOf(newClip))
            }
            proj.copy(tracks = updatedTracks)
        }
        selectClip(clipId)

        // Extract real audio waveform asynchronously for Video/Audio clips
        if (mediaType == MediaType.AUDIO || mediaType == MediaType.VIDEO) {
            viewModelScope.launch {
                val peaks = MediaInspector.extractAudioWaveform(getApplication(), uri, 64)
                if (peaks.isNotEmpty()) {
                    mutateClip(clipId, recordUndo = false) { it.copy(waveformPeaks = peaks) }
                }
            }
        }
        viewModelScope.launch {
            _project.value?.let { p ->
                val updated = refreshProjectThumbnailIfNeeded(p)
                _project.value = updated
                repo.saveAutoRecovery(updated)
            }
        }
    }

    fun addTextLayer(initialText: String = "ZUVYRA Title") {
        val clipId = UUID.randomUUID().toString()
        val startAt = _playheadMs.value
        mutateProject { proj ->
            val track = proj.tracks.firstOrNull { it.type == TrackType.TEXT && !it.isLocked }
                ?: TimelineTrack(id = UUID.randomUUID().toString(), name = "Text ${proj.tracks.size + 1}", type = TrackType.TEXT, order = proj.tracks.size)
            val clip = TimelineClip(
                id = clipId,
                trackId = track.id,
                mediaType = MediaType.TEXT,
                displayName = initialText.take(20),
                timelineStartMs = startAt,
                durationMs = 3500L,
                textData = TextOverlayData(text = initialText)
            )
            val tracks = if (proj.tracks.any { it.id == track.id }) {
                proj.tracks.map { if (it.id == track.id) it.copy(clips = it.clips + clip) else it }
            } else {
                proj.tracks + track.copy(clips = listOf(clip))
            }
            proj.copy(tracks = tracks)
        }
        selectClip(clipId)
        _activeInspector.value = EditorInspectorTab.TEXT_EDIT
    }

    fun addStickerLayer(stickerData: StickerOverlayData, label: String) {
        val clipId = UUID.randomUUID().toString()
        val startAt = _playheadMs.value
        mutateProject { proj ->
            val track = proj.tracks.firstOrNull { it.type == TrackType.STICKER && !it.isLocked }
                ?: TimelineTrack(id = UUID.randomUUID().toString(), name = "Stickers", type = TrackType.STICKER, order = proj.tracks.size)
            val clip = TimelineClip(
                id = clipId,
                trackId = track.id,
                mediaType = MediaType.STICKER,
                displayName = label,
                timelineStartMs = startAt,
                durationMs = 3000L,
                stickerData = stickerData
            )
            val tracks = if (proj.tracks.any { it.id == track.id }) {
                proj.tracks.map { if (it.id == track.id) it.copy(clips = it.clips + clip) else it }
            } else {
                proj.tracks + track.copy(clips = listOf(clip))
            }
            proj.copy(tracks = tracks)
        }
        selectClip(clipId)
        _activeInspector.value = EditorInspectorTab.STICKER_EDIT
    }

    fun addHandwritingLayer() {
        val clipId = UUID.randomUUID().toString()
        val startAt = _playheadMs.value
        mutateProject { proj ->
            val track = proj.tracks.firstOrNull { it.type == TrackType.HANDWRITING && !it.isLocked }
                ?: TimelineTrack(id = UUID.randomUUID().toString(), name = "Drawing", type = TrackType.HANDWRITING, order = proj.tracks.size)
            val clip = TimelineClip(
                id = clipId,
                trackId = track.id,
                mediaType = MediaType.HANDWRITING,
                displayName = "Handwriting",
                timelineStartMs = startAt,
                durationMs = 4000L,
                handwritingData = HandwritingData()
            )
            val tracks = if (proj.tracks.any { it.id == track.id }) {
                proj.tracks.map { if (it.id == track.id) it.copy(clips = it.clips + clip) else it }
            } else {
                proj.tracks + track.copy(clips = listOf(clip))
            }
            proj.copy(tracks = tracks)
        }
        selectClip(clipId)
        _isHandwritingMode.value = true
        _activeInspector.value = EditorInspectorTab.HANDWRITING
    }

    fun appendHandwritingStroke(stroke: HandwritingStroke) {
        val clip = selectedClip() ?: return
        if (clip.mediaType != MediaType.HANDWRITING) return
        mutateClip(clip.id) { c ->
            val currentData = c.handwritingData ?: HandwritingData()
            c.copy(handwritingData = currentData.copy(strokes = currentData.strokes + stroke))
        }
    }

    fun undoLastHandwritingStroke() {
        val clip = selectedClip() ?: return
        val data = clip.handwritingData ?: return
        if (data.strokes.isEmpty()) return
        mutateClip(clip.id) { it.copy(handwritingData = data.copy(strokes = data.strokes.dropLast(1))) }
    }

    fun setHandwritingAnimateStrokes(clipId: String, animate: Boolean) {
        mutateClip(clipId) { c ->
            val cur = c.handwritingData ?: HandwritingData()
            c.copy(handwritingData = cur.copy(animateStrokes = animate))
        }
    }

    fun setBrushColor(argb: Long) { _activeBrushColor.value = argb; _activeEraserMode.value = false }
    fun setBrushSize(size: Float) { _activeBrushSize.value = size }
    fun setBrushOpacity(opacity: Float) { _activeBrushOpacity.value = opacity }
    fun setEraserMode(eraser: Boolean) { _activeEraserMode.value = eraser }

    // Track Controls
    fun addTrack(type: TrackType) {
        mutateProject { proj ->
            val newTrack = TimelineTrack(
                id = UUID.randomUUID().toString(),
                name = "${type.name.lowercase().replaceFirstChar { it.uppercase() }} ${proj.tracks.count { it.type == type } + 1}",
                type = type,
                order = proj.tracks.size
            )
            proj.copy(tracks = proj.tracks + newTrack)
        }
    }

    fun toggleTrackVisibility(trackId: String) {
        mutateProject { proj ->
            proj.copy(tracks = proj.tracks.map { if (it.id == trackId) it.copy(isVisible = !it.isVisible) else it })
        }
    }

    fun toggleTrackLock(trackId: String) {
        mutateProject { proj ->
            proj.copy(tracks = proj.tracks.map { if (it.id == trackId) it.copy(isLocked = !it.isLocked) else it })
        }
    }

    fun toggleTrackMute(trackId: String) {
        mutateProject { proj ->
            proj.copy(tracks = proj.tracks.map { if (it.id == trackId) it.copy(isMuted = !it.isMuted) else it })
        }
    }

    fun toggleTrackSolo(trackId: String) {
        mutateProject { proj ->
            proj.copy(tracks = proj.tracks.map { if (it.id == trackId) it.copy(isSolo = !it.isSolo) else it })
        }
    }

    fun updateTrackMix(trackId: String, volume: Float, pan: Float) {
        mutateProject(recordUndo = false) { proj ->
            proj.copy(tracks = proj.tracks.map { if (it.id == trackId) it.copy(volume = volume.coerceIn(0f, 2f), pan = pan.coerceIn(-1f, 1f)) else it })
        }
    }

    fun reorderTrack(trackId: String, delta: Int) {
        mutateProject { proj ->
            val sorted = proj.tracks.sortedBy { it.order }.toMutableList()
            val idx = sorted.indexOfFirst { it.id == trackId }
            val targetIdx = (idx + delta).coerceIn(0, sorted.lastIndex)
            if (idx >= 0 && idx != targetIdx) {
                val item = sorted.removeAt(idx)
                sorted.add(targetIdx, item)
            }
            proj.copy(tracks = sorted.mapIndexed { i, t -> t.copy(order = i) })
        }
    }

    fun duplicateTrack(trackId: String) {
        mutateProject { proj ->
            val src = proj.tracks.firstOrNull { it.id == trackId } ?: return@mutateProject proj
            val newTrackId = UUID.randomUUID().toString()
            val dupTrack = src.copy(
                id = newTrackId,
                name = "${src.name} Copy",
                order = proj.tracks.size,
                clips = src.clips.map { it.copy(id = UUID.randomUUID().toString(), trackId = newTrackId) }
            )
            proj.copy(tracks = proj.tracks + dupTrack)
        }
    }

    fun deleteTrack(trackId: String) {
        mutateProject { proj ->
            if (proj.tracks.size <= 1) return@mutateProject proj
            val remaining = proj.tracks.filterNot { it.id == trackId }.mapIndexed { i, t -> t.copy(order = i) }
            proj.copy(tracks = remaining)
        }
    }

    // Clip Editing Operations
    fun mutateClip(clipId: String, recordUndo: Boolean = true, transform: (TimelineClip) -> TimelineClip) {
        mutateProject(recordUndo) { proj ->
            proj.copy(tracks = proj.tracks.map { track ->
                if (track.isLocked) track
                else track.copy(clips = track.clips.map { if (it.id == clipId) transform(it) else it })
            })
        }
    }

    fun moveSelectedClips(deltaMs: Long, snapThresholdMs: Long = 180L) {
        val ids = _selectedClipIds.value
        if (ids.isEmpty()) return
        mutateProject(recordUndo = false) { proj ->
            val otherEdges = proj.tracks.flatMap { it.clips }
                .filter { it.id !in ids }
                .flatMap { listOf(it.timelineStartMs, it.timelineStartMs + it.durationMs) } + 0L + _playheadMs.value

            proj.copy(tracks = proj.tracks.map { track ->
                if (track.isLocked) track else track.copy(clips = track.clips.map { clip ->
                    if (clip.id !in ids) clip else {
                        var candidate = (clip.timelineStartMs + deltaMs).coerceAtLeast(0L)
                        if (_snappingEnabled.value && ids.size == 1) {
                            val closestStart = otherEdges.minByOrNull { abs(it - candidate) }
                            if (closestStart != null && abs(closestStart - candidate) <= snapThresholdMs) {
                                candidate = closestStart
                            } else {
                                val candEnd = candidate + clip.durationMs
                                val closestEnd = otherEdges.minByOrNull { abs(it - candEnd) }
                                if (closestEnd != null && abs(closestEnd - candEnd) <= snapThresholdMs) {
                                    candidate = (closestEnd - clip.durationMs).coerceAtLeast(0L)
                                }
                            }
                        }
                        clip.copy(timelineStartMs = candidate)
                    }
                })
            })
        }
    }

    fun trimClipEdges(clipId: String, deltaStartMs: Long, deltaEndMs: Long) {
        mutateClip(clipId, recordUndo = false) { clip ->
            var newStart = clip.timelineStartMs
            var newDur = clip.durationMs
            var newSrcStart = clip.sourceStartMs
            var newSrcEnd = clip.sourceEndMs

            if (deltaStartMs != 0L) {
                val maxTrimIn = (clip.durationMs - 200L)
                val clampedDelta = deltaStartMs.coerceIn(-clip.timelineStartMs, maxTrimIn)
                val srcDelta = if (clip.mediaType == MediaType.VIDEO || clip.mediaType == MediaType.AUDIO) {
                    clampedDelta.coerceAtLeast(-clip.sourceStartMs)
                } else clampedDelta
                newStart = (clip.timelineStartMs + srcDelta).coerceAtLeast(0L)
                newDur = (clip.durationMs - srcDelta).coerceAtLeast(200L)
                if (clip.mediaType == MediaType.VIDEO || clip.mediaType == MediaType.AUDIO) {
                    newSrcStart = (clip.sourceStartMs + srcDelta).coerceAtLeast(0L)
                }
            }

            if (deltaEndMs != 0L) {
                val targetDur = (newDur + deltaEndMs).coerceAtLeast(200L)
                val maxDur = if (clip.mediaType == MediaType.VIDEO || clip.mediaType == MediaType.AUDIO) {
                    ((clip.sourceDurationMs - newSrcStart) / clip.speedConfig.constantSpeed.coerceAtLeast(0.1f)).toLong().coerceAtLeast(200L)
                } else 600_000L
                newDur = targetDur.coerceAtMost(maxDur)
                if (clip.mediaType == MediaType.VIDEO || clip.mediaType == MediaType.AUDIO) {
                    newSrcEnd = (newSrcStart + (newDur * clip.speedConfig.constantSpeed).toLong()).coerceAtMost(clip.sourceDurationMs)
                }
            }

            clip.copy(
                timelineStartMs = newStart,
                durationMs = newDur,
                sourceStartMs = newSrcStart,
                sourceEndMs = newSrcEnd
            )
        }
    }

    fun splitClipAtPlayhead() {
        val clip = selectedClip() ?: return
        val splitAt = _playheadMs.value
        if (splitAt <= clip.timelineStartMs + 100L || splitAt >= clip.timelineStartMs + clip.durationMs - 100L) {
            _toastMessage.value = "Move playhead inside the selected clip to split."
            return
        }
        val leftDur = splitAt - clip.timelineStartMs
        val rightDur = clip.durationMs - leftDur
        val srcMid = clip.sourceStartMs + (leftDur * clip.speedConfig.constantSpeed).toLong()

        val leftClip = clip.copy(
            durationMs = leftDur,
            sourceEndMs = srcMid.coerceAtMost(clip.sourceEndMs)
        )
        val rightClip = clip.copy(
            id = UUID.randomUUID().toString(),
            timelineStartMs = splitAt,
            durationMs = rightDur,
            sourceStartMs = srcMid.coerceAtMost(clip.sourceEndMs)
        )

        mutateProject { proj ->
            proj.copy(tracks = proj.tracks.map { track ->
                if (track.id != clip.trackId) track else {
                    val idx = track.clips.indexOfFirst { it.id == clip.id }
                    if (idx < 0) track else {
                        val list = track.clips.toMutableList()
                        list[idx] = leftClip
                        list.add(idx + 1, rightClip)
                        track.copy(clips = list)
                    }
                }
            })
        }
        selectClip(rightClip.id)
    }

    fun cutOutMiddleAtPlayhead(cutWidthMs: Long = 500L) {
        val clip = selectedClip() ?: return
        val play = _playheadMs.value
        if (play <= clip.timelineStartMs + 100L || play >= clip.timelineStartMs + clip.durationMs - cutWidthMs - 100L) {
            _toastMessage.value = "Position playhead inside clip to cut a segment."
            return
        }
        val leftDur = play - clip.timelineStartMs
        val rightStart = play + cutWidthMs
        val rightDur = (clip.timelineStartMs + clip.durationMs) - rightStart
        val srcLeftEnd = clip.sourceStartMs + (leftDur * clip.speedConfig.constantSpeed).toLong()
        val srcRightStart = clip.sourceStartMs + ((leftDur + cutWidthMs) * clip.speedConfig.constantSpeed).toLong()

        val leftClip = clip.copy(durationMs = leftDur, sourceEndMs = srcLeftEnd)
        val rightClip = clip.copy(
            id = UUID.randomUUID().toString(),
            timelineStartMs = play,
            durationMs = rightDur,
            sourceStartMs = srcRightStart
        )
        mutateProject { proj ->
            proj.copy(tracks = proj.tracks.map { t ->
                if (t.id != clip.trackId) t else t.copy(clips = t.clips.flatMap { if (it.id == clip.id) listOf(leftClip, rightClip) else listOf(it) })
            })
        }
    }

    fun duplicateSelectedClips() {
        val ids = _selectedClipIds.value
        if (ids.isEmpty()) return
        mutateProject { proj ->
            proj.copy(tracks = proj.tracks.map { track ->
                val dups = track.clips.filter { it.id in ids }.map {
                    it.copy(
                        id = UUID.randomUUID().toString(),
                        timelineStartMs = it.timelineStartMs + it.durationMs + 50L
                    )
                }
                track.copy(clips = track.clips + dups)
            })
        }
    }

    fun deleteSelectedClips() {
        val ids = _selectedClipIds.value
        if (ids.isEmpty()) return
        mutateProject { proj ->
            proj.copy(
                tracks = proj.tracks.map { t -> if (t.isLocked) t else t.copy(clips = t.clips.filterNot { it.id in ids }) },
                transitions = proj.transitions.filterNot { it.fromClipId in ids || it.toClipId in ids }
            )
        }
        clearSelection()
    }

    fun groupSelectedClips() {
        val ids = _selectedClipIds.value
        if (ids.size < 2) {
            _toastMessage.value = "Long-press or multi-select 2+ clips to group."
            return
        }
        val gid = UUID.randomUUID().toString()
        mutateProject { proj ->
            proj.copy(tracks = proj.tracks.map { t ->
                t.copy(clips = t.clips.map { if (it.id in ids) it.copy(groupId = gid) else it })
            })
        }
        _toastMessage.value = "Grouped ${ids.size} clips"
    }

    fun replaceSelectedClipMedia(uri: Uri) {
        val clip = selectedClip() ?: return
        viewModelScope.launch {
            _busyMessage.value = "Inspecting replacement media..."
            when (val res = MediaInspector.inspectUri(getApplication(), uri)) {
                is MediaInspectResult.Success -> {
                    mutateClip(clip.id) { c ->
                        c.copy(
                            mediaUri = res.uri.toString(),
                            mediaType = res.mediaType,
                            displayName = res.displayName,
                            sourceStartMs = 0L,
                            sourceEndMs = res.durationMs.coerceAtMost(c.durationMs),
                            sourceDurationMs = res.durationMs
                        )
                    }
                    _toastMessage.value = "Replaced clip with ${res.displayName}"
                }
                is MediaInspectResult.UnsupportedOrError -> _toastMessage.value = res.reason
            }
            _busyMessage.value = null
        }
    }

    fun freezeFrameAtPlayhead() {
        val clip = selectedClip() ?: return
        if (clip.mediaType != MediaType.VIDEO || clip.mediaUri == null) {
            _toastMessage.value = "Select a video clip to create a freeze frame."
            return
        }
        val offsetMs = (_playheadMs.value - clip.timelineStartMs).coerceIn(0L, clip.durationMs)
        val srcMs = clip.sourceStartMs + (offsetMs * clip.speedConfig.constantSpeed).toLong()

        viewModelScope.launch {
            _busyMessage.value = "Capturing lossless freeze frame..."
            val result = OfflineMediaProcessor.captureFreezeFrame(
                context = getApplication(),
                sourceUri = Uri.parse(clip.mediaUri),
                sourceTimeMs = srcMs,
                outputDir = container.fileStore.derivedMediaDir
            )
            _busyMessage.value = null
            result.onSuccess { pngFile ->
                val freezeId = UUID.randomUUID().toString()
                mutateProject { proj ->
                    val freezeClip = TimelineClip(
                        id = freezeId,
                        trackId = clip.trackId,
                        mediaUri = Uri.fromFile(pngFile).toString(),
                        mediaType = MediaType.IMAGE,
                        displayName = "Freeze Frame",
                        timelineStartMs = clip.timelineStartMs + clip.durationMs,
                        durationMs = 2500L,
                        isFreezeFrame = true
                    )
                    proj.copy(tracks = proj.tracks.map { t ->
                        if (t.id == clip.trackId) t.copy(clips = t.clips + freezeClip) else t
                    })
                }
                selectClip(freezeId)
                _toastMessage.value = "Freeze frame inserted"
            }.onFailure {
                _toastMessage.value = "Freeze frame failed: ${it.localizedMessage}"
            }
        }
    }

    fun extractOrDetachAudio(detachFromVideo: Boolean) {
        val clip = selectedClip() ?: return
        if (clip.mediaType != MediaType.VIDEO || clip.mediaUri == null) {
            _toastMessage.value = "Select a video clip to extract/detach audio."
            return
        }
        viewModelScope.launch {
            _busyMessage.value = if (detachFromVideo) "Detaching audio track..." else "Extracting audio..."
            val res = OfflineMediaProcessor.extractAudioToM4a(
                context = getApplication(),
                sourceUri = Uri.parse(clip.mediaUri),
                startMs = clip.sourceStartMs,
                endMs = clip.sourceEndMs,
                outputDir = container.fileStore.derivedMediaDir
            )
            _busyMessage.value = null
            res.onSuccess { m4aFile ->
                val uri = Uri.fromFile(m4aFile)
                val peaks = MediaInspector.extractAudioWaveform(getApplication(), uri, 64)
                val newClipId = UUID.randomUUID().toString()
                mutateProject { proj ->
                    val audioTrack = proj.tracks.firstOrNull { it.type == TrackType.AUDIO && !it.isLocked }
                        ?: TimelineTrack(id = UUID.randomUUID().toString(), name = "Audio ${proj.tracks.size + 1}", type = TrackType.AUDIO, order = proj.tracks.size)
                    val audioClip = TimelineClip(
                        id = newClipId,
                        trackId = audioTrack.id,
                        mediaUri = uri.toString(),
                        mediaType = MediaType.AUDIO,
                        displayName = "${clip.displayName} (Audio)",
                        timelineStartMs = clip.timelineStartMs,
                        durationMs = clip.durationMs,
                        sourceStartMs = 0L,
                        sourceEndMs = clip.durationMs,
                        sourceDurationMs = clip.durationMs,
                        waveformPeaks = peaks
                    )
                    val tracksWithMute = if (detachFromVideo) {
                        proj.tracks.map { t ->
                            if (t.id == clip.trackId) t.copy(clips = t.clips.map { if (it.id == clip.id) it.copy(audioDetached = true) else it })
                            else t
                        }
                    } else proj.tracks

                    val finalTracks = if (tracksWithMute.any { it.id == audioTrack.id }) {
                        tracksWithMute.map { if (it.id == audioTrack.id) it.copy(clips = it.clips + audioClip) else it }
                    } else {
                        tracksWithMute + audioTrack.copy(clips = listOf(audioClip))
                    }
                    proj.copy(tracks = finalTracks)
                }
                _toastMessage.value = if (detachFromVideo) "Audio detached to Audio track" else "Audio extracted to Audio track"
            }.onFailure {
                _toastMessage.value = "Audio extraction failed: ${it.localizedMessage}"
            }
        }
    }

    fun toggleReverseSelectedClip() {
        val clip = selectedClip() ?: return
        if (clip.mediaType != MediaType.VIDEO || clip.mediaUri == null) {
            _toastMessage.value = "Select a video clip to reverse."
            return
        }
        if (clip.isReversed) {
            mutateClip(clip.id) { it.copy(isReversed = false) }
            _toastMessage.value = "Restored forward playback"
            return
        }
        if (!clip.reversedCachePath.isNullOrBlank() && File(clip.reversedCachePath).exists()) {
            mutateClip(clip.id) { it.copy(isReversed = true) }
            _toastMessage.value = "Clip reversed"
            return
        }
        viewModelScope.launch {
            _busyMessage.value = "Reversing frames locally (0%)..."
            val res = OfflineMediaProcessor.reverseVideoClip(
                context = getApplication(),
                sourceUri = Uri.parse(clip.mediaUri),
                sourceStartMs = clip.sourceStartMs,
                sourceEndMs = clip.sourceEndMs,
                fps = _project.value?.fps ?: 30,
                outputDir = container.fileStore.derivedMediaDir
            ) { p ->
                _busyMessage.value = "Reversing frames locally (${(p * 100).toInt()}%)..."
            }
            _busyMessage.value = null
            res.onSuccess { revFile ->
                mutateClip(clip.id) { it.copy(isReversed = true, reversedCachePath = revFile.absolutePath) }
                _toastMessage.value = "Clip reversed locally"
            }.onFailure {
                _toastMessage.value = it.localizedMessage ?: "Reverse failed"
            }
        }
    }

    // Transform, Keyframes, Speed, Animation, Color, Effects, Style, Text, Sticker, Audio
    fun updateTransform(clipId: String, transform: TransformConfig) {
        mutateClip(clipId) { it.copy(transform = transform) }
    }

    fun resetTransform(clipId: String) {
        mutateClip(clipId) { it.copy(transform = TransformConfig()) }
    }

    fun rotateClip90(clipId: String) {
        mutateClip(clipId) { it.copy(transform = it.transform.copy(rotation = (it.transform.rotation + 90f) % 360f)) }
    }

    fun flipClipHorizontal(clipId: String) {
        mutateClip(clipId) { it.copy(transform = it.transform.copy(flipHorizontal = !it.transform.flipHorizontal)) }
    }

    fun flipClipVertical(clipId: String) {
        mutateClip(clipId) { it.copy(transform = it.transform.copy(flipVertical = !it.transform.flipVertical)) }
    }

    fun setFitMode(clipId: String, fitMode: FitMode) {
        mutateClip(clipId) { it.copy(transform = it.transform.copy(fitMode = fitMode)) }
    }

    fun addOrUpdateKeyframeAtPlayhead(clipId: String, property: KeyframeProperty, value: Float) {
        val clip = _project.value?.tracks?.flatMap { it.clips }?.firstOrNull { it.id == clipId } ?: return
        val localMs = (_playheadMs.value - clip.timelineStartMs).coerceIn(0L, clip.durationMs)
        mutateClip(clipId) { c ->
            val filtered = c.keyframes.filterNot { it.property == property && abs(it.timeOffsetMs - localMs) < 40L }
            val kf = Keyframe(timeOffsetMs = localMs, property = property, value = value)
            c.copy(keyframes = (filtered + kf).sortedBy { it.timeOffsetMs })
        }
    }

    fun removeKeyframe(clipId: String, keyframeId: String) {
        mutateClip(clipId) { c -> c.copy(keyframes = c.keyframes.filterNot { it.id == keyframeId }) }
    }

    fun jumpToAdjacentKeyframe(clipId: String, forward: Boolean) {
        val clip = _project.value?.tracks?.flatMap { it.clips }?.firstOrNull { it.id == clipId } ?: return
        val localMs = _playheadMs.value - clip.timelineStartMs
        val target = if (forward) {
            clip.keyframes.sortedBy { it.timeOffsetMs }.firstOrNull { it.timeOffsetMs > localMs + 20L }
        } else {
            clip.keyframes.sortedByDescending { it.timeOffsetMs }.firstOrNull { it.timeOffsetMs < localMs - 20L }
        }
        if (target != null) seekTo(clip.timelineStartMs + target.timeOffsetMs)
    }

    fun copyKeyframe(kf: Keyframe) {
        _copiedKeyframe.value = kf
        _toastMessage.value = "Copied keyframe (${kf.property.name})"
    }

    fun pasteKeyframeAtPlayhead(clipId: String) {
        val copied = _copiedKeyframe.value ?: return
        val clip = _project.value?.tracks?.flatMap { it.clips }?.firstOrNull { it.id == clipId } ?: return
        val localMs = (_playheadMs.value - clip.timelineStartMs).coerceIn(0L, clip.durationMs)
        mutateClip(clipId) { c ->
            val filtered = c.keyframes.filterNot { it.property == copied.property && abs(it.timeOffsetMs - localMs) < 40L }
            c.copy(keyframes = (filtered + copied.copy(id = UUID.randomUUID().toString(), timeOffsetMs = localMs)).sortedBy { it.timeOffsetMs })
        }
    }

    fun updateKeyframeEasing(
        clipId: String,
        keyframeId: String,
        easing: EasingType,
        p1x: Float,
        p1y: Float,
        p2x: Float,
        p2y: Float
    ) {
        mutateClip(clipId) { c ->
            c.copy(keyframes = c.keyframes.map { kf ->
                if (kf.id == keyframeId) {
                    kf.copy(easing = easing, bezierP1x = p1x, bezierP1y = p1y, bezierP2x = p2x, bezierP2y = p2y)
                } else kf
            })
        }
    }

    fun updateClipAnimation(clipId: String, config: ClipAnimationConfig) {
        mutateClip(clipId) { it.copy(animation = config) }
    }

    fun updateSpeedConfig(clipId: String, config: SpeedCurveConfig) {
        mutateClip(clipId) { c ->
            val avgSpeed = if (config.points.isNotEmpty()) {
                config.points.map { it.speedMultiplier }.average().toFloat().coerceIn(0.1f, 16f)
            } else config.constantSpeed.coerceIn(0.1f, 16f)
            val newDur = if (c.mediaType == MediaType.VIDEO || c.mediaType == MediaType.AUDIO) {
                val srcSpan = (c.sourceEndMs - c.sourceStartMs).coerceAtLeast(200L)
                (srcSpan / avgSpeed).toLong().coerceAtLeast(200L)
            } else c.durationMs
            c.copy(speedConfig = config, durationMs = newDur)
        }
    }

    fun updateColorAdjustments(clipId: String, adjustments: ColorAdjustments) {
        mutateClip(clipId) { it.copy(colorAdjustments = adjustments) }
    }

    fun saveCustomFilterPreset(name: String, adjustments: ColorAdjustments) {
        viewModelScope.launch {
            repo.saveCustomFilterPreset(name, adjustments)
            _toastMessage.value = "Saved filter preset \"$name\""
        }
    }

    fun applyFilterPresetEntity(clipId: String, preset: FilterPresetEntity) {
        val decoded = repo.decodeFilterPreset(preset) ?: return
        updateColorAdjustments(clipId, decoded)
    }

    fun importCubeLutUri(clipId: String, uri: Uri) {
        viewModelScope.launch {
            val res = OfflineMediaProcessor.parseCubeLut(getApplication(), uri, container.fileStore.lutsDir)
            res.onSuccess { (savedFile, lut) ->
                val clip = selectedClip() ?: return@onSuccess
                updateColorAdjustments(clipId, clip.colorAdjustments.copy(customLutPath = savedFile.absolutePath))
                _toastMessage.value = "Applied 3D LUT: ${lut.title} (${lut.size}³)"
            }.onFailure {
                _toastMessage.value = it.localizedMessage ?: "Invalid .cube LUT"
            }
        }
    }

    fun addVideoEffect(clipId: String, type: VideoEffectType) {
        mutateClip(clipId) { c ->
            c.copy(videoEffects = c.videoEffects + VideoEffectItem(type = type))
        }
    }

    fun updateVideoEffects(clipId: String, effects: List<VideoEffectItem>) {
        mutateClip(clipId) { it.copy(videoEffects = effects) }
    }

    fun updateLayerStyle(clipId: String, style: LayerStyleConfig) {
        mutateClip(clipId) { it.copy(layerStyle = style) }
    }

    fun addNullObjectLayer() {
        val clipId = UUID.randomUUID().toString()
        val startAt = _playheadMs.value
        mutateProject { proj ->
            val track = proj.tracks.firstOrNull { it.type == TrackType.NULL_OBJECT && !it.isLocked }
                ?: TimelineTrack(
                    id = UUID.randomUUID().toString(),
                    name = "Null Rig ${proj.tracks.count { it.type == TrackType.NULL_OBJECT } + 1}",
                    type = TrackType.NULL_OBJECT,
                    order = proj.tracks.size
                )
            val nullClip = TimelineClip(
                id = clipId,
                trackId = track.id,
                mediaType = MediaType.NULL_OBJECT,
                displayName = "Null Object ${track.clips.size + 1}",
                timelineStartMs = startAt,
                durationMs = (proj.computedDurationMs() - startAt).coerceIn(3000L, 12000L),
                transform = TransformConfig(is3dLayer = true)
            )
            val tracks = if (proj.tracks.any { it.id == track.id }) {
                proj.tracks.map { if (it.id == track.id) it.copy(clips = it.clips + nullClip) else it }
            } else {
                proj.tracks + track.copy(clips = listOf(nullClip))
            }
            proj.copy(tracks = tracks)
        }
        selectClip(clipId)
        _activeInspector.value = EditorInspectorTab.TRANSFORM
        _toastMessage.value = "Added Null Object controller"
    }

    fun addCamera3DLayer() {
        val clipId = UUID.randomUUID().toString()
        val startAt = _playheadMs.value
        mutateProject { proj ->
            val track = proj.tracks.firstOrNull { it.type == TrackType.CAMERA_3D && !it.isLocked }
                ?: TimelineTrack(
                    id = UUID.randomUUID().toString(),
                    name = "3D Camera",
                    type = TrackType.CAMERA_3D,
                    order = proj.tracks.size
                )
            val camClip = TimelineClip(
                id = clipId,
                trackId = track.id,
                mediaType = MediaType.CAMERA_3D,
                displayName = "3D Camera Rig",
                timelineStartMs = startAt,
                durationMs = (proj.computedDurationMs() - startAt).coerceIn(4000L, 15000L),
                camera3d = Camera3DConfig()
            )
            val tracks = if (proj.tracks.any { it.id == track.id }) {
                proj.tracks.map { if (it.id == track.id) it.copy(clips = it.clips + camClip) else it }
            } else {
                proj.tracks + track.copy(clips = listOf(camClip))
            }
            proj.copy(tracks = tracks)
        }
        selectClip(clipId)
        _activeInspector.value = EditorInspectorTab.CAMERA_3D_RIG
        _toastMessage.value = "Added 3D Camera motion rig"
    }

    fun setClipParent(clipId: String, parentClipId: String?) {
        if (clipId == parentClipId) return
        mutateClip(clipId) { it.copy(transform = it.transform.copy(parentClipId = parentClipId)) }
    }

    fun updateCamera3D(clipId: String, config: Camera3DConfig) {
        mutateClip(clipId) { it.copy(camera3d = config) }
    }

    fun updateChromaKey(clipId: String, config: ChromaKeyConfig) {
        mutateClip(clipId) { it.copy(chromaKey = config) }
    }

    fun updateAlphaKey(clipId: String, config: AlphaKeyConfig) {
        mutateClip(clipId) { it.copy(alphaKey = config) }
    }

    fun updateBgRemoval(clipId: String, config: BgRemovalConfig) {
        mutateClip(clipId) { it.copy(bgRemoval = config) }
    }

    fun clearBgRemovalCutout(clipId: String) {
        mutateClip(clipId) {
            it.copy(
                maskedMediaCachePath = null,
                bgRemoval = it.bgRemoval.copy(enabled = false)
            )
        }
        _toastMessage.value = "Cleared extracted matte cutout"
    }

    fun runOfflineBackgroundRemoval(clipId: String) {
        val clip = _project.value?.tracks?.flatMap { it.clips }?.firstOrNull { it.id == clipId } ?: return
        val uriStr = clip.mediaUri ?: run {
            _toastMessage.value = "Select a photo or video clip first."
            return
        }
        val localMs = (_playheadMs.value - clip.timelineStartMs).coerceIn(0L, clip.durationMs)
        val srcMs = clip.sourceStartMs + (localMs * clip.speedConfig.constantSpeed).toLong()

        viewModelScope.launch {
            _busyMessage.value = "Running offline segmentation & edge matting (10%)..."
            val res = OfflineVisionAndTracking.removeImageOrFrameBackground(
                context = getApplication(),
                sourceUri = Uri.parse(uriStr),
                isVideo = clip.mediaType == MediaType.VIDEO,
                sampleTimeMs = srcMs,
                config = clip.bgRemoval,
                outputDir = container.fileStore.derivedMediaDir
            ) { progress ->
                _busyMessage.value = "Running offline segmentation & edge matting (${(progress * 100).toInt()}%)..."
            }
            _busyMessage.value = null
            res.onSuccess { cutoutPng ->
                mutateClip(clipId) {
                    it.copy(
                        maskedMediaCachePath = cutoutPng.absolutePath,
                        bgRemoval = it.bgRemoval.copy(enabled = true)
                    )
                }
                _toastMessage.value = "Offline background cutout extracted"
            }.onFailure { err ->
                _toastMessage.value = "Background removal failed: ${err.localizedMessage}"
            }
        }
    }

    fun setCameraTrackTarget(nx: Float, ny: Float) {
        _cameraTrackTarget.value = nx.coerceIn(-0.9f, 0.9f) to ny.coerceIn(-0.9f, 0.9f)
    }

    fun runCameraTracking(clipId: String, applyToNewNull: Boolean) {
        val clip = _project.value?.tracks?.flatMap { it.clips }?.firstOrNull { it.id == clipId } ?: return
        val uriStr = clip.mediaUri
        if (uriStr == null || clip.mediaType != MediaType.VIDEO) {
            _toastMessage.value = "Camera tracking requires a video clip on the timeline."
            return
        }
        val (targetX, targetY) = _cameraTrackTarget.value
        viewModelScope.launch {
            _busyMessage.value = "Tracking offline camera/object motion (0%)..."
            _cameraTrackResultLabel.value = "Analyzing motion frames locally..."
            val res = OfflineVisionAndTracking.trackClipMotion(
                context = getApplication(),
                sourceUri = Uri.parse(uriStr),
                sourceStartMs = clip.sourceStartMs,
                sourceEndMs = clip.sourceEndMs,
                clipDurationMs = clip.durationMs,
                initNormX = targetX,
                initNormY = targetY,
                sampleCount = 16
            ) { progress ->
                _busyMessage.value = "Tracking offline camera/object motion (${(progress * 100).toInt()}%)..."
            }
            _busyMessage.value = null
            res.onSuccess { trackResult ->
                if (applyToNewNull) {
                    val nullId = UUID.randomUUID().toString()
                    mutateProject { proj ->
                        val track = proj.tracks.firstOrNull { it.type == TrackType.NULL_OBJECT && !it.isLocked }
                            ?: TimelineTrack(
                                id = UUID.randomUUID().toString(),
                                name = "Tracked Nulls",
                                type = TrackType.NULL_OBJECT,
                                order = proj.tracks.size
                            )
                        val nullClip = TimelineClip(
                            id = nullId,
                            trackId = track.id,
                            mediaType = MediaType.NULL_OBJECT,
                            displayName = "Tracked Null (${clip.displayName.take(10)})",
                            timelineStartMs = clip.timelineStartMs,
                            durationMs = clip.durationMs,
                            keyframes = trackResult.keyframes,
                            transform = TransformConfig(is3dLayer = true)
                        )
                        val updatedTracks = proj.tracks.map { t ->
                            t.copy(clips = t.clips.map { c ->
                                if (c.id == clip.id) c.copy(transform = c.transform.copy(parentClipId = nullId)) else c
                            })
                        }
                        val finalTracks = if (updatedTracks.any { it.id == track.id }) {
                            updatedTracks.map { if (it.id == track.id) it.copy(clips = it.clips + nullClip) else it }
                        } else {
                            updatedTracks + track.copy(clips = listOf(nullClip))
                        }
                        proj.copy(tracks = finalTracks)
                    }
                    _toastMessage.value = "Baked ${trackResult.sampleCount} motion keyframes into Null Object"
                } else {
                    mutateClip(clipId) { c ->
                        val kept = c.keyframes.filterNot {
                            it.property == KeyframeProperty.POS_X || it.property == KeyframeProperty.POS_Y
                        }
                        c.copy(keyframes = (kept + trackResult.keyframes).sortedBy { it.timeOffsetMs })
                    }
                    _toastMessage.value = "Applied ${trackResult.sampleCount} motion keyframes to clip"
                }
                val confPct = (trackResult.averageConfidence * 100).toInt().coerceIn(0, 100)
                _cameraTrackResultLabel.value = "Tracked ${trackResult.sampleCount} frames • Confidence: $confPct%"
            }.onFailure { err ->
                _cameraTrackResultLabel.value = "Tracking failed: ${err.localizedMessage}"
                _toastMessage.value = "Camera track error: ${err.localizedMessage}"
            }
        }
    }

    fun updateTextData(clipId: String, textData: TextOverlayData) {
        mutateClip(clipId) { it.copy(textData = textData, displayName = textData.text.take(20).ifEmpty { "Text" }) }
    }

    fun updateStickerData(clipId: String, stickerData: StickerOverlayData) {
        mutateClip(clipId) { it.copy(stickerData = stickerData) }
    }

    fun importCustomFontUri(uri: Uri, scriptTag: String = "Custom") {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            var fileName = "custom_font.ttf"
            app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) fileName = it.getString(0) ?: fileName
            }
            val dest = File(container.fileStore.fontsDir, "${UUID.randomUUID().toString().take(6)}_$fileName")
            val copied = runCatching {
                app.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(dest).use { out -> input.copyTo(out) }
                }
                dest.exists() && dest.length() > 0
            }.getOrDefault(false)

            if (copied) {
                repo.addCustomFont(fileName.substringBeforeLast('.'), dest.absolutePath, scriptTag)
                withContext(Dispatchers.Main) { _toastMessage.value = "Imported font: $fileName" }
            } else {
                withContext(Dispatchers.Main) { _toastMessage.value = "Failed to import font file." }
            }
        }
    }

    fun importCustomStickerUri(uri: Uri, collectionName: String = "My Stickers") {
        viewModelScope.launch {
            MediaInspector.takePersistablePermissionIfPossible(getApplication(), uri)
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "Sticker"
            repo.addCustomSticker(name, collectionName, uri.toString())
            addStickerLayer(
                StickerOverlayData(category = StickerCategory.CUSTOM_IMAGE, customImageUri = uri.toString()),
                name
            )
            _toastMessage.value = "Added sticker to $collectionName"
        }
    }

    fun addOrUpdateTransitionBetween(fromClipId: String, toClipId: String, type: TransitionType) {
        mutateProject { proj ->
            val existing = proj.transitions.firstOrNull { it.fromClipId == fromClipId && it.toClipId == toClipId }
            val updated = if (existing != null) {
                if (type == TransitionType.NONE) proj.transitions.filterNot { it.id == existing.id }
                else proj.transitions.map { if (it.id == existing.id) it.copy(type = type) else it }
            } else if (type != TransitionType.NONE) {
                val t = ClipTransition(fromClipId = fromClipId, toClipId = toClipId, type = type)
                _selectedTransitionId.value = t.id
                proj.transitions + t
            } else proj.transitions
            proj.copy(transitions = updated)
        }
    }

    fun updateTransition(transition: ClipTransition) {
        mutateProject { proj ->
            proj.copy(transitions = proj.transitions.map { if (it.id == transition.id) transition else it })
        }
    }

    fun updateClipAudio(clipId: String, audioSettings: ClipAudioSettings) {
        mutateClip(clipId) { it.copy(audioSettings = audioSettings) }
    }

    fun applyEqPreset(clipId: String, preset: EqPreset) {
        val clip = selectedClip() ?: return
        val eq = when (preset) {
            EqPreset.FLAT -> EqSettings(preset, 0f, 0f, 0f, 20000f, 20f)
            EqPreset.BASS_BOOST -> EqSettings(preset, 7.5f, 1.0f, -1.0f, 20000f, 25f)
            EqPreset.VOCAL_CLARITY -> EqSettings(preset, -3f, 5.5f, 3.0f, 16000f, 110f)
            EqPreset.TREBLE_BOOST -> EqSettings(preset, -1f, 1.5f, 7.0f, 20000f, 40f)
            EqPreset.CINEMATIC_WARM -> EqSettings(preset, 4.5f, 2.0f, -2.5f, 14000f, 35f)
            EqPreset.PODCAST -> EqSettings(preset, -2f, 4.0f, 2.5f, 14000f, 95f)
            EqPreset.CUSTOM -> clip.audioSettings.eqSettings.copy(preset = EqPreset.CUSTOM)
        }
        updateClipAudio(clipId, clip.audioSettings.copy(eqSettings = eq))
    }

    fun updateProjectCanvas(aspectRatio: AspectRatioOption, resolution: ResolutionPreset, fps: Int) {
        val (w, h) = ProjectRepository.computeDimensions(aspectRatio, resolution)
        mutateProject { proj ->
            proj.copy(
                width = w,
                height = h,
                fps = fps,
                aspectRatio = aspectRatio,
                exportConfig = proj.exportConfig.copy(aspectRatio = aspectRatio, resolution = resolution, fps = fps)
            )
        }
    }

    // Voice Recorder
    fun startVoiceRecording() {
        val res = voiceRecorder.startRecording()
        res.onSuccess {
            _isRecordingActive.value = true
            _isRecordingPaused.value = false
            _recordingElapsedMs.value = 0L
            recorderMeterJob?.cancel()
            recorderMeterJob = viewModelScope.launch {
                var lastTick = System.currentTimeMillis()
                while (_isRecordingActive.value) {
                    delay(60L)
                    val now = System.currentTimeMillis()
                    if (!_isRecordingPaused.value) {
                        _recordingElapsedMs.value += (now - lastTick)
                    }
                    lastTick = now
                    _recordingAmplitude.value = voiceRecorder.getAmplitudeNormalized()
                }
            }
        }.onFailure {
            _toastMessage.value = "Recording failed: ${it.localizedMessage}"
        }
    }

    fun togglePauseVoiceRecording() {
        if (!_isRecordingActive.value) return
        if (_isRecordingPaused.value) {
            voiceRecorder.resumeRecording()
            _isRecordingPaused.value = false
        } else {
            voiceRecorder.pauseRecording()
            _isRecordingPaused.value = true
        }
    }

    fun stopVoiceRecording(addToTimeline: Boolean = true) {
        recorderMeterJob?.cancel()
        val file = voiceRecorder.stopRecording()
        _isRecordingActive.value = false
        _isRecordingPaused.value = false
        _recordingAmplitude.value = 0f
        refreshRecordings()
        if (file != null && addToTimeline) {
            addRecordingToTimeline(file)
        }
    }

    fun refreshRecordings() {
        viewModelScope.launch(Dispatchers.IO) {
            _recordings.value = voiceRecorder.listRecordings()
        }
    }

    fun addRecordingToTimeline(file: File) {
        viewModelScope.launch {
            val uri = Uri.fromFile(file)
            val dur = when (val res = MediaInspector.inspectUri(getApplication(), uri)) {
                is MediaInspectResult.Success -> res.durationMs
                else -> _recordingElapsedMs.value.coerceAtLeast(500L)
            }
            addMediaUriToTimeline(uri, MediaType.AUDIO, file.nameWithoutExtension, dur)
            _toastMessage.value = "Voice recording added to Audio track"
        }
    }

    fun renameRecording(file: File, newName: String) {
        voiceRecorder.renameRecording(file, newName)
        refreshRecordings()
    }

    fun deleteRecording(file: File) {
        voiceRecorder.deleteRecording(file)
        refreshRecordings()
    }

    // Export Engine
    fun updateExportConfig(config: ExportConfig) {
        mutateProject(recordUndo = false) { it.copy(exportConfig = config) }
    }

    fun startExport(config: ExportConfig) {
        val proj = _project.value ?: return
        pausePlayback()
        viewModelScope.launch {
            repo.saveProject(proj.copy(exportConfig = config))
            exportEngine.startExport(proj, config) { state ->
                _exportState.value = state
            }
        }
    }

    fun cancelExport() {
        exportEngine.cancelExport()
        _exportState.value = ExportState.Cancelled
    }

    fun resetExportState() {
        _exportState.value = ExportState.Idle
    }

    fun openExportedFile(file: File, galleryUri: Uri?) {
        val app = getApplication<Application>()
        val targetUri = galleryUri ?: FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        val mime = when (file.extension.lowercase()) {
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            "gif" -> "image/gif"
            else -> "application/zip"
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(targetUri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { app.startActivity(intent) }.onFailure {
            _toastMessage.value = "No viewer app found for .${file.extension}"
        }
    }

    fun shareExportedFile(file: File, galleryUri: Uri?) {
        val app = getApplication<Application>()
        val targetUri = galleryUri ?: FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        val mime = when (file.extension.lowercase()) {
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            "gif" -> "image/gif"
            else -> "application/zip"
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, targetUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        app.startActivity(Intent.createChooser(intent, "Share Exported Video").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun onCleared() {
        super.onCleared()
        autoSaveJob?.cancel()
        playbackJob?.cancel()
        recorderMeterJob?.cancel()
        voiceRecorder.release()
    }
}
