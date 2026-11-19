package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.media.OfflineMediaProcessor
import com.example.core.media.OfflineVisionAndTracking
import com.example.core.model.AspectRatioOption
import com.example.core.model.BitratePreset
import com.example.core.model.Camera3DConfig
import com.example.core.model.CameraMotionPreset
import com.example.core.model.ClipAnimationConfig
import com.example.core.model.EasingType
import com.example.core.model.ExportConfig
import com.example.core.model.ExportFormat
import com.example.core.model.InAnimationType
import com.example.core.model.Keyframe
import com.example.core.model.KeyframeProperty
import com.example.core.model.MediaType
import com.example.core.model.OutAnimationType
import com.example.core.model.ResolutionPreset
import com.example.core.model.SpeedCurveConfig
import com.example.core.model.SpeedCurvePreset
import com.example.core.model.TimelineClip
import com.example.core.model.TimelineTrack
import com.example.core.model.TrackType
import com.example.core.model.TransformConfig
import com.example.core.model.ZuvyraProject
import com.example.core.storage.ProjectFileStore
import com.example.core.storage.ProjectRepository
import com.example.core.storage.ZuvyraDatabase
import com.example.engine.AnimationEvaluator
import com.example.engine.SpectralNoiseReducer
import com.example.engine.Zuvyra3DMatrixMath
import com.example.engine.ZuvyraExportEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ZuvyraCoreTest {

    @Test
    fun `animation evaluator interpolates keyframes and cubic bezier accurately`() {
        val clip = TimelineClip(
            trackId = "t1",
            mediaType = MediaType.IMAGE,
            displayName = "Test",
            timelineStartMs = 0L,
            durationMs = 2000L,
            keyframes = listOf(
                Keyframe(id = "k1", timeOffsetMs = 0L, property = KeyframeProperty.POS_X, value = -1.0f, easing = EasingType.LINEAR),
                Keyframe(id = "k2", timeOffsetMs = 1000L, property = KeyframeProperty.POS_X, value = 1.0f, easing = EasingType.CUBIC_BEZIER),
                Keyframe(id = "k3", timeOffsetMs = 2000L, property = KeyframeProperty.POS_X, value = 0.0f)
            )
        )

        val atStart = AnimationEvaluator.evaluateClipAt(clip, 0L)
        assertEquals(-1.0f, atStart.posX, 0.01f)

        val atMidLinear = AnimationEvaluator.evaluateClipAt(clip, 500L)
        assertEquals(0.0f, atMidLinear.posX, 0.01f)

        val atEnd = AnimationEvaluator.evaluateClipAt(clip, 2000L)
        assertEquals(0.0f, atEnd.posX, 0.01f)
    }

    @Test
    fun `3d keyframes and perspective scale compute depth accurately`() {
        val clip = TimelineClip(
            trackId = "t1",
            mediaType = MediaType.IMAGE,
            displayName = "3D Layer",
            timelineStartMs = 0L,
            durationMs = 2000L,
            keyframes = listOf(
                Keyframe(id = "kz1", timeOffsetMs = 0L, property = KeyframeProperty.POS_Z, value = -0.5f),
                Keyframe(id = "kz2", timeOffsetMs = 2000L, property = KeyframeProperty.POS_Z, value = 1.5f),
                Keyframe(id = "ksz1", timeOffsetMs = 0L, property = KeyframeProperty.SCALE_Z, value = 0.5f),
                Keyframe(id = "ksz2", timeOffsetMs = 2000L, property = KeyframeProperty.SCALE_Z, value = 1.5f),
                Keyframe(id = "rx1", timeOffsetMs = 0L, property = KeyframeProperty.ROTATION_X, value = 0f),
                Keyframe(id = "rx2", timeOffsetMs = 2000L, property = KeyframeProperty.ROTATION_X, value = 40f),
                Keyframe(id = "ry1", timeOffsetMs = 0L, property = KeyframeProperty.ROTATION_Y, value = -30f),
                Keyframe(id = "ry2", timeOffsetMs = 2000L, property = KeyframeProperty.ROTATION_Y, value = 30f)
            )
        )

        val mid = AnimationEvaluator.evaluateClipAt(clip, 1000L)
        assertEquals(0.5f, mid.posZ, 0.02f)
        assertEquals(1.0f, mid.scaleZ, 0.02f)
        assertEquals(20.0f, mid.rotationX, 0.2f)
        assertEquals(0.0f, mid.rotationY, 0.2f)

        val closer = AnimationEvaluator.computePerspectiveScale(posZ = -0.6f, scaleZ = 1.0f, fovDeg = 55f)
        val farther = AnimationEvaluator.computePerspectiveScale(posZ = 1.2f, scaleZ = 1.0f, fovDeg = 55f)
        assertTrue("Closer layer should project larger ($closer vs $farther)", closer > 1.15f && farther < 0.7f)
    }

    @Test
    fun `null object parenting and 3d camera rig compose world coordinates`() {
        val nullRig = TimelineClip(
            id = "null_1",
            trackId = "t_null",
            mediaType = MediaType.NULL_OBJECT,
            displayName = "Null Rig",
            timelineStartMs = 0L,
            durationMs = 4000L,
            transform = TransformConfig(posX = 0.4f, posY = -0.2f, posZ = 0.2f, scaleX = 1.5f, scaleY = 1.5f, rotation = 90f, rotationX = 12f)
        )
        val childLayer = TimelineClip(
            id = "child_1",
            trackId = "t_vid",
            mediaType = MediaType.IMAGE,
            displayName = "Child",
            timelineStartMs = 0L,
            durationMs = 4000L,
            transform = TransformConfig(posX = 0.2f, posY = 0f, scaleX = 1f, scaleY = 1f, parentClipId = "null_1")
        )
        val cameraRig = TimelineClip(
            id = "cam_1",
            trackId = "t_cam",
            mediaType = MediaType.CAMERA_3D,
            displayName = "3D Camera",
            timelineStartMs = 0L,
            durationMs = 4000L,
            camera3d = Camera3DConfig(preset = CameraMotionPreset.DOLLY_IN, motionIntensity = 1.0f, depthParallax = 1.0f)
        )

        val worldWithoutCam = AnimationEvaluator.evaluateClipWorld3D(childLayer, listOf(nullRig, childLayer), 2000L)
        // Child offset (0.2, 0) scaled by 1.5 and rotated 90deg -> (0, 0.3) added to parent (0.4, -0.2) = (0.4, 0.1)
        assertEquals(0.4f, worldWithoutCam.posX, 0.02f)
        assertEquals(0.1f, worldWithoutCam.posY, 0.02f)
        assertEquals(1.5f, worldWithoutCam.scaleX, 0.01f)
        assertEquals(90f, worldWithoutCam.rotationDeg, 0.1f)
        assertEquals(12f, worldWithoutCam.rotationX, 0.1f)

        val worldWithCamStart = AnimationEvaluator.evaluateClipWorld3D(childLayer, listOf(nullRig, childLayer, cameraRig), 0L)
        val worldWithCamEnd = AnimationEvaluator.evaluateClipWorld3D(childLayer, listOf(nullRig, childLayer, cameraRig), 4000L)
        assertTrue(
            "DOLLY_IN should move layers closer (smaller relative Z / higher perspectiveScale)",
            worldWithCamEnd.perspectiveScale > worldWithCamStart.perspectiveScale
        )
    }

    @Test
    fun `in and out animations modulate opacity cleanly`() {
        val clip = TimelineClip(
            trackId = "t1",
            mediaType = MediaType.TEXT,
            displayName = "Title",
            timelineStartMs = 0L,
            durationMs = 3000L,
            animation = ClipAnimationConfig(
                inType = InAnimationType.FADE_IN,
                inDurationMs = 500L,
                outType = OutAnimationType.FADE_OUT,
                outDurationMs = 500L
            )
        )

        val startState = AnimationEvaluator.evaluateClipAt(clip, 0L)
        assertEquals(0.0f, startState.opacity, 0.02f)

        val middleState = AnimationEvaluator.evaluateClipAt(clip, 1500L)
        assertEquals(1.0f, middleState.opacity, 0.01f)

        val endState = AnimationEvaluator.evaluateClipAt(clip, 3000L)
        assertEquals(0.0f, endState.opacity, 0.02f)
    }

    @Test
    fun `speed curve evaluator interpolates montage preset`() {
        val points = AnimationEvaluator.presetSpeedPoints(SpeedCurvePreset.MONTAGE)
        val cfg = SpeedCurveConfig(preset = SpeedCurvePreset.MONTAGE, points = points)
        assertEquals(0.5f, AnimationEvaluator.evaluateSpeedAt(cfg, 0f), 0.01f)
        assertEquals(2.5f, AnimationEvaluator.evaluateSpeedAt(cfg, 0.55f), 0.01f)
        assertEquals(1.0f, AnimationEvaluator.evaluateSpeedAt(cfg, 1.0f), 0.01f)
    }

    @Test
    fun `spectral noise reducer attenuates stationary noise while preserving tone`() {
        val sampleRate = 44100
        val count = 8192
        val rng = java.util.Random(42L)
        val pureNoise = FloatArray(count) {
            // Deterministic broadband stationary noise (-0.08..0.08)
            (rng.nextFloat() * 2f - 1f) * 0.08f
        }
        val reducer = SpectralNoiseReducer()
        val denoisedNoise = reducer.processBuffer(pureNoise, strength = 0.85f)

        fun rms(arr: FloatArray, from: Int): Double {
            var s = 0.0
            for (i in from until arr.size) s += arr[i] * arr[i]
            return sqrt(s / (arr.size - from).coerceAtLeast(1))
        }
        val inNoiseRms = rms(pureNoise, 2048)
        val outNoiseRms = rms(denoisedNoise, 2048)
        assertTrue("Expected stationary noise RMS to drop significantly (in=$inNoiseRms, out=$outNoiseRms)", outNoiseRms < inNoiseRms * 0.55)

        // Verify a strong 1kHz sine wave after warmup preserves significant energy
        val cleanTone = FloatArray(count) { i ->
            (sin(2.0 * PI * 1000.0 * i / sampleRate) * 0.6).toFloat()
        }
        val outTone = reducer.processBuffer(cleanTone, strength = 0.7f)
        assertTrue("Clean tone should retain strong energy", rms(outTone, 2048) > 0.25)
    }

    @Test
    fun `offline planar tracker detects shifted textured feature accurately`() {
        val w = 64
        val h = 64
        fun pattern(x: Int, y: Int): Float =
            ((sin(x * 0.45) * cosVal(y * 0.45)) * 0.45 + 0.5).toFloat()

        val prev = FloatArray(w * h)
        val curr = FloatArray(w * h)
        val shiftX = 4
        val shiftY = -3
        for (y in 0 until h) {
            for (x in 0 until w) {
                prev[y * w + x] = pattern(x, y)
                val sx = (x - shiftX).coerceIn(0, w - 1)
                val sy = (y - shiftY).coerceIn(0, h - 1)
                curr[y * w + x] = pattern(sx, sy)
            }
        }
        val (dx, dy, conf) = OfflineVisionAndTracking.trackLumaPatchDelta(
            prevLuma = prev,
            currLuma = curr,
            width = w,
            height = h,
            centerX = 32,
            centerY = 32,
            patchRadius = 6,
            searchRadius = 10
        )
        assertTrue("Tracker should detect dx ~ $shiftX (got $dx)", abs(dx - shiftX) < 0.75f)
        assertTrue("Tracker should detect dy ~ $shiftY (got $dy)", abs(dy - shiftY) < 0.75f)
        assertTrue("Confidence should be high ($conf)", conf > 0.5f)
    }

    private fun cosVal(v: Double): Double = kotlin.math.cos(v)

    @Test
    fun `offline saliency matting separates centered object from uniform border`() {
        val w = 32
        val h = 32
        val pixels = IntArray(w * h) { android.graphics.Color.rgb(20, 180, 40) } // uniform green background
        for (y in 10..21) {
            for (x in 10..21) {
                pixels[y * w + x] = android.graphics.Color.rgb(230, 40, 40) // distinct centered red subject
            }
        }
        val mask = OfflineVisionAndTracking.computeSaliencyMatteMask(pixels, w, h, edgeRefineStrength = 0.6f)
        val centerAlpha = mask[16 * w + 16]
        val cornerAlpha = mask[0]
        assertTrue("Center subject should have high alpha ($centerAlpha)", centerAlpha > 0.8f)
        assertTrue("Corner background should have low alpha ($cornerAlpha)", cornerAlpha < 0.2f)
    }

    @Test
    fun `3d projective matrix builder returns identity at zero pose and skews under 3d tilt`() {
        val id = Zuvyra3DMatrixMath.buildProjectiveMatrix(
            tx = 0f, ty = 0f, tz = 0f,
            sx = 1f, sy = 1f, sz = 1f,
            rotXDeg = 0f, rotYDeg = 0f, rotZDeg = 0f,
            anchorOffsetX = 0f, anchorOffsetY = 0f, fovDeg = 55f
        )
        assertEquals(1f, id[0], 0.01f)
        assertEquals(1f, id[4], 0.01f)
        assertEquals(0f, id[6], 0.001f)
        assertEquals(0f, id[7], 0.001f)
        assertEquals(1f, id[8], 0.01f)

        val tilted = Zuvyra3DMatrixMath.buildProjectiveMatrix(
            tx = 0.1f, ty = -0.1f, tz = 0.3f,
            sx = 1f, sy = 1f, sz = 1f,
            rotXDeg = 30f, rotYDeg = 25f, rotZDeg = 0f,
            anchorOffsetX = 0f, anchorOffsetY = 0f, fovDeg = 55f
        )
        assertTrue("Projective skew components must be non-zero under 3D pitch/yaw", abs(tilted[6]) > 0.05f && abs(tilted[7]) > 0.05f)
    }

    @Test
    fun `computeDimensions computes even pixel bounds for standard ratios`() {
        val (w16_9, h16_9) = ProjectRepository.computeDimensions(AspectRatioOption.RATIO_16_9, ResolutionPreset.P1080)
        assertEquals(1920, w16_9)
        assertEquals(1080, h16_9)

        val (w9_16, h9_16) = ProjectRepository.computeDimensions(AspectRatioOption.RATIO_9_16, ResolutionPreset.P1080)
        assertEquals(1080, w9_16)
        assertEquals(1920, h9_16)

        val (w4k, h4k) = ProjectRepository.computeDimensions(AspectRatioOption.RATIO_16_9, ResolutionPreset.P2160)
        assertEquals(3840, w4k)
        assertEquals(2160, h4k)
    }

    @Test
    fun `parseCubeText parses valid 3D LUT cube data`() {
        val cubeSample = """
            # Test LUT
            TITLE "Cinematic Teal"
            LUT_3D_SIZE 2
            0.0 0.0 0.0
            1.0 0.0 0.0
            0.0 1.0 0.0
            1.0 1.0 0.0
            0.0 0.0 1.0
            1.0 0.0 1.0
            0.0 1.0 1.0
            1.0 1.0 1.0
        """.trimIndent()

        val parsed = OfflineMediaProcessor.parseCubeText(cubeSample)
        assertNotNull(parsed)
        assertEquals("Cinematic Teal", parsed!!.title)
        assertEquals(2, parsed.size)
        assertEquals(24, parsed.data.size)
    }

    @Test
    fun `project repository saves, auto-recovers, exports zip package, and estimates export size`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ZuvyraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val fileStore = ProjectFileStore(context)
        val repo = ProjectRepository(context, db, fileStore)

        val proj = repo.createNewProject("Robolectric Edit", AspectRatioOption.RATIO_16_9, ResolutionPreset.P1080, 30)
        val withClip = proj.copy(
            tracks = listOf(
                TimelineTrack(
                    id = "t1",
                    name = "Video 1",
                    type = TrackType.VIDEO,
                    order = 0,
                    clips = listOf(
                        TimelineClip(
                            trackId = "t1",
                            mediaType = MediaType.TEXT,
                            displayName = "Intro",
                            timelineStartMs = 0L,
                            durationMs = 10_000L
                        )
                    )
                )
            )
        )
        repo.saveAutoRecovery(withClip)
        val recovery = repo.getLatestRecoveryProject()
        assertNotNull(recovery)

        val loadedRecovery = repo.loadProject(proj.id, preferRecovery = true)
        assertNotNull(loadedRecovery)
        assertEquals(10_000L, loadedRecovery!!.computedDurationMs())

        val pkgZip = repo.exportLocalPackage(loadedRecovery)
        assertTrue(pkgZip.exists() && pkgZip.length() > 0L)

        val exportEngine = ZuvyraExportEngine(context, repo)
        val estBytes = exportEngine.estimateFileSizeBytes(
            loadedRecovery,
            ExportConfig(format = ExportFormat.MP4, resolution = ResolutionPreset.P1080, bitratePreset = BitratePreset.HIGH)
        )
        assertTrue(estBytes > 1_000_000L)

        repo.deleteProject(proj.id)
        db.close()
    }
}
