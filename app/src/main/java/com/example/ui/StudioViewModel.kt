package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ZuvyraApplication
import com.example.core.media.DeviceCapabilities
import com.example.core.media.MediaInspectResult
import com.example.core.media.MediaInspector
import com.example.core.model.AspectRatioOption
import com.example.core.model.MediaType
import com.example.core.model.ResolutionPreset
import com.example.core.model.ZuvyraProject
import com.example.core.storage.ExportHistoryEntity
import com.example.core.storage.InterfaceDensity
import com.example.core.storage.MediaAssetEntity
import com.example.core.storage.PreviewQuality
import com.example.core.storage.ProjectEntity
import com.example.core.storage.StorageBreakdown
import com.example.core.storage.ZuvyraSettings
import com.example.ui.theme.AccentColorOption
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class ImportProgressState(
    val isImporting: Boolean = false,
    val totalCount: Int = 0,
    val processedCount: Int = 0,
    val currentFileName: String = "",
    val importedCount: Int = 0,
    val duplicateCount: Int = 0,
    val errorMessages: List<String> = emptyList()
)

class StudioViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as ZuvyraApplication).container
    val repo = container.projectRepository
    val settingsRepo = container.settingsRepository
    val thumbnailEngine = container.thumbnailEngine
    val deviceCapabilities: DeviceCapabilities = container.deviceCapabilities

    val projects: StateFlow<List<ProjectEntity>> = repo.allProjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mediaAssets: StateFlow<List<MediaAssetEntity>> = repo.allMediaAssets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val exportHistory: StateFlow<List<ExportHistoryEntity>> = repo.exportHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<ZuvyraSettings> = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ZuvyraSettings())

    private val _recoveryCandidate = MutableStateFlow<ProjectEntity?>(null)
    val recoveryCandidate: StateFlow<ProjectEntity?> = _recoveryCandidate.asStateFlow()

    private val _importState = MutableStateFlow(ImportProgressState())
    val importState: StateFlow<ImportProgressState> = _importState.asStateFlow()

    private val _storageBreakdown = MutableStateFlow(StorageBreakdown(0, 0, 0, 0))
    val storageBreakdown: StateFlow<StorageBreakdown> = _storageBreakdown.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _selectedMediaIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedMediaIds: StateFlow<Set<String>> = _selectedMediaIds.asStateFlow()

    private var importJob: Job? = null

    init {
        checkCrashRecovery()
        refreshStorageBreakdown()
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun showStatus(msg: String) {
        _statusMessage.value = msg
    }

    private fun checkCrashRecovery() {
        viewModelScope.launch {
            _recoveryCandidate.value = repo.getLatestRecoveryProject()
        }
    }

    fun dismissRecovery(projectId: String) {
        viewModelScope.launch {
            repo.discardRecovery(projectId)
            _recoveryCandidate.value = null
        }
    }

    fun clearRecoveryBanner() {
        _recoveryCandidate.value = null
    }

    suspend fun createProject(
        name: String,
        aspectRatio: AspectRatioOption,
        resolution: ResolutionPreset,
        fps: Int
    ): ZuvyraProject {
        val created = repo.createNewProject(name, aspectRatio, resolution, fps)
        refreshStorageBreakdown()
        return created
    }

    fun renameProject(projectId: String, newName: String) {
        viewModelScope.launch {
            repo.renameProject(projectId, newName)
            _statusMessage.value = "Project renamed to \"$newName\""
        }
    }

    fun duplicateProject(projectId: String) {
        viewModelScope.launch {
            val copy = repo.duplicateProject(projectId)
            if (copy != null) {
                _statusMessage.value = "Duplicated as \"${copy.name}\""
                refreshStorageBreakdown()
            }
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            repo.deleteProject(projectId)
            _statusMessage.value = "Project deleted"
            refreshStorageBreakdown()
        }
    }

    fun shareProjectPackage(projectId: String) {
        viewModelScope.launch {
            val project = repo.loadProject(projectId) ?: return@launch
            val pkgFile = repo.exportLocalPackage(project)
            val app = getApplication<Application>()
            val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", pkgFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "${project.name}.zuvyra")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            app.startActivity(Intent.createChooser(intent, "Share ZUVYRA Project Package").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun importMediaUris(uris: List<Uri>, folderLabel: String = "Device Media") {
        if (uris.isEmpty()) return
        importJob?.cancel()
        importJob = viewModelScope.launch {
            _importState.value = ImportProgressState(
                isImporting = true,
                totalCount = uris.size,
                processedCount = 0
            )
            var imported = 0
            var duplicates = 0
            val errors = mutableListOf<String>()

            try {
                for ((idx, uri) in uris.withIndex()) {
                    val existing = repo.findMediaByUri(uri.toString())
                    if (existing != null) {
                        duplicates++
                        _importState.value = _importState.value.copy(
                            processedCount = idx + 1,
                            duplicateCount = duplicates,
                            currentFileName = existing.displayName
                        )
                        continue
                    }

                    when (val res = MediaInspector.inspectUri(getApplication(), uri, folderLabel)) {
                        is MediaInspectResult.Success -> {
                            val thumbPath = if (res.mediaType == MediaType.VIDEO || res.mediaType == MediaType.IMAGE) {
                                val bmp = thumbnailEngine.getThumbnail(res.uri, res.mediaType, 500L, 320, 180)
                                if (bmp != null) {
                                    val f = java.io.File(container.fileStore.thumbnailsDir, "asset_${UUID.randomUUID().toString().take(8)}.jpg")
                                    withContext(Dispatchers.IO) {
                                        runCatching {
                                            java.io.FileOutputStream(f).use { fos ->
                                                bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, fos)
                                            }
                                        }
                                    }
                                    f.absolutePath
                                } else null
                            } else null

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
                                thumbnailPath = thumbPath,
                                folderLabel = res.folderLabel,
                                importedAt = System.currentTimeMillis()
                            )
                            repo.registerMediaAsset(entity)
                            imported++
                            _importState.value = _importState.value.copy(
                                processedCount = idx + 1,
                                importedCount = imported,
                                currentFileName = res.displayName
                            )
                        }
                        is MediaInspectResult.UnsupportedOrError -> {
                            errors.add("${res.displayName}: ${res.reason}")
                            _importState.value = _importState.value.copy(
                                processedCount = idx + 1,
                                errorMessages = errors.toList(),
                                currentFileName = res.displayName
                            )
                        }
                    }
                }
            } catch (_: CancellationException) {
                _statusMessage.value = "Import cancelled ($imported imported before cancellation)."
            } finally {
                _importState.value = _importState.value.copy(isImporting = false)
                refreshStorageBreakdown()
            }
        }
    }

    fun importFromFolderTree(treeUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            MediaInspector.takePersistablePermissionIfPossible(app, treeUri)
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            val collected = mutableListOf<Uri>()
            runCatching {
                app.contentResolver.query(
                    childrenUri,
                    arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE),
                    null, null, null
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    while (cursor.moveToNext()) {
                        val childId = cursor.getString(idIdx)
                        val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx) ?: "" else ""
                        if (mime != DocumentsContract.Document.MIME_TYPE_DIR) {
                            collected.add(DocumentsContract.buildDocumentUriUsingTree(treeUri, childId))
                        }
                    }
                }
            }
            withContext(Dispatchers.Main) {
                if (collected.isEmpty()) {
                    _statusMessage.value = "No importable files found in the selected folder."
                } else {
                    val folderName = treeUri.lastPathSegment?.substringAfterLast(':') ?: "Folder"
                    importMediaUris(collected, folderLabel = folderName)
                }
            }
        }
    }

    fun cancelImport() {
        importJob?.cancel()
        _importState.value = _importState.value.copy(isImporting = false)
    }

    fun clearImportReport() {
        _importState.value = ImportProgressState()
    }

    fun toggleMediaSelection(id: String) {
        val current = _selectedMediaIds.value
        _selectedMediaIds.value = if (id in current) current - id else current + id
    }

    fun selectAllMedia(ids: List<String>) {
        _selectedMediaIds.value = ids.toSet()
    }

    fun deselectAllMedia() {
        _selectedMediaIds.value = emptySet()
    }

    fun deleteSelectedMedia() {
        val ids = _selectedMediaIds.value.toList()
        viewModelScope.launch {
            ids.forEach { repo.deleteMediaAsset(it) }
            _selectedMediaIds.value = emptySet()
            _statusMessage.value = "Removed ${ids.size} item(s) from ZUVYRA library (originals on device untouched)"
        }
    }

    fun refreshStorageBreakdown() {
        viewModelScope.launch {
            _storageBreakdown.value = container.fileStore.computeStorageBreakdown()
        }
    }

    fun clearTemporaryFiles() {
        viewModelScope.launch {
            val freed = container.fileStore.clearTemporaryAndCacheFiles()
            thumbnailEngine.clearMemoryCache()
            refreshStorageBreakdown()
            val mb = String.format(java.util.Locale.US, "%.1f MB", freed / (1024f * 1024f))
            _statusMessage.value = "Cleared $mb of temporary cache files"
        }
    }

    // Settings mutations
    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsRepo.updateThemeMode(mode) }
    fun setAccentColor(accent: AccentColorOption) = viewModelScope.launch { settingsRepo.updateAccentColor(accent) }
    fun setDensity(density: InterfaceDensity) = viewModelScope.launch { settingsRepo.updateDensity(density) }
    fun setDefaultResolution(res: ResolutionPreset) = viewModelScope.launch { settingsRepo.updateDefaultResolution(res) }
    fun setDefaultFps(fps: Int) = viewModelScope.launch { settingsRepo.updateDefaultFps(fps) }
    fun setDefaultAspectRatio(ratio: AspectRatioOption) = viewModelScope.launch { settingsRepo.updateDefaultAspectRatio(ratio) }
    fun setTimelineSnapping(enabled: Boolean) = viewModelScope.launch { settingsRepo.updateTimelineSnapping(enabled) }
    fun setPreviewQuality(quality: PreviewQuality) = viewModelScope.launch { settingsRepo.updatePreviewQuality(quality) }
    fun setAutoSaveInterval(seconds: Int) = viewModelScope.launch { settingsRepo.updateAutoSaveInterval(seconds) }
    fun setThumbnailCacheLimitMb(mb: Int) = viewModelScope.launch { settingsRepo.updateThumbnailCacheLimitMb(mb) }
    fun setHardwareAccel(enabled: Boolean) = viewModelScope.launch { settingsRepo.updateHardwareAccel(enabled) }
    fun setBackgroundExport(enabled: Boolean) = viewModelScope.launch { settingsRepo.updateBackgroundExport(enabled) }
}
