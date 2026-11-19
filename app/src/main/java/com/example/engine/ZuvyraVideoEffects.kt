package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.opengl.GLES20
import androidx.annotation.OptIn
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.Crop
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.ScaleAndRotateTransformation
import com.example.core.media.CubeLut3D
import com.example.core.media.OfflineMediaProcessor
import com.example.core.model.AlphaKeyMode
import com.example.core.model.BlendModeType
import com.example.core.model.BuiltInFilterType
import com.example.core.model.ClipTransition
import com.example.core.model.ColorAdjustments
import com.example.core.model.FitMode
import com.example.core.model.HslColorChannel
import com.example.core.model.TimelineClip
import com.example.core.model.TransitionDirection
import com.example.core.model.TransitionType
import com.example.core.model.VideoEffectType
import com.example.core.model.ZuvyraProject
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tan

@OptIn(UnstableApi::class)
object ZuvyraVideoEffects {

    fun buildClipVideoEffects(
        clip: TimelineClip,
        allClips: List<TimelineClip>,
        inTransition: ClipTransition?,
        outTransition: ClipTransition?
    ): List<androidx.media3.common.Effect> {
        val effects = mutableListOf<androidx.media3.common.Effect>()

        // 1. Non-destructive Crop
        val t = clip.transform
        if (t.cropLeft > 0.001f || t.cropRight > 0.001f || t.cropTop > 0.001f || t.cropBottom > 0.001f) {
            val left = (-1f + t.cropLeft * 2f).coerceIn(-1f, 0.9f)
            val right = (1f - t.cropRight * 2f).coerceIn(left + 0.05f, 1f)
            val bottom = (-1f + t.cropBottom * 2f).coerceIn(-1f, 0.9f)
            val top = (1f - t.cropTop * 2f).coerceIn(bottom + 0.05f, 1f)
            effects.add(Crop(left, right, bottom, top))
        }

        // 2. Fit / Fill base transform
        if (t.fitMode == FitMode.FILL) {
            effects.add(ScaleAndRotateTransformation.Builder().setScale(1.18f, 1.18f).build())
        }

        // 3. Animated 3D Transform (Position X/Y/Z, Scale X/Y/Z, Rot X/Y/Z, Null Parent, 3D Camera)
        effects.add(ZuvyraClipMatrixTransformation(clip, allClips, inTransition, outTransition))

        // 4. GPU Chroma Key, Alpha Key, Blend Modes, Color Grading, 8-CH HSL, .cube LUT & Effects
        effects.add(
            ZuvyraColorAndEffectsGlEffect(
                clip = clip,
                allClips = allClips,
                inTransition = inTransition,
                outTransition = outTransition
            )
        )

        return effects
    }

    fun buildCompositionOverlayEffect(
        context: Context,
        project: ZuvyraProject,
        targetWidth: Int,
        targetHeight: Int
    ): OverlayEffect? {
        val hasOverlays = project.tracks.any { track ->
            track.isVisible && track.clips.any {
                it.textData != null || it.stickerData != null || it.handwritingData != null
            }
        }
        if (!hasOverlays) return null

        val dynamicOverlay = object : BitmapOverlay() {
            private var lastTimeBucket = Long.MIN_VALUE
            private var cachedBitmap: Bitmap? = null

            override fun getBitmap(presentationTimeUs: Long): Bitmap {
                val timeMs = (presentationTimeUs / 1000L).coerceAtLeast(0L)
                val bucket = timeMs / 33L
                val existing = cachedBitmap
                if (existing != null && !existing.isRecycled && bucket == lastTimeBucket) {
                    return existing
                }
                lastTimeBucket = bucket
                val bmp = OverlayCanvasRenderer.renderAllOverlaysBitmap(
                    context = context,
                    project = project,
                    timelineMs = timeMs,
                    width = targetWidth.coerceIn(320, 1920),
                    height = targetHeight.coerceIn(180, 1920)
                )
                cachedBitmap = bmp
                return bmp
            }
        }
        return OverlayEffect(listOf(dynamicOverlay))
    }
}

/**
 * Pure-Kotlin 4x4 perspective + 3D Euler rotation matrix solver producing an exact 3x3 homogeneous
 * projection matrix for Media3's MatrixTransformation (zero native JNI dependency).
 */
