package com.example

import android.app.Application
import com.example.core.media.DeviceCapabilities
import com.example.core.media.DeviceCapabilityProbe
import com.example.core.media.ThumbnailEngine
import com.example.core.media.VoiceRecorderEngine
import com.example.core.storage.ProjectFileStore
import com.example.core.storage.ProjectRepository
import com.example.core.storage.SettingsRepository
import com.example.core.storage.ZuvyraDatabase
import com.example.engine.ZuvyraExportEngine

class AppContainer(application: Application) {
    val database: ZuvyraDatabase = ZuvyraDatabase.getInstance(application)
    val fileStore: ProjectFileStore = ProjectFileStore(application)
    val settingsRepository: SettingsRepository = SettingsRepository(application)
    val projectRepository: ProjectRepository = ProjectRepository(application, database, fileStore)
    val thumbnailEngine: ThumbnailEngine = ThumbnailEngine(application, fileStore.thumbnailsDir)
    val voiceRecorder: VoiceRecorderEngine = VoiceRecorderEngine(application, fileStore.recordingsDir)
    val exportEngine: ZuvyraExportEngine = ZuvyraExportEngine(application, projectRepository)
    val deviceCapabilities: DeviceCapabilities by lazy { DeviceCapabilityProbe.probe(application) }
}

class ZuvyraApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
