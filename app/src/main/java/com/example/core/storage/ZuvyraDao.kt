package com.example.core.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getProjectById(id: String): ProjectEntity?

    @Query("SELECT * FROM projects WHERE hasRecoverySnapshot = 1 ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestRecoveryProject(): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProject(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProjectById(id: String)

    @Query("UPDATE projects SET hasRecoverySnapshot = :hasRecovery WHERE id = :id")
    suspend fun setRecoveryFlag(id: String, hasRecovery: Boolean)
}

@Dao
interface MediaAssetDao {
    @Query("SELECT * FROM media_assets ORDER BY importedAt DESC")
    fun observeAllMedia(): Flow<List<MediaAssetEntity>>

    @Query("SELECT * FROM media_assets WHERE uriString = :uriString LIMIT 1")
    suspend fun findByUri(uriString: String): MediaAssetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMedia(asset: MediaAssetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAllMedia(assets: List<MediaAssetEntity>)

    @Query("DELETE FROM media_assets WHERE id = :id")
    suspend fun deleteMediaById(id: String)
}

@Dao
interface ExportHistoryDao {
    @Query("SELECT * FROM export_history ORDER BY exportedAt DESC")
    fun observeExportHistory(): Flow<List<ExportHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExportRecord(record: ExportHistoryEntity)

    @Query("DELETE FROM export_history WHERE id = :id")
    suspend fun deleteRecord(id: String)
}

@Dao
interface FilterPresetDao {
    @Query("SELECT * FROM filter_presets ORDER BY createdAt DESC")
    fun observeFilterPresets(): Flow<List<FilterPresetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPreset(preset: FilterPresetEntity)

    @Query("DELETE FROM filter_presets WHERE id = :id")
    suspend fun deletePreset(id: String)
}

@Dao
interface StickerAssetDao {
    @Query("SELECT * FROM sticker_assets ORDER BY createdAt DESC")
    fun observeCustomStickers(): Flow<List<StickerAssetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSticker(sticker: StickerAssetEntity)

    @Query("DELETE FROM sticker_assets WHERE id = :id")
    suspend fun deleteSticker(id: String)
}

@Dao
interface CustomFontDao {
    @Query("SELECT * FROM custom_fonts ORDER BY importedAt DESC")
    fun observeCustomFonts(): Flow<List<CustomFontEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFont(font: CustomFontEntity)

    @Query("DELETE FROM custom_fonts WHERE id = :id")
    suspend fun deleteFont(id: String)
}
