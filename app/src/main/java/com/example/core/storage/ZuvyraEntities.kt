package com.example.core.storage

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val jsonFilePath: String,
    val thumbnailPath: String?,
    val width: Int,
    val height: Int,
    val fps: Int,
    val durationMs: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val hasRecoverySnapshot: Boolean = false
)

@Entity(tableName = "media_assets")
data class MediaAssetEntity(
    @PrimaryKey val id: String,
    val uriString: String,
    val displayName: String,
    val mimeType: String,
    val mediaType: String, // VIDEO, IMAGE, AUDIO
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val thumbnailPath: String?,
    val folderLabel: String,
    val importedAt: Long
)

@Entity(tableName = "export_history")
data class ExportHistoryEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val projectName: String,
    val outputUriString: String,
    val outputFilePath: String?,
    val format: String,
    val codec: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val bitrateKbps: Int,
    val fileSizeBytes: Long,
    val durationMs: Long,
    val exportedAt: Long,
    val status: String // COMPLETED, CANCELLED, FAILED
)

@Entity(tableName = "filter_presets")
data class FilterPresetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorAdjustmentsJson: String,
    val createdAt: Long
)

@Entity(tableName = "sticker_assets")
data class StickerAssetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val collectionName: String,
    val uriString: String,
    val createdAt: Long
)

@Entity(tableName = "custom_fonts")
data class CustomFontEntity(
    @PrimaryKey val id: String,
    val name: String,
    val filePath: String,
    val scriptTag: String, // English, Hindi/Devanagari, Custom
    val importedAt: Long
)
