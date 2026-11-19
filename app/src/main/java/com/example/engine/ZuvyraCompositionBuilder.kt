package com.example.engine

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import com.example.core.model.MediaType
import com.example.core.model.SpeedCurvePreset
import com.example.core.model.TimelineClip
import com.example.core.model.TrackType
import com.example.core.model.ZuvyraProject
import androidx.media3.common.audio.SpeedProvider
import java.io.File

@OptIn(UnstableApi::class)
object ZuvyraCompositionBuilder {

    fun buildComposition(
        context: Context,
        project: ZuvyraProject,
        targetWidth: Int = project.width,
        targetHeight: Int = project.height,
        targetFps: Int = project.fps,
        includeOverlays: Boolean = true
    ): Composition? {
        val anySolo = project.tracks.any { it.isSolo }

        val allClips = project.tracks.flatMap { it.clips }

        // Primary visual clips across visible video/image tracks ordered by timelineStartMs
        val visualTracks = project.tracks
            .filter { it.isVisible && (it.type == TrackType.VIDEO || it.type == TrackType.IMAGE) }
            .sortedBy { it.order }

        val primaryClips = visualTracks
            .flatMap { track -> track.clips.map { track to it } }
            .filter { (_, clip) -> clip.mediaUri != null && (clip.mediaType == MediaType.VIDEO || clip.mediaType == MediaType.IMAGE) }
            .sortedBy { (_, clip) -> clip.timelineStartMs }

        val audioTracks = project.tracks
            .filter { it.isVisible && it.type == TrackType.AUDIO }
            .sortedBy { it.order }

        if (primaryClips.isEmpty() && audioTracks.all { it.clips.isEmpty() }) {
            return null
        }

        val sequences = mutableListOf<EditedMediaItemSequence>()

        if (primaryClips.isNotEmpty()) {
            val videoItems = primaryClips.map { (track, clip) ->
                val inTrans = project.transitions.firstOrNull { it.toClipId == clip.id }
                val outTrans = project.transitions.firstOrNull { it.fromClipId == clip.id }
                val trackEffectivelyMuted = track.isMuted || (anySolo && !track.isSolo) || clip.audioDetached ||
                    clip.speedConfig.muteAudioOnSpeedChange
                buildEditedMediaItem(
                    clip = clip,
                    allClips = allClips,
                    trackVolume = track.volume,
                    trackMuted = trackEffectivelyMuted,
                    trackPan = track.pan,
                    targetFps = targetFps,
                    inTransition = inTrans,
                    outTransition = outTrans
                )
            }
            val hasAudioInVisual = primaryClips.any { (_, c) -> c.mediaType == MediaType.VIDEO && !c.audioDetached }
            val seq = if (hasAudioInVisual) {
                EditedMediaItemSequence.withAudioAndVideoFrom(videoItems)
            } else {
                EditedMediaItemSequence.withVideoFrom(videoItems)
            }
            sequences.add(seq)
        }

        // Audio-only sequences
        for (audioTrack in audioTracks) {
            val trackClips = audioTrack.clips
                .filter { it.mediaUri != null && it.mediaType == MediaType.AUDIO }
                .sortedBy { it.timelineStartMs }
            if (trackClips.isEmpty()) continue

            val trackEffectivelyMuted = audioTrack.isMuted || (anySolo && !audioTrack.isSolo)
            val audioItems = trackClips.map { clip ->
                buildEditedMediaItem(
                    clip = clip,
                    allClips = allClips,
                    trackVolume = audioTrack.volume,
                    trackMuted = trackEffectivelyMuted,
                    trackPan = audioTrack.pan,
                    targetFps = targetFps,
                    inTransition = null,
                    outTransition = null
                )
            }
            sequences.add(EditedMediaItemSequence.withAudioFrom(audioItems))
        }

        if (sequences.isEmpty()) return null

        val compVideoEffects = mutableListOf<Effect>()
        compVideoEffects.add(Presentation.createForWidthAndHeight(targetWidth, targetHeight, Presentation.LAYOUT_SCALE_TO_FIT))

        if (includeOverlays) {
            ZuvyraVideoEffects.buildCompositionOverlayEffect(context, project, targetWidth, targetHeight)
                ?.let { compVideoEffects.add(it) }
        }

        return Composition.Builder(sequences)
            .setEffects(Effects(emptyList(), compVideoEffects))
            .build()
    }

