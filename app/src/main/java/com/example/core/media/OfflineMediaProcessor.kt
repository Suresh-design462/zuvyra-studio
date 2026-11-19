package com.example.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.view.Surface
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class CubeLut3D(
    val title: String,
    val size: Int,
    val data: FloatArray // size * size * size * 3 RGB floats 0f..1f
)

object OfflineMediaProcessor {

    /**
     * Non-destructively captures a lossless PNG freeze frame from the exact source timestamp.
     */
    suspend fun captureFreezeFrame(
        context: Context,
        sourceUri: Uri,
        sourceTimeMs: Long,
        outputDir: File
    ): Result<File> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, sourceUri)
            val bitmap = retriever.getFrameAtTime(
                sourceTimeMs * 1000L,
                MediaMetadataRetriever.OPTION_CLOSEST
            ) ?: retriever.frameAtTime
                ?: return@withContext Result.failure(IllegalStateException("Unable to decode frame at ${sourceTimeMs}ms"))

            val outFile = File(outputDir, "freeze_${UUID.randomUUID().toString().take(8)}.png")
            FileOutputStream(outFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            bitmap.recycle()
            Result.success(outFile)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * Non-destructively extracts the audio stream from a video clip into a local .m4a file.
     * Uses fast stream-copy if already AAC, otherwise transcodes cleanly via Media3 Transformer.
     */
    @OptIn(UnstableApi::class)
    suspend fun extractAudioToM4a(
        context: Context,
        sourceUri: Uri,
        startMs: Long,
        endMs: Long,
        outputDir: File
    ): Result<File> {
        val outFile = File(outputDir, "audio_ext_${UUID.randomUUID().toString().take(8)}.m4a")
        // Attempt fast MediaExtractor -> MediaMuxer copy if track is AAC
        val fastCopied = withContext(Dispatchers.IO) {
            tryFastAudioCopy(context, sourceUri, startMs, endMs, outFile)
        }
        if (fastCopied) {
            return Result.success(outFile)
        }

        // Fall back to Media3 Transformer audio-only export on Main thread
        return withContext(Dispatchers.Main) {
            try {
                suspendCancellableCoroutine { cont ->
                    val mediaItem = MediaItem.Builder()
                        .setUri(sourceUri)
                        .setClippingConfiguration(
                            MediaItem.ClippingConfiguration.Builder()
                                .setStartPositionMs(startMs.coerceAtLeast(0L))
                                .setEndPositionMs(endMs.coerceAtLeast(startMs + 100L))
                                .build()
                        )
                        .build()
                    val editedItem = EditedMediaItem.Builder(mediaItem)
                        .setRemoveVideo(true)
                        .build()

                    val transformer = Transformer.Builder(context)
                        .addListener(object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                if (cont.isActive) cont.resume(Result.success(outFile))
                            }

                            override fun onError(
                                composition: Composition,
                                exportResult: ExportResult,
                                exportException: ExportException
                            ) {
                                outFile.delete()
                                if (cont.isActive) cont.resume(Result.failure(exportException))
                            }
                        })
                        .build()

                    cont.invokeOnCancellation {
                        transformer.cancel()
                        outFile.delete()
                    }
                    transformer.start(editedItem, outFile.absolutePath)
                }
            } catch (e: Exception) {
                outFile.delete()
                Result.failure(e)
            }
        }
    }

    private fun tryFastAudioCopy(
        context: Context,
        sourceUri: Uri,
        startMs: Long,
        endMs: Long,
        outFile: File
    ): Boolean {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        return try {
            extractor.setDataSource(context, sourceUri, null)
            var audioTrack = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime == MediaFormat.MIMETYPE_AUDIO_AAC) {
                    audioTrack = i
                    format = fmt
                    break
                }
            }
            if (audioTrack < 0 || format == null) return false

            extractor.selectTrack(audioTrack)
            val startUs = startMs * 1000L
            val endUs = endMs * 1000L
            if (startUs > 0) extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxTrack = muxer.addTrack(format)
            muxer.start()

            val buffer = ByteBuffer.allocateDirect(256 * 1024)
            val info = MediaCodec.BufferInfo()
            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs > endUs) break
                if (sampleTimeUs >= startUs) {
                    info.offset = 0
                    info.size = sampleSize
                    info.presentationTimeUs = (sampleTimeUs - startUs).coerceAtLeast(0L)
                    info.flags = extractor.sampleFlags
                    muxer.writeSampleData(muxTrack, buffer, info)
                }
                extractor.advance()
            }
            true
        } catch (_: Exception) {
            outFile.delete()
            false
        } finally {
            runCatching { muxer?.stop(); muxer?.release() }
            runCatching { extractor.release() }
        }
    }

    /**
     * Generates a locally cached time-reversed MP4 copy of a trimmed clip segment using
     * MediaMetadataRetriever + hardware H.264 MediaCodec Surface encoding + MediaMuxer.
     */
    suspend fun reverseVideoClip(
        context: Context,
        sourceUri: Uri,
        sourceStartMs: Long,
        sourceEndMs: Long,
        fps: Int,
        outputDir: File,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val durationMs = (sourceEndMs - sourceStartMs).coerceAtLeast(200L)
        if (durationMs > 30_000L) {
            return@withContext Result.failure(
                IllegalArgumentException("To preserve device memory, reverse clips up to 30 seconds at a time (split longer clips first).")
            )
        }
        val outFile = File(outputDir, "rev_${UUID.randomUUID().toString().take(8)}.mp4")
        val retriever = MediaMetadataRetriever()
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var inputSurface: Surface? = null

        try {
            retriever.setDataSource(context, sourceUri)
            val rawW = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1280
            val rawH = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 720
            val rot = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val (srcW, srcH) = if (rot == 90 || rot == 270) rawH to rawW else rawW to rawH

            // Cap reverse buffer resolution to 1080p for fast & safe encoding
            val scale = if (maxOf(srcW, srcH) > 1920) 1920f / maxOf(srcW, srcH) else 1f
            val encW = (((srcW * scale).toInt() / 16) * 16).coerceAtLeast(320)
            val encH = (((srcH * scale).toInt() / 16) * 16).coerceAtLeast(240)
            val targetFps = fps.coerceIn(15, 30)

            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, encW, encH).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, 6_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, targetFps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = encoder.createInputSurface()
            encoder.start()

            muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var muxTrackIndex = -1
            var muxerStarted = false
            val bufferInfo = MediaCodec.BufferInfo()

            val totalFrames = ((durationMs / 1000.0) * targetFps).toInt().coerceAtLeast(2)
            val frameStepMs = durationMs.toDouble() / totalFrames

            fun drainEncoder(endOfStream: Boolean) {
                if (endOfStream) encoder.signalEndOfInputStream()
                while (true) {
                    val outIdx = encoder.dequeueOutputBuffer(bufferInfo, 10_000)
                    when {
                        outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            if (!endOfStream) break
                        }
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (!muxerStarted) {
                                muxTrackIndex = muxer.addTrack(encoder.outputFormat)
                                muxer.start()
                                muxerStarted = true
                            }
                        }
                        outIdx >= 0 -> {
                            val encodedBuf = encoder.getOutputBuffer(outIdx)
                            if (encodedBuf != null && bufferInfo.size > 0 && muxerStarted) {
                                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                                    encodedBuf.position(bufferInfo.offset)
                                    encodedBuf.limit(bufferInfo.offset + bufferInfo.size)
                                    muxer.writeSampleData(muxTrackIndex, encodedBuf, bufferInfo)
                                }
                            }
                            encoder.releaseOutputBuffer(outIdx, false)
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                        }
                    }
                }
            }

            for (i in 0 until totalFrames) {
                val srcTimeMs = (sourceEndMs - (i * frameStepMs)).toLong().coerceIn(sourceStartMs, sourceEndMs)
                val frameBmp = retriever.getScaledFrameAtTime(
                    srcTimeMs * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST,
                    encW,
                    encH
                )
                if (frameBmp != null) {
                    val canvas: Canvas = inputSurface.lockCanvas(null)
                    try {
                        canvas.drawBitmap(frameBmp, null, Rect(0, 0, encW, encH), null)
                    } finally {
                        inputSurface.unlockCanvasAndPost(canvas)
                        frameBmp.recycle()
                    }
                    drainEncoder(false)
                }
                onProgress((i + 1f) / totalFrames)
            }
            drainEncoder(true)
            Result.success(outFile)
        } catch (e: Exception) {
            outFile.delete()
            Result.failure(e)
        } finally {
            runCatching { retriever.release() }
            runCatching { inputSurface?.release() }
            runCatching { encoder?.stop(); encoder?.release() }
            runCatching { muxer?.stop(); muxer?.release() }
        }
    }

    /**
     * Parses a standard .cube 3D LUT file from disk or SAF Uri.
     */
    suspend fun parseCubeLut(context: Context, uri: Uri, destDir: File): Result<Pair<File, CubeLut3D>> =
        withContext(Dispatchers.IO) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: return@withContext Result.failure(IllegalArgumentException("Cannot read .cube file"))
                val parsed = parseCubeText(text)
                    ?: return@withContext Result.failure(IllegalArgumentException("Invalid or unsupported .cube 3D LUT format"))

                val savedFile = File(destDir, "lut_${UUID.randomUUID().toString().take(8)}.cube")
                savedFile.writeText(text)
                Result.success(savedFile to parsed)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun parseCubeText(text: String): CubeLut3D? {
        var title = "Custom LUT"
        var size = 0
        val values = ArrayList<Float>(32 * 32 * 32 * 3)

        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            when {
                line.startsWith("TITLE", ignoreCase = true) -> {
                    title = line.substringAfter("TITLE").trim().trim('"')
                }
                line.startsWith("LUT_3D_SIZE", ignoreCase = true) -> {
                    size = line.substringAfter("LUT_3D_SIZE").trim().toIntOrNull() ?: 0
                }
                line.startsWith("DOMAIN_") || line.startsWith("LUT_1D_SIZE") -> Unit
                else -> {
                    val parts = line.split(Regex("\\s+"))
                    if (parts.size >= 3) {
                        val r = parts[0].toFloatOrNull()
                        val g = parts[1].toFloatOrNull()
                        val b = parts[2].toFloatOrNull()
                        if (r != null && g != null && b != null) {
                            values.add(r.coerceIn(0f, 1f))
                            values.add(g.coerceIn(0f, 1f))
                            values.add(b.coerceIn(0f, 1f))
                        }
                    }
                }
            }
        }
        if (size < 2 || values.size < size * size * size * 3) return null
        return CubeLut3D(title, size, values.toFloatArray())
    }
}
