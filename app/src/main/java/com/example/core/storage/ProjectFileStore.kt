package com.example.core.storage

import android.content.Context
import android.util.Log
import com.example.core.model.ZuvyraProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class StorageBreakdown(
    val projectsBytes: Long,
    val cacheBytes: Long,
    val exportsBytes: Long,
    val tempAndDerivedBytes: Long
) {
    val totalBytes: Long get() = projectsBytes + cacheBytes + exportsBytes + tempAndDerivedBytes
}

class ProjectFileStore(private val context: Context) {

    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    private val projectsDir: File by lazy {
        File(context.filesDir, "projects").apply { mkdirs() }
    }

    private val recoveryDir: File by lazy {
        File(context.filesDir, "recovery").apply { mkdirs() }
    }

    val exportsDir: File by lazy {
        File(context.filesDir, "exports").apply { mkdirs() }
    }

    val recordingsDir: File by lazy {
        File(context.filesDir, "recordings").apply { mkdirs() }
    }

    val derivedMediaDir: File by lazy {
        File(context.filesDir, "derived_media").apply { mkdirs() }
    }

    val fontsDir: File by lazy {
        File(context.filesDir, "fonts").apply { mkdirs() }
    }

    val lutsDir: File by lazy {
        File(context.filesDir, "luts").apply { mkdirs() }
    }

    val thumbnailsDir: File by lazy {
        File(context.cacheDir, "thumbnails").apply { mkdirs() }
    }

    fun getProjectFile(projectId: String): File = File(projectsDir, "$projectId.zuvyra.json")

    fun getRecoveryFile(projectId: String): File = File(recoveryDir, "$projectId.recovery.json")

    suspend fun saveProject(project: ZuvyraProject): File = withContext(Dispatchers.IO) {
        val target = getProjectFile(project.id)
        val tmp = File(projectsDir, "${project.id}.tmp")
        val encoded = json.encodeToString(project)
        tmp.writeText(encoded)
        if (!tmp.renameTo(target)) {
            target.writeText(encoded)
            tmp.delete()
        }
        // Clear recovery snapshot once explicitly saved
        getRecoveryFile(project.id).delete()
        target
    }

    suspend fun saveRecoverySnapshot(project: ZuvyraProject): File = withContext(Dispatchers.IO) {
        val target = getRecoveryFile(project.id)
        val tmp = File(recoveryDir, "${project.id}.recovery.tmp")
        val encoded = json.encodeToString(project)
        tmp.writeText(encoded)
        if (!tmp.renameTo(target)) {
            target.writeText(encoded)
            tmp.delete()
        }
        target
    }

    suspend fun loadProject(projectId: String, preferRecovery: Boolean = false): ZuvyraProject? =
        withContext(Dispatchers.IO) {
            val recoveryFile = getRecoveryFile(projectId)
            val mainFile = getProjectFile(projectId)
            val candidate = when {
                preferRecovery && recoveryFile.exists() -> recoveryFile
                mainFile.exists() -> mainFile
                recoveryFile.exists() -> recoveryFile
                else -> null
            } ?: return@withContext null

            return@withContext try {
                json.decodeFromString<ZuvyraProject>(candidate.readText())
            } catch (e: Exception) {
                Log.e("ProjectFileStore", "Failed to decode project $projectId", e)
                if (preferRecovery && mainFile.exists()) {
                    try {
                        json.decodeFromString<ZuvyraProject>(mainFile.readText())
                    } catch (_: Exception) {
                        null
                    }
                } else null
            }
        }

    suspend fun hasRecoverySnapshot(projectId: String): Boolean = withContext(Dispatchers.IO) {
        getRecoveryFile(projectId).exists()
    }

    suspend fun clearRecoverySnapshot(projectId: String) = withContext(Dispatchers.IO) {
        getRecoveryFile(projectId).delete()
    }

    suspend fun deleteProjectFiles(projectId: String) = withContext(Dispatchers.IO) {
        getProjectFile(projectId).delete()
        getRecoveryFile(projectId).delete()
    }

    suspend fun exportLocalPackage(project: ZuvyraProject): File = withContext(Dispatchers.IO) {
        val safeName = project.name.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "zuvyra_project" }
        val packageFile = File(context.cacheDir, "${safeName}_${project.id.take(6)}.zuvyra")
        ZipOutputStream(packageFile.outputStream().buffered()).use { zos ->
            val projectJson = json.encodeToString(project)
            zos.putNextEntry(ZipEntry("project.zuvyra.json"))
            zos.write(projectJson.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            val manifestNote = "ZUVYRA Local Project Package\nProject: ${project.name}\nDeveloper: SURESH DIDEL\nSchema: v${project.schemaVersion}\n"
            zos.putNextEntry(ZipEntry("MANIFEST.txt"))
            zos.write(manifestNote.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        packageFile
    }

    suspend fun computeStorageBreakdown(): StorageBreakdown = withContext(Dispatchers.IO) {
        StorageBreakdown(
            projectsBytes = dirSize(projectsDir) + dirSize(recoveryDir),
            cacheBytes = dirSize(context.cacheDir),
            exportsBytes = dirSize(exportsDir),
            tempAndDerivedBytes = dirSize(derivedMediaDir) + dirSize(recordingsDir) + dirSize(fontsDir) + dirSize(lutsDir)
        )
    }

    suspend fun clearTemporaryAndCacheFiles(): Long = withContext(Dispatchers.IO) {
        val before = dirSize(context.cacheDir)
        deleteChildren(context.cacheDir)
        thumbnailsDir.mkdirs()
        // Clean stale .tmp files in projects and exports
        projectsDir.listFiles()?.filter { it.name.endsWith(".tmp") }?.forEach { it.delete() }
        exportsDir.listFiles()?.filter { it.name.endsWith(".tmp") }?.forEach { it.delete() }
        before
    }

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        return dir.listFiles()?.sumOf { dirSize(it) } ?: 0L
    }

    private fun deleteChildren(dir: File) {
        dir.listFiles()?.forEach { child ->
            if (child.isDirectory) deleteChildren(child)
            child.delete()
        }
    }
}