    fun buildEditedMediaItem(
        clip: TimelineClip,
        allClips: List<TimelineClip> = emptyList(),
        trackVolume: Float,
        trackMuted: Boolean,
        trackPan: Float,
        targetFps: Int,
        inTransition: com.example.core.model.ClipTransition?,
        outTransition: com.example.core.model.ClipTransition?
    ): EditedMediaItem {
        val cutoutFile = clip.maskedMediaCachePath?.let { File(it) }
        val useCutoutImage = clip.bgRemoval.enabled && cutoutFile != null && cutoutFile.exists() && clip.mediaType == MediaType.IMAGE

        val effectiveUri = when {
            useCutoutImage -> Uri.fromFile(cutoutFile!!)
            clip.isReversed && !clip.reversedCachePath.isNullOrBlank() && File(clip.reversedCachePath).exists() -> {
                Uri.fromFile(File(clip.reversedCachePath))
            }
            else -> Uri.parse(clip.mediaUri ?: "")
        }

        val mediaBuilder = MediaItem.Builder().setUri(effectiveUri)
        if (clip.mediaType == MediaType.IMAGE) {
            mediaBuilder.setImageDurationMs(clip.durationMs.coerceAtLeast(500L))
        } else if (!clip.isReversed) {
            val clipEnd = clip.sourceEndMs.coerceAtLeast(clip.sourceStartMs + 100L)
            mediaBuilder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clip.sourceStartMs.coerceAtLeast(0L))
                    .setEndPositionMs(clipEnd)
                    .build()
            )
        }

        val audioProcessors = mutableListOf<AudioProcessor>()
        val videoEffects = mutableListOf<Effect>()

        if (clip.mediaType == MediaType.VIDEO || clip.mediaType == MediaType.IMAGE) {
            videoEffects.addAll(ZuvyraVideoEffects.buildClipVideoEffects(clip, allClips, inTransition, outTransition))
        }

        // Speed & Speed Curve handling
        val speedCfg = clip.speedConfig
        val hasCustomSpeed = speedCfg.preset != SpeedCurvePreset.LINEAR || kotlin.math.abs(speedCfg.constantSpeed - 1f) > 0.02f
        if (hasCustomSpeed && clip.mediaType != MediaType.IMAGE) {
            val clipDurUs = (clip.durationMs * 1000L).coerceAtLeast(100_000L)
            val provider = object : SpeedProvider {
                override fun getSpeed(timeUs: Long): Float {
                    val norm = (timeUs.toFloat() / clipDurUs.toFloat()).coerceIn(0f, 1f)
                    return AnimationEvaluator.evaluateSpeedAt(speedCfg, norm)
                }

                override fun getNextSpeedChangeTimeUs(timeUs: Long): Long {
                    if (speedCfg.preset == SpeedCurvePreset.LINEAR) return androidx.media3.common.C.TIME_UNSET
                    val stepUs = 50_000L // 50ms ramp resolution
                    val next = timeUs + stepUs
                    return if (next >= clipDurUs) androidx.media3.common.C.TIME_UNSET else next
                }
            }
            if (clip.mediaType == MediaType.VIDEO) {
                videoEffects.add(SpeedChangeEffect(provider))
            }
            if (!trackMuted) {
                val sonic = SonicAudioProcessor().apply {
                    setSpeed(speedCfg.constantSpeed.coerceIn(0.1f, 16f))
                    val pitch = if (speedCfg.preservePitch) {
                        Math.pow(2.0, clip.audioSettings.audioEffects.pitchSemitones / 12.0).toFloat()
                    } else {
                        speedCfg.constantSpeed.coerceIn(0.25f, 4f)
                    }
                    setPitch(pitch)
                }
                audioProcessors.add(sonic)
            }
        } else if (!trackMuted && kotlin.math.abs(clip.audioSettings.audioEffects.pitchSemitones) > 0.1f) {
            val sonic = SonicAudioProcessor().apply {
                setPitch(Math.pow(2.0, clip.audioSettings.audioEffects.pitchSemitones / 12.0).toFloat())
            }
            audioProcessors.add(sonic)
        }

        if (clip.mediaType == MediaType.VIDEO || clip.mediaType == MediaType.AUDIO) {
            audioProcessors.add(
                ZuvyraAudioProcessor(
                    clipDurationMs = clip.durationMs,
                    trackVolume = trackVolume,
                    trackMuted = trackMuted,
                    trackPan = trackPan,
                    clipAudio = clip.audioSettings,
                    keyframes = clip.keyframes
                )
            )
        }

        val builder = EditedMediaItem.Builder(mediaBuilder.build())
            .setEffects(Effects(audioProcessors, videoEffects))

        if (clip.mediaType == MediaType.IMAGE) {
            builder.setFrameRate(targetFps.coerceIn(15, 60))
        }
        if (clip.mediaType == MediaType.VIDEO && (trackMuted || clip.audioDetached)) {
            builder.setRemoveAudio(true)
        }

        return builder.build()
    }
}
