package com.example.core.media

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LocalRecording(
    val file: File,
    val name: String,
    val durationMs: Long,
    val createdAt: Long
)

class VoiceRecorderEngine(
    private val context: Context,
    private val recordingsDir: File
) {
    private var recorder: MediaRecorder? = null
    private var activeFile: File? = null
    private var player: MediaPlayer? = null

    var isRecording: Boolean = false
        private set
    var isPaused: Boolean = false
        private set

    fun startRecording(): Result<File> {
        return try {
            stopRecordingInternal()
            recordingsDir.mkdirs()
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val outFile = File(recordingsDir, "Voiceover_$stamp.m4a")
            val rec = MediaRecorder(context).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(48000)
                setAudioEncodingBitRate(192000)
                setOutputFile(outFile.absolutePath)
                prepare()
                start()
            }
            recorder = rec
            activeFile = outFile
            isRecording = true
            isPaused = false
            Result.success(outFile)
        } catch (e: Exception) {
            activeFile?.delete()
            activeFile = null
            Result.failure(e)
        }
    }

    fun pauseRecording() {
        if (isRecording && !isPaused) {
            runCatching {
                recorder?.pause()
                isPaused = true
            }
        }
    }

    fun resumeRecording() {
        if (isRecording && isPaused) {
            runCatching {
                recorder?.resume()
                isPaused = false
            }
        }
    }

    fun stopRecording(): File? = stopRecordingInternal()

    private fun stopRecordingInternal(): File? {
        val finished = activeFile
        runCatching {
            recorder?.stop()
        }
        runCatching {
            recorder?.release()
        }
        recorder = null
        activeFile = null
        isRecording = false
        isPaused = false
        return if (finished != null && finished.exists() && finished.length() > 0L) finished else {
            finished?.delete()
            null
        }
    }

    fun getAmplitudeNormalized(): Float {
        if (!isRecording || isPaused) return 0f
        val amp = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
        return (amp / 32767f).coerceIn(0f, 1f)
    }

    fun listRecordings(): List<LocalRecording> {
        recordingsDir.mkdirs()
        return recordingsDir.listFiles()
            ?.filter { it.isFile && it.length() > 0 }
            ?.sortedByDescending { it.lastModified() }
            ?.map { file ->
                val dur = probeDurationMs(file)
                LocalRecording(
                    file = file,
                    name = file.nameWithoutExtension,
                    durationMs = dur,
                    createdAt = file.lastModified()
                )
            } ?: emptyList()
    }

    fun renameRecording(file: File, newName: String): File? {
        val sanitized = newName.trim().replace(Regex("[^a-zA-Z0-9_ -]"), "")
        if (sanitized.isEmpty()) return null
        val target = File(recordingsDir, "$sanitized.${file.extension}")
        return if (file.renameTo(target)) target else null
    }

    fun deleteRecording(file: File): Boolean = file.delete()

    fun playPreview(file: File, onComplete: () -> Unit) {
        stopPlayback()
        runCatching {
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    stopPlayback()
                    onComplete()
                }
                prepare()
                start()
            }
        }
    }

    fun stopPlayback() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
    }

    private fun probeDurationMs(file: File): Long {
        val mp = MediaPlayer()
        return try {
            mp.setDataSource(file.absolutePath)
            mp.prepare()
            mp.duration.toLong().coerceAtLeast(0L)
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { mp.release() }
        }
    }

    fun release() {
        stopRecordingInternal()
        stopPlayback()
    }
}
