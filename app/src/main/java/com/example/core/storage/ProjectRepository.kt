package com.example.core.storage

import android.content.Context
import com.example.core.model.AspectRatioOption
import com.example.core.model.ColorAdjustments
import com.example.core.model.ExportConfig
import com.example.core.model.ResolutionPreset
import com.example.core.model.ZuvyraProject
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import java.io.File
import java.util.UUID

class ProjectRepository(
    private val context: Context,
    private val db: ZuvyraDatabase,
    val fileStore: ProjectFileStore
) {
    val allProjects: Flow<List<ProjectEntity>> = db.projectDao().observeAllProjects()
    val allMediaAssets: Flow<List<MediaAssetEntity>> = db.mediaAssetDao().observeAllMedia()
    val exportHistory: Flow<List<ExportHistoryEntity>> = db.exportHistoryDao().observeExportHistory()
    val customFilterPresets: Flow<List<FilterPresetEntity>> = db.filterPresetDao().observeFilterPresets()
    val customStickers: Flow<List<StickerAssetEntity>> = db.stickerAssetDao().observeCustomStickers()
    val customFonts: Flow<List<CustomFontEntity>> = db.customFontDao().observeCustomFonts()

    suspend fun createNewProject(
        name: String,
        aspectRatio: AspectRatioOption,
        resolution: ResolutionPreset,
        fps: Int
    ): ZuvyraProject {
        val (w, h) = computeDimensions(aspectRatio, resolution)
        val project = ZuvyraProject(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "Untitled Project" },
            width = w,
            height = h,
            fps = fps,
            aspectRatio = aspectRatio,
            exportConfig = ExportConfig(
                resolution = resolution,
                aspectRatio = aspectRatio,
                fps = fps
            )
        )
        saveProject(project)
        return project
    }

    suspend fun saveProject(project: ZuvyraProject) {
        val updated = project.copy(updatedAt = System.currentTimeMillis())
        val file = fileStore.saveProject(updated)
        val entity = ProjectEntity(
            id = updated.id,
            name = updated.name,
            jsonFilePath = file.absolutePath,
            thumbnailPath = updated.thumbnailPath,
            width = updated.width,
            height = updated.height,
            fps = updated.fps,
            durationMs = updated.computedDurationMs(),
            createdAt = updated.createdAt,
            updatedAt = updated.updatedAt,
            hasRecoverySnapshot = false
        )
        db.projectDao().upsertProject(entity)
    }

    suspend fun saveAutoRecovery(project: ZuvyraProject) {
        val updated = project.copy(updatedAt = System.currentTimeMillis())
        fileStore.saveRecoverySnapshot(updated)
        val existing = db.projectDao().getProjectById(updated.id)
        val mainFile = fileStore.getProjectFile(updated.id)
        db.projectDao().upsertProject(
            ProjectEntity(
                id = updated.id,
                name = updated.name,
                jsonFilePath = existing?.jsonFilePath ?: mainFile.absolutePath,
                thumbnailPath = updated.thumbnailPath ?: existing?.thumbnailPath,
                width = updated.width,
                height = updated.height,
                fps = updated.fps,
                durationMs = updated.computedDurationMs(),
                createdAt = updated.createdAt,
                updatedAt = updated.updatedAt,
                hasRecoverySnapshot = true
            )
        )
    }

    suspend fun loadProject(projectId: String, preferRecovery: Boolean = false): ZuvyraProject? {
        return fileStore.loadProject(projectId, preferRecovery)
    }

    suspend fun getLatestRecoveryProject(): ProjectEntity? {
        val entity = db.projectDao().getLatestRecoveryProject() ?: return null
        return if (fileStore.hasRecoverySnapshot(entity.id)) entity else {
            db.projectDao().setRecoveryFlag(entity.id, false)
            null
        }
    }

    suspend fun discardRecovery(projectId: String) {
        fileStore.clearRecoverySnapshot(projectId)
        db.projectDao().setRecoveryFlag(projectId, false)
    }

    suspend fun renameProject(projectId: String, newName: String) {
        val loaded = loadProject(projectId) ?: return
        val renamed = loaded.copy(name = newName.trim().ifEmpty { loaded.name })
        saveProject(renamed)
    }

    suspend fun duplicateProject(projectId: String): ZuvyraProject? {
        val original = loadProject(projectId) ?: return null
        val now = System.currentTimeMillis()
        val copy = original.copy(
            id = UUID.randomUUID().toString(),
            name = "${original.name} (Copy)",
            createdAt = now,
            updatedAt = now
        )
        saveProject(copy)
        return copy
    }

    suspend fun deleteProject(projectId: String) {
        fileStore.deleteProjectFiles(projectId)
        db.projectDao().deleteProjectById(projectId)
    }

    suspend fun saveAsProject(project: ZuvyraProject, newName: String): ZuvyraProject {
        val now = System.currentTimeMillis()
        val newProj = project.copy(
            id = UUID.randomUUID().toString(),
            name = newName.trim().ifEmpty { "${project.name} Copy" },
            createdAt = now,
            updatedAt = now
        )
        saveProject(newProj)
        return newProj
    }

    suspend fun exportLocalPackage(project: ZuvyraProject): File {
        return fileStore.exportLocalPackage(project)
    }

    suspend fun registerMediaAsset(asset: MediaAssetEntity) {
        db.mediaAssetDao().upsertMedia(asset)
    }

    suspend fun findMediaByUri(uriString: String): MediaAssetEntity? {
        return db.mediaAssetDao().findByUri(uriString)
    }

    suspend fun deleteMediaAsset(id: String) {
        db.mediaAssetDao().deleteMediaById(id)
    }

    suspend fun recordExport(record: ExportHistoryEntity) {
        db.exportHistoryDao().insertExportRecord(record)
    }

    suspend fun saveCustomFilterPreset(name: String, adjustments: ColorAdjustments) {
        val encoded = fileStore.json.encodeToString(adjustments)
        db.filterPresetDao().upsertPreset(
            FilterPresetEntity(
                id = UUID.randomUUID().toString(),
                name = name.trim().ifEmpty { "Custom Preset" },
                colorAdjustmentsJson = encoded,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    fun decodeFilterPreset(entity: FilterPresetEntity): ColorAdjustments? {
        return runCatching {
            fileStore.json.decodeFromString<ColorAdjustments>(entity.colorAdjustmentsJson)
        }.getOrNull()
    }

    suspend fun deleteFilterPreset(id: String) = db.filterPresetDao().deletePreset(id)

    suspend fun addCustomSticker(name: String, collection: String, uriString: String) {
        db.stickerAssetDao().insertSticker(
            StickerAssetEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                collectionName = collection,
                uriString = uriString,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteCustomSticker(id: String) = db.stickerAssetDao().deleteSticker(id)

    suspend fun addCustomFont(name: String, filePath: String, scriptTag: String) {
        db.customFontDao().insertFont(
            CustomFontEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                filePath = filePath,
                scriptTag = scriptTag,
                importedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteCustomFont(id: String) = db.customFontDao().deleteFont(id)

    companion object {
        fun computeDimensions(ratio: AspectRatioOption, res: ResolutionPreset): Pair<Int, Int> {
            val shortSide = res.shortSidePx
            val rw = ratio.ratioWidth.toFloat()
            val rh = ratio.ratioHeight.toFloat()
            return if (rw >= rh) {
                val h = shortSide
                val w = ((shortSide * (rw / rh)).toInt() / 2) * 2
                w to h
            } else {
                val w = shortSide
                val h = ((shortSide * (rh / rw)).toInt() / 2) * 2
                w to h
            }
        }
    }
}