object Zuvyra3DMatrixMath {
    fun buildProjectiveMatrix(
        tx: Float,
        ty: Float,
        tz: Float,
        sx: Float,
        sy: Float,
        sz: Float,
        rotXDeg: Float,
        rotYDeg: Float,
        rotZDeg: Float,
        anchorOffsetX: Float,
        anchorOffsetY: Float,
        fovDeg: Float
    ): FloatArray {
        val halfRad = ((fovDeg.coerceIn(20f, 120f) * 0.5f) * PI / 180.0)
        val focalDist = (1.0 / tan(halfRad)).toFloat().coerceIn(0.6f, 4.5f)

        val rx = (rotXDeg * PI / 180.0).toFloat()
        val ry = (rotYDeg * PI / 180.0).toFloat()
        val rz = (-rotZDeg * PI / 180.0).toFloat()

        val cx = cos(rx); val srx = sin(rx)
        val cy = cos(ry); val sry = sin(ry)
        val cz = cos(rz); val srz = sin(rz)

        // Combined Rotation R = Rz * Ry * Rx (column-major 4x4)
        val r00 = cy * cz
        val r01 = cz * sry * srx - srz * cx
        val r02 = cz * sry * cx + srz * srx

        val r10 = cy * srz
        val r11 = srz * sry * srx + cz * cx
        val r12 = srz * sry * cx - cz * srx

        val r20 = -sry
        val r21 = cy * srx
        val r22 = cy * cx

        // Perspective depth placement at z_eye = -(focalDist + tz), scaled by scaleZ foreshortening
        val effZ = (focalDist + tz).coerceAtLeast(0.35f)
        val effSz = sz.coerceIn(0.1f, 4f)

        // Apply pre-anchor scale & 3D rotation, then translate to world + camera distance
        // Point (x, y, 0, 1) -> anchor shift -> scale(sx, sy, effSz) -> rotate -> translate(anchor + tx, anchor + ty, -effZ)
        val px = -anchorOffsetX
        val py = -anchorOffsetY

        val m00 = r00 * sx
        val m01 = r01 * sy
        val m03 = r00 * (px * sx) + r01 * (py * sy) + anchorOffsetX + tx

        val m10 = r10 * sx
        val m11 = r11 * sy
        val m13 = r10 * (px * sx) + r11 * (py * sy) + anchorOffsetY + ty

        val m20 = r20 * sx * (0.5f + 0.5f * effSz)
        val m21 = r21 * sy * (0.5f + 0.5f * effSz)
        val m23 = r20 * (px * sx) + r21 * (py * sy) - effZ

        // Project through perspective focalDist:
        // X_ndc = (focalDist * m0*) / (-m2*), Y_ndc = (focalDist * m1*) / (-m2*)
        val invNear = 1f / focalDist
        return floatArrayOf(
            m00, m01, m03,
            m10, m11, m13,
            -m20 * invNear, -m21 * invNear, (-m23 * invNear).coerceAtLeast(0.25f)
        )
    }
}

