package com.example.core.media

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.example.core.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

sealed class MediaInspectResult {
    data class Success(
        val uri: Uri,
        val displayName: String,
        val mimeType: String,
        val mediaType: MediaType,
        val durationMs: Long,
        val width: Int,
        val height: Int,
        val fps: Int,
        val sizeBytes: Long,
        val hasAudioTrack: Boolean,
        val folderLabel: String
    ) : MediaInspectResult()

    data class UnsupportedOrError(
        val uri: Uri,
        val displayName: String,
        val reason: String
    ) : MediaInspectResult()
}

object MediaInspector {

    fun takePersistablePermissionIfPossible(context: Context, uri: Uri) {
        if (uri.scheme == "content") {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
    }

    suspend fun inspectUri(context: Context, uri: Uri, folderLabel: String = "Imported"): MediaInspectResult =
        withContext(Dispatchers.IO) {
            takePersistablePermissionIfPossible(context, uri)
            val cr = context.contentResolver

            var displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "Media"
            var sizeBytes = 0L

            runCatching {
                cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                    ?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (nameIdx >= 0) displayName = cursor.getString(nameIdx) ?: displayName
                            if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) sizeBytes = cursor.getLong(sizeIdx)
                        }
                    }
            }

            val detectedMime = cr.getType(uri) ?: run {
                val ext = displayName.substringAfterLast('.', "").lowercase()
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: ""
            }

            val mediaType = classifyMediaType(detectedMime, displayName)
                ?: return@withContext MediaInspectResult.UnsupportedOrError(
                    uri = uri,
                    displayName = displayName,
                    reason = "Unsupported file format ($detectedMime). Supported: MP4, MOV, MKV, WebM, 3GP, JPG, PNG, WEBP, BMP, GIF, MP3, WAV, AAC, M4A, FLAC, OGG."
                )

            return@withContext when (mediaType) {
                MediaType.IMAGE -> inspectImage(context, uri, displayName, detectedMime, sizeBytes, folderLabel)
                MediaType.VIDEO -> inspectVideo(context, uri, displayName, detectedMime, sizeBytes, folderLabel)
                MediaType.AUDIO -> inspectAudio(context, uri, displayName, detectedMime, sizeBytes, folderLabel)
                else -> MediaInspectResult.UnsupportedOrError(uri, displayName, "Unsupported media category")
            }
        }

    private fun classifyMediaType(mime: String, name: String): MediaType? {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            mime.startsWith("video/") || ext in setOf("mp4", "mov", "mkv", "webm", "3gp", "m4v") -> MediaType.VIDEO
            mime.startsWith("image/") || ext in setOf("jpg", "jpeg", "png", "webp", "bmp", "gif") -> MediaType.IMAGE
            mime.startsWith("audio/") || ext in setOf("mp3", "wav", "aac", "m4a", "flac", "ogg", "opus") -> MediaType.AUDIO
            else -> null
        }
    }

    private fun inspectImage(
        context: Context,
        uri: Uri,
        displayName: String,
        mime: String,
        sizeBytes: Long,
        folderLabel: String
    ): MediaInspectResult {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            } ?: return MediaInspectResult.UnsupportedOrError(uri, displayName, "Cannot open image stream.")

            if (opts.outWidth <= 0 || opts.outHeight <= 0) {
                return MediaInspectResult.UnsupportedOrError(uri, displayName, "Corrupted or unreadable image file.")
            }
            MediaInspectResult.Success(
                uri = uri,
                displayName = displayName,
                mimeType = mime.ifEmpty { "image/*" },
                mediaType = MediaType.IMAGE,
                durationMs = 5000L, // Default image clip duration 5s
                width = opts.outWidth,
                height = opts.outHeight,
                fps = 30,
                sizeBytes = sizeBytes,
                hasAudioTrack = false,
                folderLabel = folderLabel
            )
        } catch (e: Exception) {
            MediaInspectResult.UnsupportedOrError(uri, displayName, "Image decode error: ${e.localizedMessage}")
        }
    }

    private fun inspectVideo(
        context: Context,
        uri: Uri,
        displayName: String,
        mime: String,
        sizeBytes: Long,
        folderLabel: String
    ): MediaInspectResult {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.coerceAtLeast(100L) ?: 0L
            val rawW = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val rawH = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val (w, h) = if (rotation == 90 || rotation == 270) rawH to rawW else rawW to rawH
            val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) != null
            val captureFps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                ?.toFloatOrNull()?.toInt()?.coerceIn(15, 120) ?: 30

            if (durationMs <= 0L || w <= 0 || h <= 0) {
                return MediaInspectResult.UnsupportedOrError(uri, displayName, "No decodable video stream found.")
            }

            MediaInspectResult.Success(
                uri = uri,
                displayName = displayName,
                mimeType = mime.ifEmpty { "video/mp4" },
                mediaType = MediaType.VIDEO,
                durationMs = durationMs,
                width = w,
                height = h,
                fps = captureFps,
                sizeBytes = sizeBytes,
                hasAudioTrack = hasAudio,
                folderLabel = folderLabel
            )
        } catch (e: Exception) {
            MediaInspectResult.UnsupportedOrError(uri, displayName, "Cannot decode video: ${e.localizedMessage}")
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun inspectAudio(
        context: Context,
        uri: Uri,
        displayName: String,
        mime: String,
        sizeBytes: Long,
        folderLabel: String
    ): MediaInspectResult {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.coerceAtLeast(100L) ?: 0L
            if (durationMs <= 0L) {
                return MediaInspectResult.UnsupportedOrError(uri, displayName, "Audio file has zero duration or is unreadable.")
            }
            MediaInspectResult.Success(
                uri = uri,
                displayName = displayName,
                mimeType = mime.ifEmpty { "audio/*" },
                mediaType = MediaType.AUDIO,
                durationMs = durationMs,
                width = 0,
                height = 0,
                fps = 0,
                sizeBytes = sizeBytes,
                hasAudioTrack = true,
                folderLabel = folderLabel
            )
        } catch (e: Exception) {
            MediaInspectResult.UnsupportedOrError(uri, displayName, "Cannot decode audio: ${e.localizedMessage}")
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * Extracts real normalized PCM amplitude peaks (0f..1f) from an audio or video URI for timeline waveforms.
     */
    suspend fun extractAudioWaveform(
        context: Context,
        uri: Uri,
        bucketCount: Int = 64
    ): List<Float> = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = fmt
                    break
                }
            }
            if (audioTrackIndex < 0 || audioFormat == null) return@withContext emptyList()
            extractor.selectTrack(audioTrackIndex)

            val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: return@withContext emptyList()
            val durationUs = if (audioFormat.containsKey(MediaFormat.KEY_DURATION)) {
                audioFormat.getLong(MediaFormat.KEY_DURATION).coerceAtLeast(1L)
            } else 10_000_000L

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(audioFormat, null, null, 0)
            codec.start()

            val buckets = FloatArray(bucketCount)
            val counts = IntArray(bucketCount)
            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var totalSamplesProcessed = 0
            val maxSamplesCap = 400_000 // Cap decode work for fast responsiveness on long files

            while (!outputDone && totalSamplesProcessed < maxSamplesCap) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(5000)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)
                        if (inBuf != null) {
                            val sampleSize = extractor.readSampleData(inBuf, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(bufferInfo, 5000)
                if (outIdx >= 0) {
                    val outBuf = codec.getOutputBuffer(outIdx)
                    if (outBuf != null && bufferInfo.size > 0) {
                        outBuf.position(bufferInfo.offset)
                        outBuf.limit(bufferInfo.offset + bufferInfo.size)
                        val shortBuf = outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val bucketIdx = ((bufferInfo.presentationTimeUs.toDouble() / durationUs) * bucketCount)
                            .toInt().coerceIn(0, bucketCount - 1)
                        var localPeak = 0f
                        var step = 0
                        while (shortBuf.hasRemaining()) {
                            val s = abs(shortBuf.get().toInt()) / 32768f
                            if (s > localPeak) localPeak = s
                            step++
                            if (step % 8 == 0) totalSamplesProcessed++
                        }
                        buckets[bucketIdx] = max(buckets[bucketIdx], localPeak)
                        counts[bucketIdx]++
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true
                    }
                } else if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER && inputDone) {
                    break
                }
            }

            val maxPeak = buckets.maxOrNull()?.coerceAtLeast(0.05f) ?: 1f
            buckets.map { min(1f, it / maxPeak) }
        } catch (_: Exception) {
            emptyList()
        } finally {
            runCatching { codec?.stop(); codec?.release() }
            runCatching { extractor.release() }
        }
    }
}
