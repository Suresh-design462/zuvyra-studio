package com.example.engine

import com.example.core.model.Camera3DConfig
import com.example.core.model.CameraMotionPreset
import com.example.core.model.ClipAnimationConfig
import com.example.core.model.EasingType
import com.example.core.model.InAnimationType
import com.example.core.model.Keyframe
import com.example.core.model.KeyframeProperty
import com.example.core.model.MediaType
import com.example.core.model.OutAnimationType
import com.example.core.model.OverallAnimationType
import com.example.core.model.SpeedCurveConfig
import com.example.core.model.SpeedCurvePreset
import com.example.core.model.SpeedPoint
import com.example.core.model.TimelineClip
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

data class EvaluatedClipState(
    val posX: Float,
    val posY: Float,
    val posZ: Float,
    val scaleX: Float,
    val scaleY: Float,
    val scaleZ: Float,
    val rotationDeg: Float, // Z-axis Roll
    val rotationX: Float,   // 3D Pitch
    val rotationY: Float,   // 3D Yaw
    val opacity: Float,
    val volume: Float,
    val extraBlur: Float,
    val perspectiveFov: Float = 55f,
    val perspectiveScale: Float = 1f
)

data class EvaluatedCamera3D(
    val posX: Float = 0f,
    val posY: Float = 0f,
    val posZ: Float = 0f,
    val rotX: Float = 0f,
    val rotY: Float = 0f,
    val rotZ: Float = 0f,
    val fovDeg: Float = 55f,
    val depthParallax: Float = 1f,
    val isActive: Boolean = false
)

object AnimationEvaluator {

    fun computePerspectiveScale(posZ: Float, scaleZ: Float, fovDeg: Float): Float {
        // Camera sits at Z = -distance where focal length ~ 1 / tan(fov/2)
        val halfRad = ((fovDeg.coerceIn(20f, 120f) * 0.5f) * PI / 180.0)
        val focalDist = (1.0 / tan(halfRad)).toFloat().coerceIn(0.6f, 4.5f)
        // Z > 0 pushes deeper into screen; Z < 0 pulls closer toward camera
        val effectiveDepth = (focalDist + posZ).coerceAtLeast(0.28f)
        val baseScale = (focalDist / effectiveDepth).coerceIn(0.18f, 3.8f)
        return (baseScale * (0.65f + 0.35f * scaleZ.coerceIn(0.1f, 4f))).coerceIn(0.12f, 4.5f)
    }

