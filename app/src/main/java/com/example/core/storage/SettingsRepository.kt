package com.example.core.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.model.AspectRatioOption
import com.example.core.model.ResolutionPreset
import com.example.ui.theme.AccentColorOption
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "zuvyra_settings")

enum class PreviewQuality(val label: String, val scaleFactor: Float) {
    AUTO("Adaptive (Auto)", 1f),
    LOW("Draft (480p)", 0.5f),
    MEDIUM("Balanced (720p)", 0.75f),
    FULL("Full Quality", 1f)
}

enum class InterfaceDensity(val label: String) {
    COMFORTABLE("Comfortable"),
    COMPACT("Compact Studio")
}

data class ZuvyraSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val accentColor: AccentColorOption = AccentColorOption.VIOLET,
    val density: InterfaceDensity = InterfaceDensity.COMFORTABLE,
    val defaultResolution: ResolutionPreset = ResolutionPreset.P1080,
    val defaultFps: Int = 30,
    val defaultAspectRatio: AspectRatioOption = AspectRatioOption.RATIO_16_9,
    val timelineSnapping: Boolean = true,
    val previewQuality: PreviewQuality = PreviewQuality.AUTO,
    val autoSaveIntervalSec: Int = 15,
    val defaultExportFolderLabel: String = "Movies/ZUVYRA",
    val thumbnailCacheLimitMb: Int = 256,
    val preferHardwareAcceleration: Boolean = true,
    val enableBackgroundExport: Boolean = true
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val DENSITY = stringPreferencesKey("density")
        val DEFAULT_RES = stringPreferencesKey("default_res")
        val DEFAULT_FPS = intPreferencesKey("default_fps")
        val DEFAULT_ASPECT = stringPreferencesKey("default_aspect")
        val TIMELINE_SNAP = booleanPreferencesKey("timeline_snap")
        val PREVIEW_QUALITY = stringPreferencesKey("preview_quality")
        val AUTOSAVE_INTERVAL = intPreferencesKey("autosave_interval")
        val EXPORT_FOLDER = stringPreferencesKey("export_folder")
        val THUMB_CACHE_MB = intPreferencesKey("thumb_cache_mb")
        val HW_ACCEL = booleanPreferencesKey("hw_accel")
        val BG_EXPORT = booleanPreferencesKey("bg_export")
    }

    val settingsFlow: Flow<ZuvyraSettings> = context.dataStore.data.map { prefs ->
        ZuvyraSettings(
            themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DARK,
            accentColor = prefs[Keys.ACCENT_COLOR]?.let { runCatching { AccentColorOption.valueOf(it) }.getOrNull() } ?: AccentColorOption.VIOLET,
            density = prefs[Keys.DENSITY]?.let { runCatching { InterfaceDensity.valueOf(it) }.getOrNull() } ?: InterfaceDensity.COMFORTABLE,
            defaultResolution = prefs[Keys.DEFAULT_RES]?.let { runCatching { ResolutionPreset.valueOf(it) }.getOrNull() } ?: ResolutionPreset.P1080,
            defaultFps = prefs[Keys.DEFAULT_FPS] ?: 30,
            defaultAspectRatio = prefs[Keys.DEFAULT_ASPECT]?.let { runCatching { AspectRatioOption.valueOf(it) }.getOrNull() } ?: AspectRatioOption.RATIO_16_9,
            timelineSnapping = prefs[Keys.TIMELINE_SNAP] ?: true,
            previewQuality = prefs[Keys.PREVIEW_QUALITY]?.let { runCatching { PreviewQuality.valueOf(it) }.getOrNull() } ?: PreviewQuality.AUTO,
            autoSaveIntervalSec = prefs[Keys.AUTOSAVE_INTERVAL] ?: 15,
            defaultExportFolderLabel = prefs[Keys.EXPORT_FOLDER] ?: "Movies/ZUVYRA",
            thumbnailCacheLimitMb = prefs[Keys.THUMB_CACHE_MB] ?: 256,
            preferHardwareAcceleration = prefs[Keys.HW_ACCEL] ?: true,
            enableBackgroundExport = prefs[Keys.BG_EXPORT] ?: true
        )
    }

    suspend fun updateThemeMode(mode: ThemeMode) = edit { it[Keys.THEME_MODE] = mode.name }
    suspend fun updateAccentColor(accent: AccentColorOption) = edit { it[Keys.ACCENT_COLOR] = accent.name }
    suspend fun updateDensity(density: InterfaceDensity) = edit { it[Keys.DENSITY] = density.name }
    suspend fun updateDefaultResolution(res: ResolutionPreset) = edit { it[Keys.DEFAULT_RES] = res.name }
    suspend fun updateDefaultFps(fps: Int) = edit { it[Keys.DEFAULT_FPS] = fps }
    suspend fun updateDefaultAspectRatio(ratio: AspectRatioOption) = edit { it[Keys.DEFAULT_ASPECT] = ratio.name }
    suspend fun updateTimelineSnapping(enabled: Boolean) = edit { it[Keys.TIMELINE_SNAP] = enabled }
    suspend fun updatePreviewQuality(quality: PreviewQuality) = edit { it[Keys.PREVIEW_QUALITY] = quality.name }
    suspend fun updateAutoSaveInterval(seconds: Int) = edit { it[Keys.AUTOSAVE_INTERVAL] = seconds }
    suspend fun updateExportFolderLabel(label: String) = edit { it[Keys.EXPORT_FOLDER] = label }
    suspend fun updateThumbnailCacheLimitMb(mb: Int) = edit { it[Keys.THUMB_CACHE_MB] = mb }
    suspend fun updateHardwareAccel(enabled: Boolean) = edit { it[Keys.HW_ACCEL] = enabled }
    suspend fun updateBackgroundExport(enabled: Boolean) = edit { it[Keys.BG_EXPORT] = enabled }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
