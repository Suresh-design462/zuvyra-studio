package com.example.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import com.example.core.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

class ThumbnailEngine(
    private val context: Context,
    private val thumbnailsDir: File
) {
    private val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024L).toInt()
    private val memoryCache = object : LruCache<String, Bitmap>(maxMemoryKb / 8) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    suspend fun getThumbnail(
        uri: Uri,
        mediaType: MediaType,
        timeMs: Long = 500L,
        targetWidth: Int = 240,
        targetHeight: Int = 135
    ): Bitmap? = withContext(Dispatchers.IO) {
        val key = hashKey("${uri}_${timeMs}_${targetWidth}x${targetHeight}")
        memoryCache.get(key)?.let { if (!it.isRecycled) return@withContext it }

        val diskFile = File(thumbnailsDir, "$key.jpg")
        if (diskFile.exists()) {
            val decoded = BitmapFactory.decodeFile(diskFile.absolutePath)
            if (decoded != null) {
                memoryCache.put(key, decoded)
                return@withContext decoded
            }
        }

        val bitmap = when (mediaType) {
            MediaType.VIDEO -> extractVideoFrame(uri, timeMs, targetWidth, targetHeight)
            MediaType.IMAGE -> decodeScaledImage(uri, targetWidth, targetHeight)
            else -> null
        } ?: return@withContext null

        memoryCache.put(key, bitmap)
        runCatching {
            FileOutputStream(diskFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos)
            }
        }
        bitmap
    }

    suspend fun saveProjectThumbnail(uri: Uri, mediaType: MediaType, projectId: String): String? =
        withContext(Dispatchers.IO) {
            val bmp = getThumbnail(uri, mediaType, 500L, 480, 270) ?: return@withContext null
            val file = File(thumbnailsDir, "project_${projectId}.jpg")
            runCatching {
                FileOutputStream(file).use { fos ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                }
                file.absolutePath
            }.getOrNull()
        }

    private fun extractVideoFrame(uri: Uri, timeMs: Long, w: Int, h: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.getScaledFrameAtTime(
                timeMs * 1000L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                w,
                h
            ) ?: retriever.frameAtTime
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun decodeScaledImage(uri: Uri, reqW: Int, reqH: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            var sampleSize = 1
            while (bounds.outWidth / sampleSize > reqW * 2 && bounds.outHeight / sampleSize > reqH * 2) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun clearMemoryCache() {
        memoryCache.evictAll()
    }

    private fun hashKey(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }
}