    fun evaluateClipAt(clip: TimelineClip, clipLocalMs: Long): EvaluatedClipState {
        val base = clip.transform
        val dur = clip.durationMs.coerceAtLeast(1L)
        val tMs = clipLocalMs.coerceIn(0L, dur)

        var posX = interpolateProperty(clip.keyframes, KeyframeProperty.POS_X, tMs, base.posX)
        var posY = interpolateProperty(clip.keyframes, KeyframeProperty.POS_Y, tMs, base.posY)
        var posZ = interpolateProperty(clip.keyframes, KeyframeProperty.POS_Z, tMs, base.posZ)
        var scaleX = interpolateProperty(clip.keyframes, KeyframeProperty.SCALE_X, tMs, base.scaleX)
        var scaleY = interpolateProperty(clip.keyframes, KeyframeProperty.SCALE_Y, tMs, base.scaleY)
        var scaleZ = interpolateProperty(clip.keyframes, KeyframeProperty.SCALE_Z, tMs, base.scaleZ)
        var rotZ = interpolateProperty(clip.keyframes, KeyframeProperty.ROTATION, tMs, base.rotation)
        var rotX = interpolateProperty(clip.keyframes, KeyframeProperty.ROTATION_X, tMs, base.rotationX)
        var rotY = interpolateProperty(clip.keyframes, KeyframeProperty.ROTATION_Y, tMs, base.rotationY)
        var opacity = interpolateProperty(clip.keyframes, KeyframeProperty.OPACITY, tMs, base.opacity)
        val volume = interpolateProperty(clip.keyframes, KeyframeProperty.VOLUME, tMs, clip.audioSettings.volume)
        val fov = interpolateProperty(clip.keyframes, KeyframeProperty.CAMERA_FOV, tMs, base.perspectiveFov)
        var extraBlur = 0f

        val anim: ClipAnimationConfig = clip.animation

        // In Animation (19 presets)
        if (anim.inType != InAnimationType.NONE && anim.inDurationMs > 0 && tMs < anim.inDurationMs) {
            val rawP = (tMs.toFloat() / anim.inDurationMs.toFloat()).coerceIn(0f, 1f)
            val p = easeOutCubic(rawP)
            val inv = (1f - p) * anim.inIntensity
            when (anim.inType) {
                InAnimationType.FADE_IN -> opacity *= p
                InAnimationType.ZOOM_IN -> {
                    val s = 0.2f + 0.8f * p
                    scaleX *= s; scaleY *= s; opacity *= p
                }
                InAnimationType.SLIDE_LEFT -> posX -= inv * 1.2f
                InAnimationType.SLIDE_RIGHT -> posX += inv * 1.2f
                InAnimationType.SLIDE_UP -> posY += inv * 1.2f
                InAnimationType.SLIDE_DOWN -> posY -= inv * 1.2f
                InAnimationType.ROTATE_IN -> {
                    rotZ -= inv * 90f; opacity *= p
                }
                InAnimationType.POP_IN -> {
                    val bounce = easeOutBack(rawP)
                    scaleX *= bounce; scaleY *= bounce; opacity *= rawP.coerceIn(0f, 1f)
                }
                InAnimationType.SCALE_IN -> {
                    scaleX *= p; scaleY *= p
                }
                InAnimationType.BOUNCE_IN -> {
                    val b = easeOutBounce(rawP)
                    scaleX *= b; scaleY *= b
                }
                InAnimationType.BLUR_IN -> {
                    extraBlur += inv * 0.8f; opacity *= p
                }
                InAnimationType.FLIP_3D_X -> {
                    rotX += inv * 95f; opacity *= p.coerceIn(0f, 1f)
                }
                InAnimationType.FLIP_3D_Y -> {
                    rotY -= inv * 95f; opacity *= p.coerceIn(0f, 1f)
                }
                InAnimationType.SWING_IN -> {
                    val swing = sin(rawP * 3f * PI).toFloat() * (1f - rawP) * 28f * anim.inIntensity
                    rotX += swing; rotZ += swing * 0.5f; opacity *= p
                }
                InAnimationType.SWIRL_IN -> {
                    rotZ -= inv * 240f
                    val s = 0.25f + 0.75f * p
                    scaleX *= s; scaleY *= s; opacity *= p
                }
                InAnimationType.ELASTIC_IN -> {
                    val e = easeOutElastic(rawP)
                    scaleX *= (1f + (e - 1f) * anim.inIntensity).coerceAtLeast(0.05f)
                    scaleY *= (1f + (e - 1f) * anim.inIntensity).coerceAtLeast(0.05f)
                    opacity *= rawP.coerceIn(0f, 1f)
                }
                InAnimationType.DROP_3D -> {
                    posZ -= inv * 1.4f
                    val s = 1f + inv * 0.5f
                    scaleX *= s; scaleY *= s; opacity *= p
                }
                InAnimationType.GLITCH_IN -> {
                    val jitter = sin(rawP * 48f * PI).toFloat() * inv * 0.14f
                    posX += jitter
                    opacity *= if ((rawP * 12f).toInt() % 3 == 0 && rawP < 0.7f) 0.4f else p
                }
                InAnimationType.WIPE_IN -> {
                    posX -= inv * 0.6f
                    scaleX *= (0.1f + 0.9f * p)
                    opacity *= p
                }
                InAnimationType.NONE -> Unit
            }
        }

        // Overall Loop Animation (15 presets)
        if (anim.overallType != OverallAnimationType.NONE) {
            val sec = (tMs / 1000f) * anim.overallSpeed
            val amp = anim.overallIntensity
            when (anim.overallType) {
                OverallAnimationType.PULSE -> {
                    val s = 1f + sin(sec * 2f * PI).toFloat() * 0.08f * amp
                    scaleX *= s; scaleY *= s
                }
                OverallAnimationType.FLOATING -> {
                    posY += sin(sec * 2f * PI).toFloat() * 0.06f * amp
                }
                OverallAnimationType.BREATHING -> {
                    val s = 1f + sin(sec * 1.3f * PI).toFloat() * 0.05f * amp
                    scaleX *= s; scaleY *= s
                    opacity *= (1f - 0.1f * amp + 0.1f * amp * sin(sec * 1.3f * PI).toFloat()).coerceIn(0.2f, 1f)
                }
                OverallAnimationType.WOBBLE -> {
                    rotZ += sin(sec * 3f * PI).toFloat() * 6f * amp
                }
                OverallAnimationType.SHAKE -> {
                    posX += sin(sec * 18f * PI).toFloat() * 0.03f * amp
                    posY += cos(sec * 23f * PI).toFloat() * 0.03f * amp
                }
                OverallAnimationType.CONTINUOUS_ROTATION -> {
                    rotZ += sec * 45f * amp
                }
                OverallAnimationType.ZOOM_LOOP -> {
                    val s = 1f + 0.15f * amp * ((sin(sec * 1.5f * PI).toFloat() + 1f) * 0.5f)
                    scaleX *= s; scaleY *= s
                }
                OverallAnimationType.GENTLE_MOVEMENT -> {
                    posX += sin(sec * 1.1f * PI).toFloat() * 0.04f * amp
                    posY += cos(sec * 0.9f * PI).toFloat() * 0.03f * amp
                }
                OverallAnimationType.ORBIT_3D -> {
                    rotY += sin(sec * 1.4f * PI).toFloat() * 22f * amp
                    rotX += cos(sec * 1.1f * PI).toFloat() * 12f * amp
                    posZ += sin(sec * 1.4f * PI).toFloat() * 0.18f * amp
                }
                OverallAnimationType.PENDULUM_3D -> {
                    rotX += sin(sec * 2.0f * PI).toFloat() * 24f * amp
                }
                OverallAnimationType.HEARTBEAT -> {
                    val phase = (sec * 1.2f) % 1f
                    val beat = when {
                        phase < 0.15f -> sin((phase / 0.15f) * PI).toFloat() * 0.18f * amp
                        phase in 0.22f..0.38f -> sin(((phase - 0.22f) / 0.16f) * PI).toFloat() * 0.11f * amp
                        else -> 0f
                    }
                    scaleX *= (1f + beat); scaleY *= (1f + beat)
                }
                OverallAnimationType.FLICKER -> {
                    val f = 0.72f + 0.28f * ((sin(sec * 34f * PI).toFloat() + 1f) * 0.5f)
                    opacity *= (1f - amp * 0.45f * (1f - f)).coerceIn(0.15f, 1f)
                }
                OverallAnimationType.FLOAT_3D_DEPTH -> {
                    posZ += sin(sec * 1.5f * PI).toFloat() * 0.35f * amp
                    posY += cos(sec * 1.2f * PI).toFloat() * 0.04f * amp
                }
                OverallAnimationType.JITTER -> {
                    posX += sin(sec * 31f * PI).toFloat() * 0.025f * amp
                    posY += cos(sec * 29f * PI).toFloat() * 0.025f * amp
                    rotZ += sin(sec * 27f * PI).toFloat() * 2.5f * amp
                }
                OverallAnimationType.NONE -> Unit
            }
        }

        // Out Animation (16 presets)
        val outStart = (dur - anim.outDurationMs).coerceAtLeast(0L)
        if (anim.outType != OutAnimationType.NONE && anim.outDurationMs > 0 && tMs > outStart) {
            val rawP = ((tMs - outStart).toFloat() / anim.outDurationMs.toFloat()).coerceIn(0f, 1f)
            val p = easeInCubic(rawP) * anim.outIntensity
            val rem = (1f - p).coerceIn(0f, 1f)
            when (anim.outType) {
                OutAnimationType.FADE_OUT -> opacity *= rem
                OutAnimationType.ZOOM_OUT -> {
                    val s = (1f - 0.8f * p).coerceAtLeast(0.05f)
                    scaleX *= s; scaleY *= s; opacity *= rem
                }
                OutAnimationType.SLIDE_LEFT -> posX -= p * 1.2f
                OutAnimationType.SLIDE_RIGHT -> posX += p * 1.2f
                OutAnimationType.SLIDE_UP -> posY -= p * 1.2f
                OutAnimationType.SLIDE_DOWN -> posY += p * 1.2f
                OutAnimationType.ROTATE_OUT -> {
                    rotZ += p * 90f; opacity *= rem
                }
                OutAnimationType.SCALE_OUT -> {
                    scaleX *= rem.coerceAtLeast(0.01f); scaleY *= rem.coerceAtLeast(0.01f)
                }
                OutAnimationType.BLUR_OUT -> {
                    extraBlur += p * 0.8f; opacity *= rem
                }
                OutAnimationType.FLIP_3D_X_OUT -> {
                    rotX -= p * 95f; opacity *= rem
                }
                OutAnimationType.FLIP_3D_Y_OUT -> {
                    rotY += p * 95f; opacity *= rem
                }
                OutAnimationType.SWIRL_OUT -> {
                    rotZ += p * 240f
                    val s = rem.coerceAtLeast(0.05f)
                    scaleX *= s; scaleY *= s; opacity *= rem
                }
                OutAnimationType.DROP_3D_OUT -> {
                    posZ += p * 1.6f
                    val s = (1f - 0.65f * p).coerceAtLeast(0.08f)
                    scaleX *= s; scaleY *= s; opacity *= rem
                }
                OutAnimationType.GLITCH_OUT -> {
                    posX += sin(rawP * 52f * PI).toFloat() * p * 0.18f
                    opacity *= rem
                }
                OutAnimationType.SHUTTER_OUT -> {
                    scaleY *= rem.coerceAtLeast(0.02f)
                    opacity *= rem
                }
                OutAnimationType.NONE -> Unit
            }
        }

        val perspScale = computePerspectiveScale(posZ, scaleZ, fov)

        return EvaluatedClipState(
            posX = posX,
            posY = posY,
            posZ = posZ,
            scaleX = if (base.flipHorizontal) -abs(scaleX) else scaleX,
            scaleY = if (base.flipVertical) -abs(scaleY) else scaleY,
            scaleZ = scaleZ,
            rotationDeg = rotZ,
            rotationX = rotX,
            rotationY = rotY,
            opacity = opacity.coerceIn(0f, 1f),
            volume = volume.coerceIn(0f, 2f),
            extraBlur = extraBlur.coerceIn(0f, 1f),
            perspectiveFov = fov.coerceIn(20f, 120f),
            perspectiveScale = perspScale
        )
    }