@OptIn(UnstableApi::class)
class ZuvyraClipMatrixTransformation(
    private val clip: TimelineClip,
    private val allClips: List<TimelineClip>,
    private val inTransition: ClipTransition?,
    private val outTransition: ClipTransition?
) : MatrixTransformation {
    override fun getMatrix(presentationTimeUs: Long): Matrix {
        val localMs = (presentationTimeUs / 1000L).coerceAtLeast(0L)
        val timelineMs = clip.timelineStartMs + localMs
        val state = AnimationEvaluator.evaluateClipWorld3D(clip, allClips, timelineMs)

        var tx = state.posX
        var ty = -state.posY // GL Y is inverted relative to Android screen Y
        val tz = state.posZ
        var sx = state.scaleX * clip.transform.widthRatio
        var sy = state.scaleY * clip.transform.heightRatio
        val sz = state.scaleZ
        var rotZ = state.rotationDeg

        // Apply In Transition geometric motion
        if (inTransition != null && inTransition.type != TransitionType.NONE && localMs < inTransition.durationMs) {
            val p = (localMs.toFloat() / inTransition.durationMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
            val inv = (1f - p) * inTransition.intensity
            when (inTransition.type) {
                TransitionType.ZOOM_IN -> { val s = 0.4f + 0.6f * p; sx *= s; sy *= s }
                TransitionType.ZOOM_OUT -> { val s = 1.6f - 0.6f * p; sx *= s; sy *= s }
                TransitionType.SLIDE_LEFT, TransitionType.PUSH -> {
                    when (inTransition.direction) {
                        TransitionDirection.LEFT -> tx += inv * 2f
                        TransitionDirection.RIGHT -> tx -= inv * 2f
                        TransitionDirection.UP -> ty -= inv * 2f
                        TransitionDirection.DOWN -> ty += inv * 2f
                    }
                }
                TransitionType.SLIDE_RIGHT -> tx -= inv * 2f
                TransitionType.SLIDE_UP -> ty -= inv * 2f
                TransitionType.SLIDE_DOWN -> ty += inv * 2f
                TransitionType.SPIN -> rotZ += inv * 360f
                else -> Unit
            }
        }

        // Apply Out Transition geometric motion
        val dur = clip.durationMs.coerceAtLeast(1L)
        if (outTransition != null && outTransition.type != TransitionType.NONE) {
            val start = (dur - outTransition.durationMs).coerceAtLeast(0L)
            if (localMs > start) {
                val p = ((localMs - start).toFloat() / outTransition.durationMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
                val amt = p * outTransition.intensity
                when (outTransition.type) {
                    TransitionType.ZOOM_IN -> { val s = 1f + amt * 0.6f; sx *= s; sy *= s }
                    TransitionType.ZOOM_OUT -> { val s = (1f - amt * 0.5f).coerceAtLeast(0.1f); sx *= s; sy *= s }
                    TransitionType.SLIDE_LEFT -> tx -= amt * 2f
                    TransitionType.SLIDE_RIGHT -> tx += amt * 2f
                    TransitionType.SLIDE_UP -> ty += amt * 2f
                    TransitionType.SLIDE_DOWN -> ty -= amt * 2f
                    TransitionType.SPIN -> rotZ -= amt * 360f
                    else -> Unit
                }
            }
        }

        val anchorOffsetX = (clip.transform.anchorX - 0.5f) * 2f
        val anchorOffsetY = -(clip.transform.anchorY - 0.5f) * 2f

        val values = Zuvyra3DMatrixMath.buildProjectiveMatrix(
            tx = tx,
            ty = ty,
            tz = tz,
            sx = sx,
            sy = sy,
            sz = sz,
            rotXDeg = state.rotationX,
            rotYDeg = -state.rotationY,
            rotZDeg = rotZ,
            anchorOffsetX = anchorOffsetX,
            anchorOffsetY = anchorOffsetY,
            fovDeg = state.perspectiveFov
        )
        return Matrix().apply { setValues(values) }
    }
}

@OptIn(UnstableApi::class)
class ZuvyraColorAndEffectsGlEffect(
    private val clip: TimelineClip,
    private val allClips: List<TimelineClip>,
    private val inTransition: ClipTransition?,
    private val outTransition: ClipTransition?
) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return ZuvyraGlShaderProgram(useHdr, clip, allClips, inTransition, outTransition)
    }

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean {
        return false
    }
}

