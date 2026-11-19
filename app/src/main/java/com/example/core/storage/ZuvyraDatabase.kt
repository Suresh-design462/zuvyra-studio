package com.example.core.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProjectEntity::class,
        MediaAssetEntity::class,
        ExportHistoryEntity::class,
        FilterPresetEntity::class,
        StickerAssetEntity::class,
        CustomFontEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ZuvyraDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun mediaAssetDao(): MediaAssetDao
    abstract fun exportHistoryDao(): ExportHistoryDao
    abstract fun filterPresetDao(): FilterPresetDao
    abstract fun stickerAssetDao(): StickerAssetDao
    abstract fun customFontDao(): CustomFontDao

    companion object {
        @Volatile
        private var INSTANCE: ZuvyraDatabase? = null

        fun getInstance(context: Context): ZuvyraDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ZuvyraDatabase::class.java,
                    "zuvyra_studio.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
