package com.example.core.media

import android.app.ActivityManager
import android.content.Context
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import com.example.core.model.VideoCodecOption
import java.io.File

data class DeviceCapabilities(
    val totalRamMb: Long,
    val availableRamMb: Long,
    val cpuCores: Int,
    val availableStorageBytes: Long,
    val thermalStatusLabel: String,
    val isThermallyThrottled: Boolean,
    val supportedVideoEncoders: Set<VideoCodecOption>,
    val supports4kEncoding: Boolean,
    val recommendedPreviewShortSide: Int
)

object DeviceCapabilityProbe {

    fun probe(context: Context): DeviceCapabilities {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memInfo)
        val totalRamMb = memInfo.totalMem / (1024L * 1024L)
        val availRamMb = memInfo.availMem / (1024L * 1024L)

        val stat = StatFs(context.filesDir.absolutePath)
        val availStorageBytes = stat.availableBytes

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val thermalStatus = powerManager?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE
        val thermalLabel = when (thermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> "Nominal"
            PowerManager.THERMAL_STATUS_LIGHT -> "Warm"
            PowerManager.THERMAL_STATUS_MODERATE -> "Moderate Load"
            PowerManager.THERMAL_STATUS_SEVERE -> "High Thermal Load"
            PowerManager.THERMAL_STATUS_CRITICAL,
            PowerManager.THERMAL_STATUS_EMERGENCY,
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "Critical Thermal Limit"
            else -> "Nominal"
        }
        val isThrottled = thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE

        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        val supportedCodecs = mutableSetOf<VideoCodecOption>()
        var supports4k = false

        for (info in codecList.codecInfos) {
            if (!info.isEncoder) continue
            for (type in info.supportedTypes) {
                when {
                    type.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true) -> {
                        supportedCodecs.add(VideoCodecOption.H264)
                        val caps = runCatching { info.getCapabilitiesForType(type).videoCapabilities }.getOrNull()
                        if (caps != null && caps.isSizeSupported(3840, 2160)) {
                            supports4k = true
                        }
                    }
                    type.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, ignoreCase = true) -> {
                        supportedCodecs.add(VideoCodecOption.H265)
                        val caps = runCatching { info.getCapabilitiesForType(type).videoCapabilities }.getOrNull()
                        if (caps != null && caps.isSizeSupported(3840, 2160)) {
                            supports4k = true
                        }
                    }
                    type.equals(MediaFormat.MIMETYPE_VIDEO_VP9, ignoreCase = true) -> {
                        supportedCodecs.add(VideoCodecOption.VP9)
                    }
                }
            }
        }
        if (supportedCodecs.isEmpty()) {
            supportedCodecs.add(VideoCodecOption.H264)
        }

        val recommendedPreview = when {
            isThrottled || totalRamMb < 4096 -> 480
            totalRamMb < 6144 -> 720
            else -> 1080
        }

        return DeviceCapabilities(
            totalRamMb = totalRamMb,
            availableRamMb = availRamMb,
            cpuCores = Runtime.getRuntime().availableProcessors(),
            availableStorageBytes = availStorageBytes,
            thermalStatusLabel = thermalLabel,
            isThermallyThrottled = isThrottled,
            supportedVideoEncoders = supportedCodecs,
            supports4kEncoding = supports4k,
            recommendedPreviewShortSide = recommendedPreview
        )
    }

    fun hasSufficientStorage(path: File, requiredBytes: Long): Boolean {
        return runCatching {
            val stat = StatFs(path.absolutePath)
            stat.availableBytes > requiredBytes + (64L * 1024L * 1024L)
        }.getOrDefault(true)
    }

    fun getPublicMoviesDir(): File {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
    }
}