    /**
     * Evaluates the active 3D Camera Rig clip at [timelineMs].
     */
    fun evaluateCamera3DAt(allClips: List<TimelineClip>, timelineMs: Long): EvaluatedCamera3D {
        val camClip = allClips.firstOrNull { clip ->
            clip.mediaType == MediaType.CAMERA_3D &&
                timelineMs >= clip.timelineStartMs &&
                timelineMs <= (clip.timelineStartMs + clip.durationMs)
        } ?: return EvaluatedCamera3D(isActive = false)

        val localMs = (timelineMs - camClip.timelineStartMs).coerceIn(0L, camClip.durationMs.coerceAtLeast(1L))
        val progress = (localMs.toFloat() / camClip.durationMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
        val camCfg = camClip.camera3d ?: Camera3DConfig()
        val sec = localMs / 1000f

        var cx = interpolateProperty(camClip.keyframes, KeyframeProperty.POS_X, localMs, camCfg.posX + camClip.transform.posX)
        var cy = interpolateProperty(camClip.keyframes, KeyframeProperty.POS_Y, localMs, camCfg.posY + camClip.transform.posY)
        var cz = interpolateProperty(camClip.keyframes, KeyframeProperty.POS_Z, localMs, camCfg.posZ + camClip.transform.posZ)
        var rx = interpolateProperty(camClip.keyframes, KeyframeProperty.ROTATION_X, localMs, camCfg.rotX + camClip.transform.rotationX)
        var ry = interpolateProperty(camClip.keyframes, KeyframeProperty.ROTATION_Y, localMs, camCfg.rotY + camClip.transform.rotationY)
        var rz = interpolateProperty(camClip.keyframes, KeyframeProperty.ROTATION, localMs, camCfg.rotZ + camClip.transform.rotation)
        val fov = interpolateProperty(camClip.keyframes, KeyframeProperty.CAMERA_FOV, localMs, camCfg.fovDeg)

        val amp = camCfg.motionIntensity
        val eased = easeInOutSine(progress)
        when (camCfg.preset) {
            CameraMotionPreset.STATIC -> Unit
            CameraMotionPreset.DOLLY_IN -> cz += eased * 0.85f * amp
            CameraMotionPreset.DOLLY_OUT -> cz -= eased * 0.85f * amp
            CameraMotionPreset.ORBIT_LEFT -> {
                val angle = eased * 24f * amp
                ry -= angle
                cx -= sin(angle * PI / 180.0).toFloat() * 0.45f * amp
            }
            CameraMotionPreset.ORBIT_RIGHT -> {
                val angle = eased * 24f * amp
                ry += angle
                cx += sin(angle * PI / 180.0).toFloat() * 0.45f * amp
            }
            CameraMotionPreset.CRANE_UP -> {
                cy -= eased * 0.45f * amp
                rx -= eased * 12f * amp
            }
            CameraMotionPreset.CRANE_DOWN -> {
                cy += eased * 0.45f * amp
                rx += eased * 12f * amp
            }
            CameraMotionPreset.HANDHELD_3D -> {
                cx += sin(sec * 2.1f * PI).toFloat() * 0.04f * amp
                cy += cos(sec * 1.7f * PI).toFloat() * 0.04f * amp
                rz += sin(sec * 1.3f * PI).toFloat() * 2.5f * amp
            }
            CameraMotionPreset.FLY_THROUGH -> {
                cz += eased * 1.3f * amp
                rx += sin(progress * PI).toFloat() * 10f * amp
                ry += sin(progress * 2f * PI).toFloat() * 12f * amp
            }
        }

        if (camCfg.handheldShake > 0.01f) {
            cx += sin(sec * 7.3f * PI).toFloat() * 0.03f * camCfg.handheldShake
            cy += cos(sec * 6.1f * PI).toFloat() * 0.03f * camCfg.handheldShake
            rz += sin(sec * 5.2f * PI).toFloat() * 2.2f * camCfg.handheldShake
        }

        return EvaluatedCamera3D(
            posX = cx,
            posY = cy,
            posZ = cz,
            rotX = rx,
            rotY = ry,
            rotZ = rz,
            fovDeg = fov.coerceIn(20f, 120f),
            depthParallax = camCfg.depthParallax.coerceIn(0f, 2.5f),
            isActive = true
        )
    }

    /**
     * Resolves full world-space 3D transform of [clip] by:
     * 1) Evaluating [clip]'s local animation & keyframes,
     * 2) Recursively inheriting any Null Object / parent layer transform chain (`parentClipId`),
     * 3) Applying the active `CAMERA_3D` rig view transform and depth parallax!
     */
    fun evaluateClipWorld3D(
        clip: TimelineClip,
        allClips: List<TimelineClip>,
        timelineMs: Long
    ): EvaluatedClipState {
        val localMs = (timelineMs - clip.timelineStartMs).coerceIn(0L, clip.durationMs.coerceAtLeast(1L))
        var state = evaluateClipAt(clip, localMs)

        // Resolve Null Object / parent chain (up to 8 levels to prevent cycles)
        val visited = HashSet<String>()
        visited.add(clip.id)
        var currentParentId = clip.transform.parentClipId
        var depth = 0
        while (!currentParentId.isNullOrBlank() && currentParentId !in visited && depth < 8) {
            visited.add(currentParentId)
            val parentClip = allClips.firstOrNull { it.id == currentParentId } ?: break
            val pLocalMs = (timelineMs - parentClip.timelineStartMs).coerceIn(0L, parentClip.durationMs.coerceAtLeast(1L))
            val pState = evaluateClipAt(parentClip, pLocalMs)

            // Rotate child offset by parent roll angle (Z) around parent center
            val rad = (pState.rotationDeg * PI / 180.0)
            val cosA = cos(rad).toFloat()
            val sinA = sin(rad).toFloat()
            val scaledX = state.posX * pState.scaleX
            val scaledY = state.posY * pState.scaleY
            val rotatedX = scaledX * cosA - scaledY * sinA
            val rotatedY = scaledX * sinA + scaledY * cosA

            state = state.copy(
                posX = pState.posX + rotatedX,
                posY = pState.posY + rotatedY,
                posZ = pState.posZ + state.posZ * pState.scaleZ,
                scaleX = state.scaleX * pState.scaleX,
                scaleY = state.scaleY * pState.scaleY,
                scaleZ = state.scaleZ * pState.scaleZ,
                rotationDeg = state.rotationDeg + pState.rotationDeg,
                rotationX = state.rotationX + pState.rotationX,
                rotationY = state.rotationY + pState.rotationY,
                opacity = (state.opacity * pState.opacity).coerceIn(0f, 1f)
            )
            currentParentId = parentClip.transform.parentClipId
            depth++
        }

        // Apply active 3D Camera inverse view transform + depth parallax
        val cam = evaluateCamera3DAt(allClips, timelineMs)
        if (cam.isActive && clip.mediaType != MediaType.CAMERA_3D) {
            val layerDepthFactor = (1f + state.posZ * 0.45f * cam.depthParallax).coerceIn(0.25f, 3.0f)
            val relX = state.posX - cam.posX / layerDepthFactor
            val relY = state.posY - cam.posY / layerDepthFactor
            val relZ = state.posZ - cam.posZ
            val fov = cam.fovDeg
            val perspScale = computePerspectiveScale(relZ, state.scaleZ, fov)
            state = state.copy(
                posX = relX,
                posY = relY,
                posZ = relZ,
                rotationX = state.rotationX - cam.rotX * 0.85f,
                rotationY = state.rotationY - cam.rotY * 0.85f,
                rotationDeg = state.rotationDeg - cam.rotZ,
                perspectiveFov = fov,
                perspectiveScale = perspScale
            )
        }

        return state
    }

    fun interpolateProperty(
        keyframes: List<Keyframe>,
        property: KeyframeProperty,
        tMs: Long,
        defaultValue: Float
    ): Float {
        val list = keyframes.filter { it.property == property }.sortedBy { it.timeOffsetMs }
        if (list.isEmpty()) return defaultValue
        if (tMs <= list.first().timeOffsetMs) return list.first().value
        if (tMs >= list.last().timeOffsetMs) return list.last().value

        for (i in 0 until list.size - 1) {
            val k0 = list[i]
            val k1 = list[i + 1]
            if (tMs in k0.timeOffsetMs..k1.timeOffsetMs) {
                val span = (k1.timeOffsetMs - k0.timeOffsetMs).coerceAtLeast(1L)
                val rawT = (tMs - k0.timeOffsetMs).toFloat() / span.toFloat()
                val eased = applyEasing(rawT, k0.easing, k0.bezierP1x, k0.bezierP1y, k0.bezierP2x, k0.bezierP2y)
                return k0.value + (k1.value - k0.value) * eased
            }
        }
        return defaultValue
    }

    fun applyEasing(
        t: Float,
        easing: EasingType,
        p1x: Float = 0.42f,
        p1y: Float = 0f,
        p2x: Float = 0.58f,
        p2y: Float = 1f
    ): Float {
        val x = t.coerceIn(0f, 1f)
        return when (easing) {
            EasingType.LINEAR -> x
            EasingType.EASE_IN -> x * x * x
            EasingType.EASE_OUT -> easeOutCubic(x)
            EasingType.EASE_IN_OUT -> if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f
            EasingType.CUBIC_BEZIER -> solveCubicBezierY(x, p1x, p1y, p2x, p2y)
        }
    }

    private fun solveCubicBezierY(xTarget: Float, p1x: Float, p1y: Float, p2x: Float, p2y: Float): Float {
        var u = xTarget
        for (i in 0 until 8) {
            val xEst = cubicBezier(u, p1x, p2x)
            val dx = cubicBezierDerivative(u, p1x, p2x)
            if (abs(dx) < 1e-5f) break
            u = (u - (xEst - xTarget) / dx).coerceIn(0f, 1f)
        }
        return cubicBezier(u, p1y, p2y).coerceIn(-0.5f, 1.5f)
    }

    private fun cubicBezier(t: Float, p1: Float, p2: Float): Float {
        val inv = 1f - t
        return 3f * inv * inv * t * p1 + 3f * inv * t * t * p2 + t * t * t
    }

    private fun cubicBezierDerivative(t: Float, p1: Float, p2: Float): Float {
        val inv = 1f - t
        return 3f * inv * inv * p1 + 6f * inv * t * (p2 - p1) + 3f * t * t * (1f - p2)
    }

    private fun easeInCubic(t: Float): Float = t * t * t
    private fun easeOutCubic(t: Float): Float = 1f - (1f - t).pow(3)
    private fun easeInOutSine(t: Float): Float = (-(cos(PI * t) - 1.0) / 2.0).toFloat()

    private fun easeOutBack(t: Float): Float {
        val c1 = 1.70158f
        val c3 = c1 + 1f
        return 1f + c3 * (t - 1f).pow(3) + c1 * (t - 1f).pow(2)
    }

    private fun easeOutElastic(t: Float): Float {
        if (t <= 0f) return 0f
        if (t >= 1f) return 1f
        val c4 = (2.0 * PI) / 3.0
        return (2.0.pow((-10.0 * t)) * sin((t * 10.0 - 0.75) * c4) + 1.0).toFloat()
    }

    private fun easeOutBounce(t: Float): Float {
        val n1 = 7.5625f
        val d1 = 2.75f
        var x = t
        return when {
            x < 1f / d1 -> n1 * x * x
            x < 2f / d1 -> {
                x -= 1.5f / d1; n1 * x * x + 0.75f
            }
            x < 2.5f / d1 -> {
                x -= 2.25f / d1; n1 * x * x + 0.9375f
            }
            else -> {
                x -= 2.625f / d1; n1 * x * x + 0.984375f
            }
        }
    }

    fun presetSpeedPoints(preset: SpeedCurvePreset): List<SpeedPoint> = when (preset) {
        SpeedCurvePreset.LINEAR -> listOf(SpeedPoint(0f, 1f), SpeedPoint(1f, 1f))
        SpeedCurvePreset.MONTAGE -> listOf(
            SpeedPoint(0f, 0.5f), SpeedPoint(0.25f, 1.8f), SpeedPoint(0.55f, 2.5f),
            SpeedPoint(0.8f, 0.7f), SpeedPoint(1f, 1.0f)
        )
        SpeedCurvePreset.HERO -> listOf(
            SpeedPoint(0f, 2.2f), SpeedPoint(0.35f, 2.2f), SpeedPoint(0.5f, 0.35f),
            SpeedPoint(0.75f, 0.35f), SpeedPoint(1f, 2.0f)
        )
        SpeedCurvePreset.BULLET -> listOf(
            SpeedPoint(0f, 3.0f), SpeedPoint(0.25f, 0.25f), SpeedPoint(0.7f, 0.25f), SpeedPoint(1f, 3.0f)
        )
        SpeedCurvePreset.JUMP_CUT -> listOf(
            SpeedPoint(0f, 1.0f), SpeedPoint(0.3f, 4.0f), SpeedPoint(0.5f, 1.0f),
            SpeedPoint(0.75f, 4.0f), SpeedPoint(1f, 1.0f)
        )
        SpeedCurvePreset.CUSTOM -> listOf(
            SpeedPoint(0f, 1f), SpeedPoint(0.33f, 1.5f), SpeedPoint(0.66f, 0.6f), SpeedPoint(1f, 1f)
        )
    }

    fun evaluateSpeedAt(config: SpeedCurveConfig, normalizedTime: Float): Float {
        if (config.preset == SpeedCurvePreset.LINEAR && config.points.size <= 2) {
            return config.constantSpeed.coerceIn(0.1f, 16f)
        }
        val pts = config.points.sortedBy { it.normalizedTime }
        if (pts.isEmpty()) return config.constantSpeed.coerceIn(0.1f, 16f)
        val t = normalizedTime.coerceIn(0f, 1f)
        if (t <= pts.first().normalizedTime) return pts.first().speedMultiplier
        if (t >= pts.last().normalizedTime) return pts.last().speedMultiplier
        for (i in 0 until pts.size - 1) {
            val a = pts[i]
            val b = pts[i + 1]
            if (t in a.normalizedTime..b.normalizedTime) {
                val span = (b.normalizedTime - a.normalizedTime).coerceAtLeast(0.0001f)
                val f = (t - a.normalizedTime) / span
                return (a.speedMultiplier + (b.speedMultiplier - a.speedMultiplier) * f).coerceIn(0.1f, 16f)
            }
        }
        return config.constantSpeed
    }
}