@OptIn(UnstableApi::class)
private class ZuvyraGlShaderProgram(
    useHdr: Boolean,
    private val clip: TimelineClip,
    private val allClips: List<TimelineClip>,
    private val inTransition: ClipTransition?,
    private val outTransition: ClipTransition?
) : BaseGlShaderProgram(useHdr, 1) {

    private var glProgram: GlProgram? = null
    private var outputSize = Size(1280, 720)
    private var lutData: CubeLut3D? = null
    private var lutTexId: Int = 0

    init {
        val lutPath = clip.colorAdjustments.customLutPath
        if (!lutPath.isNullOrBlank()) {
            val file = File(lutPath)
            if (file.exists()) {
                lutData = runCatching { OfflineMediaProcessor.parseCubeText(file.readText()) }.getOrNull()
            }
        }
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        outputSize = Size(inputWidth, inputHeight)
        if (glProgram == null) {
            glProgram = GlProgram(VERTEX_SHADER, FRAGMENT_SHADER).apply {
                setBufferAttribute(
                    "aFramePosition",
                    GlUtil.getNormalizedCoordinateBounds(),
                    GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
                )
            }
        }
        val lut = lutData
        if (lut != null && lutTexId == 0) {
            val n = lut.size
            val buf = java.nio.ByteBuffer.allocateDirect(n * n * n * 4)
            for (idx in 0 until (n * n * n)) {
                val base = idx * 3
                buf.put((lut.data[base].coerceIn(0f, 1f) * 255f).toInt().toByte())
                buf.put((lut.data[base + 1].coerceIn(0f, 1f) * 255f).toInt().toByte())
                buf.put((lut.data[base + 2].coerceIn(0f, 1f) * 255f).toInt().toByte())
                buf.put(255.toByte())
            }
            buf.flip()
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            lutTexId = ids[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lutTexId)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, n, n * n, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        }
        return outputSize
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        val program = glProgram ?: return
        val localMs = (presentationTimeUs / 1000L).coerceAtLeast(0L)
        val state = AnimationEvaluator.evaluateClipWorld3D(clip, allClips, clip.timelineStartMs + localMs)

        program.use()
        program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
        val activeLut = lutData
        if (activeLut != null && lutTexId != 0) {
            program.setSamplerTexIdUniform("uLutSampler", lutTexId, 1)
            program.setFloatUniform("uLutSize", activeLut.size.toFloat())
        } else {
            program.setSamplerTexIdUniform("uLutSampler", inputTexId, 1)
            program.setFloatUniform("uLutSize", 0f)
        }
        program.setFloatsUniform("uResolution", floatArrayOf(outputSize.width.toFloat(), outputSize.height.toFloat()))
        program.setFloatUniform("uTimeSec", localMs / 1000f)

        val c: ColorAdjustments = clip.colorAdjustments
        program.setFloatsUniform("uBasicGrade", floatArrayOf(c.brightness, c.contrast, c.saturation, c.exposure))
        program.setFloatsUniform("uToneTemp", floatArrayOf(c.temperature, c.tint, c.highlights, c.shadows))
        program.setFloatsUniform("uLevelsVignette", floatArrayOf(c.whites, c.blacks, c.fade, c.vignette))
        program.setFloatsUniform("uFilter", floatArrayOf(c.filterType.ordinal.toFloat(), c.filterIntensity))

        val hslArray = FloatArray(24)
        HslColorChannel.entries.forEachIndexed { idx, ch ->
            val adj = c.hslChannels[ch]
            if (adj != null) {
                hslArray[idx * 3] = adj.hueShift
                hslArray[idx * 3 + 1] = adj.saturation
                hslArray[idx * 3 + 2] = adj.luminance
            }
        }
        program.setFloatsUniform("uHslAdjust", hslArray)

        var blurAmt = state.extraBlur
        var sharpenAmt = c.sharpness
        var pixelateAmt = 0f
        var rgbSplitAmt = 0f
        var grainAmt = 0f
        var glowAmt = 0f
        var lightLeakAmt = 0f
        var distortAmt = 0f
        var mirrorFlag = 0f
        var flipFlag = 0f
        var shakeAmt = 0f
        var extraVignette = c.vignette

        for (fx in clip.videoEffects) {
            if (!fx.enabled || localMs < fx.startOffsetMs || localMs > fx.endOffsetMs) continue
            when (fx.type) {
                VideoEffectType.GAUSSIAN_BLUR,
                VideoEffectType.MOTION_BLUR,
                VideoEffectType.DIRECTIONAL_BLUR,
                VideoEffectType.SOFT_FOCUS,
                VideoEffectType.ZOOM_BLUR -> blurAmt = max(blurAmt, fx.intensity)
                VideoEffectType.SHARPEN -> sharpenAmt = max(sharpenAmt, fx.intensity)
                VideoEffectType.PIXELATE, VideoEffectType.MOSAIC -> pixelateAmt = max(pixelateAmt, fx.intensity)
                VideoEffectType.RGB_SPLIT, VideoEffectType.CHROMATIC_ABERRATION -> rgbSplitAmt = max(rgbSplitAmt, fx.intensity)
                VideoEffectType.FILM_GRAIN, VideoEffectType.NOISE -> grainAmt = max(grainAmt, fx.intensity)
                VideoEffectType.GLOW, VideoEffectType.GLOW_BLOOM -> glowAmt = max(glowAmt, fx.intensity)
                VideoEffectType.LIGHT_LEAK -> lightLeakAmt = max(lightLeakAmt, fx.intensity)
                VideoEffectType.BASIC_DISTORTION -> distortAmt = max(distortAmt, fx.intensity)
                VideoEffectType.MIRROR -> mirrorFlag = 1f
                VideoEffectType.FLIP -> flipFlag = 1f
                VideoEffectType.BASIC_SHAKE -> shakeAmt = max(shakeAmt, fx.intensity)
                VideoEffectType.VIGNETTE -> extraVignette = max(extraVignette, fx.intensity)
                VideoEffectType.FADE -> Unit
            }
        }

        var opacity = state.opacity
        var dipColorMix = 0f
        var dipTarget = 0f
        var wipeCutoff = 1f

        if (inTransition != null && inTransition.type != TransitionType.NONE && localMs < inTransition.durationMs) {
            val p = (localMs.toFloat() / inTransition.durationMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
            when (inTransition.type) {
                TransitionType.FADE, TransitionType.DISSOLVE, TransitionType.CROSSFADE -> opacity *= p
                TransitionType.DIP_TO_BLACK -> { dipColorMix = (1f - p); dipTarget = 0f }
                TransitionType.DIP_TO_WHITE, TransitionType.FLASH -> { dipColorMix = (1f - p); dipTarget = 1f }
                TransitionType.BLUR_TRANSITION -> blurAmt = max(blurAmt, 1f - p)
                TransitionType.SIMPLE_GLITCH -> rgbSplitAmt = max(rgbSplitAmt, (1f - p) * 0.9f)
                TransitionType.WIPE -> wipeCutoff = p
                else -> Unit
            }
        }

        val dur = clip.durationMs.coerceAtLeast(1L)
        if (outTransition != null && outTransition.type != TransitionType.NONE) {
            val start = (dur - outTransition.durationMs).coerceAtLeast(0L)
            if (localMs > start) {
                val p = ((localMs - start).toFloat() / outTransition.durationMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
                when (outTransition.type) {
                    TransitionType.FADE, TransitionType.DISSOLVE, TransitionType.CROSSFADE -> opacity *= (1f - p)
                    TransitionType.DIP_TO_BLACK -> { dipColorMix = p; dipTarget = 0f }
                    TransitionType.DIP_TO_WHITE, TransitionType.FLASH -> { dipColorMix = p; dipTarget = 1f }
                    TransitionType.BLUR_TRANSITION -> blurAmt = max(blurAmt, p)
                    TransitionType.SIMPLE_GLITCH -> rgbSplitAmt = max(rgbSplitAmt, p * 0.9f)
                    else -> Unit
                }
            }
        }

        program.setFloatsUniform("uEffectsA", floatArrayOf(blurAmt, sharpenAmt, pixelateAmt, rgbSplitAmt))
        program.setFloatsUniform("uEffectsB", floatArrayOf(grainAmt, glowAmt, lightLeakAmt, distortAmt))
        program.setFloatsUniform("uEffectsC", floatArrayOf(mirrorFlag, flipFlag, shakeAmt, extraVignette))
        program.setFloatsUniform("uCompositing", floatArrayOf(opacity, dipColorMix, dipTarget, wipeCutoff))

        // Chroma Key uniforms
        val ck = clip.chromaKey
        val kc = ck.keyColorArgb.toInt()
        program.setFloatsUniform(
            "uChromaColor",
            floatArrayOf(
                Color.red(kc) / 255f,
                Color.green(kc) / 255f,
                Color.blue(kc) / 255f,
                if (ck.enabled) 1f else 0f
            )
        )
        program.setFloatsUniform(
            "uChromaParams",
            floatArrayOf(ck.similarity, ck.smoothness, ck.spillSuppression, ck.edgeChoke)
        )

        // Alpha Key uniforms
        val ak = clip.alphaKey
        program.setFloatsUniform(
            "uAlphaKey",
            floatArrayOf(
                if (ak.enabled) 1f else 0f,
                ak.mode.ordinal.toFloat(),
                ak.threshold,
                ak.feather
            )
        )
        program.setFloatsUniform(
            "uBlendParams",
            floatArrayOf(
                if (ak.invert) 1f else 0f,
                clip.layerStyle.blendMode.ordinal.toFloat()
            )
        )

        program.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GlUtil.checkGlError()
    }

    override fun release() {
        super.release()
        if (lutTexId != 0) {
            runCatching { GLES20.glDeleteTextures(1, intArrayOf(lutTexId), 0) }
            lutTexId = 0
        }
        runCatching { glProgram?.delete() }
        glProgram = null
    }

    companion object {
        private const val VERTEX_SHADER = """
            attribute vec4 aFramePosition;
            varying vec2 vTexSamplingCoord;
            void main() {
              gl_Position = aFramePosition;
              vTexSamplingCoord = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5);
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uTexSampler;
            uniform sampler2D uLutSampler;
            uniform float uLutSize;
            uniform vec2 uResolution;
            uniform float uTimeSec;
            uniform vec4 uBasicGrade;
            uniform vec4 uToneTemp;
            uniform vec4 uLevelsVignette;
            uniform vec2 uFilter;
            uniform float uHslAdjust[24];
            uniform vec4 uEffectsA;
            uniform vec4 uEffectsB;
            uniform vec4 uEffectsC;
            uniform vec4 uCompositing;
            uniform vec4 uChromaColor;   // rgb + enabled
            uniform vec4 uChromaParams;  // similarity, smoothness, spill, edgeChoke
            uniform vec4 uAlphaKey;      // enabled, modeOrdinal, threshold, feather
            uniform vec2 uBlendParams;   // alphaInvert, blendModeOrdinal
            varying vec2 vTexSamplingCoord;

            vec3 rgb2hsv(vec3 c) {
              vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
              vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
              vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
              float d = q.x - min(q.w, q.y);
              float e = 1.0e-10;
              return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
            }

            vec3 hsv2rgb(vec3 c) {
              vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
              vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
              return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
            }

            vec2 rgbToCbCr(vec3 c) {
              float cb = -0.168736 * c.r - 0.331264 * c.g + 0.500000 * c.b;
              float cr =  0.500000 * c.r - 0.418688 * c.g - 0.081312 * c.b;
              return vec2(cb, cr);
            }

            float rand(vec2 co) {
              return fract(sin(dot(co.xy ,vec2(12.9898,78.233))) * 43758.5453);
            }

            void main() {
              vec2 uv = vTexSamplingCoord;
              if (uv.x > uCompositing.w) {
                gl_FragColor = vec4(0.0, 0.0, 0.0, 0.0);
                return;
              }

              if (uEffectsC.x > 0.5) uv.x = uv.x < 0.5 ? uv.x : (1.0 - uv.x);
              if (uEffectsC.y > 0.5) uv.y = 1.0 - uv.y;

              if (uEffectsC.z > 0.01) {
                uv.x += sin(uTimeSec * 42.0) * 0.018 * uEffectsC.z;
                uv.y += cos(uTimeSec * 37.0) * 0.018 * uEffectsC.z;
              }

              if (uEffectsB.w > 0.01) {
                vec2 centered = uv - 0.5;
                float r2 = dot(centered, centered);
                uv = 0.5 + centered * (1.0 + uEffectsB.w * 0.45 * r2);
              }

              if (uEffectsA.z > 0.01) {
                float cells = mix(180.0, 18.0, uEffectsA.z);
                uv = floor(uv * cells) / cells;
              }

              uv = clamp(uv, 0.0, 1.0);
              vec2 texel = 1.0 / max(uResolution, vec2(1.0));
              vec4 baseColor = texture2D(uTexSampler, uv);

              if (uEffectsA.w > 0.01) {
                float off = uEffectsA.w * 0.018;
                baseColor.r = texture2D(uTexSampler, clamp(uv + vec2(off, 0.0), 0.0, 1.0)).r;
                baseColor.b = texture2D(uTexSampler, clamp(uv - vec2(off, 0.0), 0.0, 1.0)).b;
              }

              if (uEffectsA.x > 0.01) {
                float radius = uEffectsA.x * 6.0;
                vec3 sum = baseColor.rgb * 0.25;
                sum += texture2D(uTexSampler, clamp(uv + texel * vec2(radius, 0.0), 0.0, 1.0)).rgb * 0.125;
                sum += texture2D(uTexSampler, clamp(uv - texel * vec2(radius, 0.0), 0.0, 1.0)).rgb * 0.125;
                sum += texture2D(uTexSampler, clamp(uv + texel * vec2(0.0, radius), 0.0, 1.0)).rgb * 0.125;
                sum += texture2D(uTexSampler, clamp(uv - texel * vec2(0.0, radius), 0.0, 1.0)).rgb * 0.125;
                sum += texture2D(uTexSampler, clamp(uv + texel * vec2(radius, radius), 0.0, 1.0)).rgb * 0.0625;
                sum += texture2D(uTexSampler, clamp(uv - texel * vec2(radius, radius), 0.0, 1.0)).rgb * 0.0625;
                sum += texture2D(uTexSampler, clamp(uv + texel * vec2(radius, -radius), 0.0, 1.0)).rgb * 0.0625;
                sum += texture2D(uTexSampler, clamp(uv - texel * vec2(radius, -radius), 0.0, 1.0)).rgb * 0.0625;
                baseColor.rgb = sum;
              }

              if (uEffectsA.y > 0.01) {
                vec3 n = texture2D(uTexSampler, clamp(uv + vec2(0.0, texel.y), 0.0, 1.0)).rgb;
                vec3 s = texture2D(uTexSampler, clamp(uv - vec2(0.0, texel.y), 0.0, 1.0)).rgb;
                vec3 e = texture2D(uTexSampler, clamp(uv + vec2(texel.x, 0.0), 0.0, 1.0)).rgb;
                vec3 w = texture2D(uTexSampler, clamp(uv - vec2(texel.x, 0.0), 0.0, 1.0)).rgb;
                vec3 blurAvg = (n + s + e + w) * 0.25;
                baseColor.rgb = clamp(baseColor.rgb + (baseColor.rgb - blurAvg) * (uEffectsA.y * 2.2), 0.0, 1.0);
              }

              if (uEffectsB.y > 0.01) {
                vec3 bright = max(baseColor.rgb - vec3(0.55), vec3(0.0)) * 2.0;
                baseColor.rgb = clamp(baseColor.rgb + bright * uEffectsB.y * 0.65, 0.0, 1.0);
              }

              vec3 col = baseColor.rgb;
              float outAlpha = baseColor.a;

              // Chroma Key (YCbCr distance + edge choke + spill suppression)
              if (uChromaColor.a > 0.5) {
                vec2 pixCbCr = rgbToCbCr(col);
                vec2 keyCbCr = rgbToCbCr(uChromaColor.rgb);
                float dist = distance(pixCbCr, keyCbCr);
                float sim = max(0.01, uChromaParams.x - uChromaParams.w * 0.25);
                float sm = max(0.005, uChromaParams.y);
                float keyMask = smoothstep(sim, sim + sm, dist);
                outAlpha *= keyMask;

                float spillAmt = uChromaParams.z * (1.0 - smoothstep(sim, sim + sm * 2.5 + 0.08, dist));
                float gray = dot(col, vec3(0.299, 0.587, 0.114));
                col = mix(col, vec3(gray), clamp(spillAmt, 0.0, 1.0));
              }

              // Alpha Key (Luma / Dark / Alpha Matte / Alpha Invert)
              if (uAlphaKey.x > 0.5) {
                int aMode = int(uAlphaKey.y + 0.5);
                float thr = uAlphaKey.z;
                float feath = max(0.005, uAlphaKey.w);
                float pixLuma = dot(col, vec3(0.299, 0.587, 0.114));
                float aMask = outAlpha;
                if (aMode == 0) {
                  // LUMA_KEY: key out bright highlights >= thr
                  aMask = 1.0 - smoothstep(thr, thr + feath, pixLuma);
                } else if (aMode == 1) {
                  // DARK_KEY: key out dark shadows <= thr
                  aMask = smoothstep(thr, thr + feath, pixLuma);
                } else if (aMode == 2) {
                  // ALPHA_MATTE: threshold texture alpha
                  aMask = smoothstep(thr, thr + feath, baseColor.a);
                } else {
                  // ALPHA_INVERT
                  aMask = 1.0 - smoothstep(thr, thr + feath, pixLuma);
                }
                if (uBlendParams.x > 0.5) {
                  aMask = 1.0 - aMask;
                }
                outAlpha *= clamp(aMask, 0.0, 1.0);
              }

              col *= pow(2.0, uBasicGrade.w * 1.5);
              col += uBasicGrade.x * 0.35;
              col = (col - 0.5) * (1.0 + uBasicGrade.y) + 0.5;
              col.r += uToneTemp.x * 0.14;
              col.b -= uToneTemp.x * 0.14;
              col.g += uToneTemp.y * 0.12;

              float luma = dot(col, vec3(0.299, 0.587, 0.114));
              col += uToneTemp.z * 0.22 * smoothstep(0.5, 1.0, luma);
              col += uToneTemp.w * 0.22 * (1.0 - smoothstep(0.0, 0.5, luma));
              col += uLevelsVignette.x * 0.15 * smoothstep(0.75, 1.0, luma);
              col -= uLevelsVignette.y * 0.15 * (1.0 - smoothstep(0.0, 0.25, luma));

              float lumaPost = dot(col, vec3(0.299, 0.587, 0.114));
              col = mix(vec3(lumaPost), col, 1.0 + uBasicGrade.z);

              vec3 hsv = rgb2hsv(clamp(col, 0.0, 1.0));
              float h = hsv.x;
              float centers[8];
              centers[0] = 0.0; centers[1] = 0.08; centers[2] = 0.16; centers[3] = 0.33;
              centers[4] = 0.50; centers[5] = 0.66; centers[6] = 0.78; centers[7] = 0.90;
              for (int i = 0; i < 8; i++) {
                float dist = abs(h - centers[i]);
                if (dist > 0.5) dist = 1.0 - dist;
                float weight = smoothstep(0.14, 0.0, dist);
                if (weight > 0.001) {
                  hsv.x = fract(hsv.x + uHslAdjust[i * 3] * 0.12 * weight);
                  hsv.y = clamp(hsv.y + uHslAdjust[i * 3 + 1] * weight, 0.0, 1.0);
                  hsv.z = clamp(hsv.z + uHslAdjust[i * 3 + 2] * 0.5 * weight, 0.0, 1.0);
                }
              }
              col = hsv2rgb(hsv);

              int fType = int(uFilter.x + 0.5);
              float fInt = uFilter.y;
              if (fType > 0 && fInt > 0.01) {
                vec3 fCol = col;
                float fl = dot(col, vec3(0.299, 0.587, 0.114));
                if (fType == 1) {
                  fCol = (vec3(col.r * 1.08 + 0.02, col.g, col.b * 0.92 + 0.04) - 0.5) * 1.15 + 0.5;
                } else if (fType == 2) {
                  fCol = vec3(col.r * 1.12, col.g * 1.04, col.b * 0.86);
                } else if (fType == 3) {
                  fCol = vec3(col.r * 0.88, col.g * 1.02, col.b * 1.15);
                } else if (fType == 4) {
                  fCol = mix(vec3(fl), col, 0.65);
                  fCol = vec3(fCol.r * 1.08 + 0.05, fCol.g * 1.01 + 0.03, fCol.b * 0.84 + 0.04);
                } else if (fType == 5) {
                  fCol = vec3(col.r * 1.06 + 0.04, col.g * 0.96 + 0.05, col.b * 0.88 + 0.06);
                } else if (fType == 6) {
                  fCol = vec3((fl - 0.5) * 1.18 + 0.5);
                } else if (fType == 7) {
                  fCol = vec3(col.r * 1.05 + 0.02, col.g * 1.02 + 0.01, col.b * 0.98);
                } else if (fType == 8) {
                  fCol = (mix(vec3(fl), col, 1.25) - 0.5) * 1.1 + 0.5;
                } else if (fType == 9) {
                  fCol = (col * 0.95 + vec3(0.03, 0.04, 0.05) - 0.5) * 1.12 + 0.5;
                } else if (fType == 10) {
                  fCol = mix(col, vec3(1.0), 0.07) * 0.96;
                } else if (fType == 11) {
                  fCol = (mix(vec3(fl), col, 0.78) - 0.5) * 1.32 + 0.5;
                }
                col = mix(col, clamp(fCol, 0.0, 1.0), fInt);
              }

              if (uLutSize > 1.5) {
                vec3 lc = clamp(col, 0.0, 1.0);
                float maxIdx = uLutSize - 1.0;
                float bCoord = lc.b * maxIdx;
                float b0 = floor(bCoord);
                float b1 = min(b0 + 1.0, maxIdx);
                float fb = bCoord - b0;
                float u = (lc.r * maxIdx + 0.5) / uLutSize;
                float totalH = uLutSize * uLutSize;
                float v0 = (b0 * uLutSize + lc.g * maxIdx + 0.5) / totalH;
                float v1 = (b1 * uLutSize + lc.g * maxIdx + 0.5) / totalH;
                vec3 s0 = texture2D(uLutSampler, vec2(u, v0)).rgb;
                vec3 s1 = texture2D(uLutSampler, vec2(u, v1)).rgb;
                col = mix(s0, s1, fb);
              }

              col = mix(col, vec3(0.12), uLevelsVignette.z * 0.55);

              if (uEffectsB.z > 0.01) {
                float leak = smoothstep(0.95, 0.15, length(uv - vec2(0.12, 0.85)));
                col += vec3(1.0, 0.45, 0.18) * leak * uEffectsB.z * 0.55;
              }

              if (uEffectsB.x > 0.01) {
                float g = (rand(uv * (uTimeSec + 1.37)) - 0.5) * uEffectsB.x * 0.28;
                col += g;
              }

              float vigAmt = max(uLevelsVignette.w, uEffectsC.w);
              if (vigAmt > 0.01) {
                float dist = distance(uv, vec2(0.5));
                float vig = smoothstep(0.85, 0.25, dist * (0.7 + vigAmt * 0.7));
                col *= mix(1.0, vig, vigAmt);
              }

              // Blend Mode stylization curve when composited standalone
              int bMode = int(uBlendParams.y + 0.5);
              if (bMode == 1) col *= col; // MULTIPLY
              else if (bMode == 2) col = 1.0 - (1.0 - col) * (1.0 - col); // SCREEN
              else if (bMode == 3) col = mix(col * col * 2.0, 1.0 - 2.0 * (1.0 - col) * (1.0 - col), step(0.5, col)); // OVERLAY
              else if (bMode == 6 || bMode == 7) col = clamp(col * 1.35, 0.0, 1.0); // ADD / LINEAR_DODGE
              else if (bMode == 8) col = clamp(col / max(1.0 - col * 0.4, vec3(0.05)), 0.0, 1.0); // COLOR_DODGE
              else if (bMode == 9) col = clamp(1.0 - (1.0 - col) / max(col + 0.35, vec3(0.05)), 0.0, 1.0); // COLOR_BURN
              else if (bMode == 15) col = clamp(col * 0.85 - vec3(0.08), 0.0, 1.0); // SUBTRACT

              outAlpha *= uCompositing.x;
              col *= outAlpha;
              col = mix(col, vec3(uCompositing.z), uCompositing.y);

              gl_FragColor = vec4(clamp(col, 0.0, 1.0), clamp(outAlpha, 0.0, 1.0));
            }
        """
    }
}
