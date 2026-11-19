package com.example.core.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class TrackType {
    VIDEO,
    IMAGE,
    TEXT,
    STICKER,
    HANDWRITING,
    AUDIO,
    NULL_OBJECT,
    CAMERA_3D
}

@Serializable
enum class MediaType {
    VIDEO,
    IMAGE,
    AUDIO,
    TEXT,
    STICKER,
    HANDWRITING,
    NULL_OBJECT,
    CAMERA_3D
}

@Serializable
enum class FitMode {
    FIT,
    FILL,
    STRETCH
}

@Serializable
enum class KeyframeProperty {
    POS_X,
    POS_Y,
    POS_Z,
    SCALE_X,
    SCALE_Y,
    SCALE_Z,
    ROTATION,
    ROTATION_X,
    ROTATION_Y,
    OPACITY,
    VOLUME,
    CAMERA_FOV
}

@Serializable
enum class EasingType {
    LINEAR,
    EASE_IN,
    EASE_OUT,
    EASE_IN_OUT,
    CUBIC_BEZIER
}

@Serializable
data class Keyframe(
    val id: String = UUID.randomUUID().toString(),
    val timeOffsetMs: Long,
    val property: KeyframeProperty,
    val value: Float,
    val easing: EasingType = EasingType.EASE_IN_OUT,
    val bezierP1x: Float = 0.42f,
    val bezierP1y: Float = 0f,
    val bezierP2x: Float = 0.58f,
    val bezierP2y: Float = 1f
)

@Serializable
enum class InAnimationType {
    NONE,
    FADE_IN,
    ZOOM_IN,
    SLIDE_LEFT,
    SLIDE_RIGHT,
    SLIDE_UP,
    SLIDE_DOWN,
    ROTATE_IN,
    POP_IN,
    SCALE_IN,
    BOUNCE_IN,
    BLUR_IN,
    FLIP_3D_X,
    FLIP_3D_Y,
    SWING_IN,
    SWIRL_IN,
    ELASTIC_IN,
    DROP_3D,
    GLITCH_IN,
    WIPE_IN
}

@Serializable
enum class OverallAnimationType {
    NONE,
    PULSE,
    FLOATING,
    BREATHING,
    WOBBLE,
    SHAKE,
    CONTINUOUS_ROTATION,
    ZOOM_LOOP,
    GENTLE_MOVEMENT,
    ORBIT_3D,
    PENDULUM_3D,
    HEARTBEAT,
    FLICKER,
    FLOAT_3D_DEPTH,
    JITTER
}

@Serializable
enum class OutAnimationType {
    NONE,
    FADE_OUT,
    ZOOM_OUT,
    SLIDE_LEFT,
    SLIDE_RIGHT,
    SLIDE_UP,
    SLIDE_DOWN,
    ROTATE_OUT,
    SCALE_OUT,
    BLUR_OUT,
    FLIP_3D_X_OUT,
    FLIP_3D_Y_OUT,
    SWIRL_OUT,
    DROP_3D_OUT,
    GLITCH_OUT,
    SHUTTER_OUT
}

@Serializable
data class ClipAnimationConfig(
    val inType: InAnimationType = InAnimationType.NONE,
    val inDurationMs: Long = 500L,
    val inIntensity: Float = 1f,
    val overallType: OverallAnimationType = OverallAnimationType.NONE,
    val overallSpeed: Float = 1f,
    val overallIntensity: Float = 0.5f,
    val outType: OutAnimationType = OutAnimationType.NONE,
    val outDurationMs: Long = 500L,
    val outIntensity: Float = 1f
)

@Serializable
enum class SpeedCurvePreset {
    LINEAR,
    MONTAGE,
    HERO,
    BULLET,
    JUMP_CUT,
    CUSTOM
}

@Serializable
data class SpeedPoint(
    val normalizedTime: Float, // 0f..1f
    val speedMultiplier: Float // 0.1f..16f
)

@Serializable
data class SpeedCurveConfig(
    val preset: SpeedCurvePreset = SpeedCurvePreset.LINEAR,
    val constantSpeed: Float = 1f,
    val points: List<SpeedPoint> = listOf(SpeedPoint(0f, 1f), SpeedPoint(1f, 1f)),
    val preservePitch: Boolean = true,
    val muteAudioOnSpeedChange: Boolean = false
)

@Serializable
data class TransformConfig(
    val posX: Float = 0f, // Normalized -1f..1f relative to canvas
    val posY: Float = 0f,
    val posZ: Float = 0f, // 3D depth Z-axis (-3f..3f; negative = closer, positive = farther)
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val scaleZ: Float = 1f, // 3D depth Z-scale / perspective stretch (0.1f..4f)
    val rotation: Float = 0f, // Z-axis Roll degrees
    val rotationX: Float = 0f, // 3D Pitch degrees (-180..180)
    val rotationY: Float = 0f, // 3D Yaw degrees (-180..180)
    val anchorX: Float = 0.5f, // 0f..1f
    val anchorY: Float = 0.5f,
    val widthRatio: Float = 1f,
    val heightRatio: Float = 1f,
    val opacity: Float = 1f, // 0f..1f
    val cropLeft: Float = 0f, // 0f..0.49f
    val cropTop: Float = 0f,
    val cropRight: Float = 0f,
    val cropBottom: Float = 0f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val fitMode: FitMode = FitMode.FIT,
    val is3dLayer: Boolean = false,
    val perspectiveFov: Float = 55f,
    val parentClipId: String? = null // Null Object / Layer Parenting ID
)

@Serializable
enum class BlendModeType {
    NORMAL,
    MULTIPLY,
    SCREEN,
    OVERLAY,
    DARKEN,
    LIGHTEN,
    ADD,
    LINEAR_DODGE,
    COLOR_DODGE,
    COLOR_BURN,
    DIFFERENCE,
    EXCLUSION,
    SOFT_LIGHT,
    HARD_LIGHT,
    PIN_LIGHT,
    SUBTRACT
}

@Serializable
data class LayerStyleConfig(
    val blendMode: BlendModeType = BlendModeType.NORMAL,
    val strokeWidth: Float = 0f,
    val strokeColorArgb: Long = 0xFFFFFFFF,
    val strokeOpacity: Float = 1f,
    val innerOutline: Boolean = false,
    val borderThickness: Float = 0f,
    val borderColorArgb: Long = 0xFF8B5CF6,
    val borderOpacity: Float = 1f,
    val borderCornerRadius: Float = 0f,
    val shadowColorArgb: Long = 0xFF000000,
    val shadowOpacity: Float = 0.65f,
    val shadowBlur: Float = 0f,
    val shadowDistance: Float = 0f,
    val shadowAngleDeg: Float = 45f,
    val shadowOffsetX: Float = 0f,
    val shadowOffsetY: Float = 0f,
    val glowColorArgb: Long = 0xFF8B5CF6,
    val glowIntensity: Float = 0f,
    val glowRadius: Float = 12f,
    val glowOpacity: Float = 0.8f
)

@Serializable
enum class HslColorChannel {
    RED,
    ORANGE,
    YELLOW,
    GREEN,
    CYAN,
    BLUE,
    PURPLE,
    MAGENTA
}

@Serializable
data class HslAdjustment(
    val hueShift: Float = 0f, // -1f..1f
    val saturation: Float = 0f, // -1f..1f
    val luminance: Float = 0f // -1f..1f
)

@Serializable
enum class BuiltInFilterType {
    NONE,
    CINEMATIC,
    WARM,
    COOL,
    VINTAGE,
    RETRO,
    BLACK_AND_WHITE,
    PORTRAIT,
    LANDSCAPE,
    FILM,
    SOFT,
    DRAMATIC
}

@Serializable
data class ColorAdjustments(
    val brightness: Float = 0f, // -1f..1f
    val contrast: Float = 0f, // -1f..1f
    val saturation: Float = 0f, // -1f..1f
    val exposure: Float = 0f, // -1f..1f
    val temperature: Float = 0f, // -1f..1f
    val tint: Float = 0f, // -1f..1f
    val highlights: Float = 0f, // -1f..1f
    val shadows: Float = 0f, // -1f..1f
    val whites: Float = 0f, // -1f..1f
    val blacks: Float = 0f, // -1f..1f
    val fade: Float = 0f, // 0f..1f
    val sharpness: Float = 0f, // 0f..1f
    val vignette: Float = 0f, // 0f..1f
    val hslChannels: Map<HslColorChannel, HslAdjustment> = emptyMap(),
    val filterType: BuiltInFilterType = BuiltInFilterType.NONE,
    val filterIntensity: Float = 1f,
    val customLutPath: String? = null
)

@Serializable
data class ChromaKeyConfig(
    val enabled: Boolean = false,
    val keyColorArgb: Long = 0xFF00D838,
    val similarity: Float = 0.30f, // 0.02f..0.85f
    val smoothness: Float = 0.14f, // 0.0f..0.5f
    val spillSuppression: Float = 0.50f, // 0f..1f
    val edgeChoke: Float = 0.0f // -0.4f..0.4f
)

@Serializable
enum class AlphaKeyMode {
    LUMA_KEY,
    DARK_KEY,
    ALPHA_MATTE,
    ALPHA_INVERT
}

@Serializable
data class AlphaKeyConfig(
    val enabled: Boolean = false,
    val mode: AlphaKeyMode = AlphaKeyMode.LUMA_KEY,
    val threshold: Float = 0.18f, // 0f..1f
    val feather: Float = 0.12f, // 0f..0.6f
    val invert: Boolean = false
)

@Serializable
enum class BgRemovalMode {
    OFFLINE_PORTRAIT_ML,
    SALIENCY_AUTO,
    EDGE_MATTING
}

@Serializable
data class BgRemovalConfig(
    val enabled: Boolean = false,
    val mode: BgRemovalMode = BgRemovalMode.OFFLINE_PORTRAIT_ML,
    val edgeFeather: Float = 0.25f, // 0f..1f
    val edgeRefineStrength: Float = 0.55f, // 0f..1f
    val softHaloRemoval: Float = 0.35f, // 0f..1f
    val maskPreviewOverlay: Boolean = false
)

@Serializable
enum class CameraMotionPreset {
    STATIC,
    DOLLY_IN,
    DOLLY_OUT,
    ORBIT_LEFT,
    ORBIT_RIGHT,
    CRANE_UP,
    CRANE_DOWN,
    HANDHELD_3D,
    FLY_THROUGH
}

@Serializable
data class Camera3DConfig(
    val preset: CameraMotionPreset = CameraMotionPreset.STATIC,
    val posX: Float = 0f, // -2f..2f
    val posY: Float = 0f, // -2f..2f
    val posZ: Float = 0f, // -2.5f..2.5f
    val rotX: Float = 0f, // Pitch -60..60
    val rotY: Float = 0f, // Yaw -60..60
    val rotZ: Float = 0f, // Roll -180..180
    val fovDeg: Float = 55f, // 25..110
    val depthParallax: Float = 1.0f, // 0f..2.5f
    val motionIntensity: Float = 0.6f, // 0f..2f
    val handheldShake: Float = 0.0f // 0f..1f
)

@Serializable
enum class VideoEffectType {
    GAUSSIAN_BLUR,
    MOTION_BLUR,
    DIRECTIONAL_BLUR,
    GLOW,
    SHARPEN,
    VIGNETTE,
    FILM_GRAIN,
    NOISE,
    PIXELATE,
    MOSAIC,
    RGB_SPLIT,
    CHROMATIC_ABERRATION,
    LIGHT_LEAK,
    GLOW_BLOOM,
    BASIC_DISTORTION,
    FADE,
    SOFT_FOCUS,
    MIRROR,
    FLIP,
    ZOOM_BLUR,
    BASIC_SHAKE
}

@Serializable
data class VideoEffectItem(
    val id: String = UUID.randomUUID().toString(),
    val type: VideoEffectType,
    val intensity: Float = 0.5f, // 0f..1f
    val secondaryParam: Float = 0.5f, // e.g., direction angle / grain size
    val enabled: Boolean = true,
    val startOffsetMs: Long = 0L,
    val endOffsetMs: Long = Long.MAX_VALUE
)

@Serializable
enum class TransitionType {
    NONE,
    FADE,
    DISSOLVE,
    CROSSFADE,
    ZOOM_IN,
    ZOOM_OUT,
    SLIDE_LEFT,
    SLIDE_RIGHT,
    SLIDE_UP,
    SLIDE_DOWN,
    WIPE,
    PUSH,
    BLUR_TRANSITION,
    SPIN,
    FLASH,
    SIMPLE_GLITCH,
    DIP_TO_BLACK,
    DIP_TO_WHITE
}

@Serializable
enum class TransitionDirection {
    LEFT,
    RIGHT,
    UP,
    DOWN
}

@Serializable
data class ClipTransition(
    val id: String = UUID.randomUUID().toString(),
    val fromClipId: String,
    val toClipId: String,
    val type: TransitionType = TransitionType.NONE,
    val durationMs: Long = 600L,
    val intensity: Float = 1f,
    val direction: TransitionDirection = TransitionDirection.LEFT
)

@Serializable
enum class TextAlignment {
    LEFT,
    CENTER,
    RIGHT
}

@Serializable
enum class TextEffectType {
    NONE,
    FADE,
    TYPEWRITER,
    POP,
    BOUNCE,
    SLIDE,
    ZOOM,
    REVEAL
}

@Serializable
data class TextOverlayData(
    val text: String = "ZUVYRA Title",
    val fontFamilyName: String = "Space Grotesk",
    val customFontPath: String? = null,
    val fontSizeSp: Float = 36f,
    val colorArgb: Long = 0xFFFFFFFF,
    val useGradient: Boolean = false,
    val gradientStartArgb: Long = 0xFFA78BFA,
    val gradientEndArgb: Long = 0xFF38BDF8,
    val isBold: Boolean = true,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val alignment: TextAlignment = TextAlignment.CENTER,
    val letterSpacingEm: Float = 0.02f,
    val lineHeightMultiplier: Float = 1.2f,
    val textOpacity: Float = 1f,
    val strokeColorArgb: Long = 0xFF000000,
    val strokeWidth: Float = 2f,
    val shadowColorArgb: Long = 0x99000000,
    val shadowBlur: Float = 6f,
    val shadowDx: Float = 2f,
    val shadowDy: Float = 4f,
    val glowColorArgb: Long = 0xFF8B5CF6,
    val glowRadius: Float = 0f,
    val backgroundColorArgb: Long = 0xFF12141E,
    val backgroundOpacity: Float = 0f,
    val backgroundCornerRadius: Float = 12f,
    val backgroundPaddingDp: Float = 12f,
    val borderWidth: Float = 0f,
    val borderColorArgb: Long = 0xFFFFFFFF,
    val textEffect: TextEffectType = TextEffectType.NONE,
    val textEffectDurationMs: Long = 700L
)

@Serializable
data class DrawingPoint(
    val x: Float, // Normalized 0..1
    val y: Float
)

@Serializable
data class HandwritingStroke(
    val id: String = UUID.randomUUID().toString(),
    val points: List<DrawingPoint>,
    val colorArgb: Long = 0xFF38BDF8,
    val brushSize: Float = 8f,
    val opacity: Float = 1f,
    val isEraser: Boolean = false,
    val smoothing: Float = 0.6f
)

@Serializable
data class HandwritingData(
    val strokes: List<HandwritingStroke> = emptyList(),
    val animateStrokes: Boolean = false
)

@Serializable
enum class StickerCategory {
    SHAPE,
    ARROW,
    DECORATIVE,
    EMOJI,
    CUSTOM_IMAGE
}

@Serializable
enum class StickerShapeKind {
    CIRCLE,
    RECTANGLE,
    ROUNDED_RECT,
    STAR,
    ARROW_RIGHT,
    ARROW_LEFT,
    ARROW_UP,
    LINE,
    SPEECH_BUBBLE,
    SPARKLE_RING,
    FRAME_CORNER
}

@Serializable
data class StickerOverlayData(
    val category: StickerCategory = StickerCategory.SHAPE,
    val shapeKind: StickerShapeKind = StickerShapeKind.STAR,
    val emoji: String = "✨",
    val customImageUri: String? = null,
    val primaryColorArgb: Long = 0xFF8B5CF6,
    val secondaryColorArgb: Long = 0xFF38BDF8,
    val strokeColorArgb: Long = 0xFFFFFFFF,
    val strokeWidth: Float = 2f,
    val shadowBlur: Float = 4f,
    val shadowColorArgb: Long = 0x88000000
)

@Serializable
data class VolumeEnvelopePoint(
    val timeOffsetMs: Long,
    val volume: Float // 0f..2f
)

@Serializable
enum class EqPreset {
    FLAT,
    BASS_BOOST,
    VOCAL_CLARITY,
    TREBLE_BOOST,
    CINEMATIC_WARM,
    PODCAST,
    CUSTOM
}

@Serializable
data class EqSettings(
    val preset: EqPreset = EqPreset.FLAT,
    val bassDb: Float = 0f, // -12..12
    val midDb: Float = 0f,
    val trebleDb: Float = 0f,
    val lowPassHz: Float = 20000f, // 500..20000
    val highPassHz: Float = 20f // 20..2000
)

@Serializable
data class AudioEffectSettings(
    val reverbMix: Float = 0f, // 0f..1f
    val echoDelayMs: Int = 0, // 0..600
    val echoFeedback: Float = 0f, // 0f..0.7f
    val noiseReductionStrength: Float = 0f, // Advanced STFT Spectral Subtraction + Gate 0f..1f
    val spectralDenoiseEnabled: Boolean = true,
    val humFilterHz: Int = 0, // 0 (off), 50, or 60 Hz mains hum notch
    val voicePresenceBoost: Float = 0f, // 0f..1f speech band clarity
    val pitchSemitones: Float = 0f // -12f..12f
)

@Serializable
data class ClipAudioSettings(
    val volume: Float = 1f, // 0f..2f
    val isMuted: Boolean = false,
    val pan: Float = 0f, // -1f..1f
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L,
    val envelopePoints: List<VolumeEnvelopePoint> = emptyList(),
    val eqSettings: EqSettings = EqSettings(),
    val audioEffects: AudioEffectSettings = AudioEffectSettings()
)

@Serializable
data class TimelineClip(
    val id: String = UUID.randomUUID().toString(),
    val trackId: String,
    val mediaUri: String? = null,
    val mediaType: MediaType,
    val displayName: String,
    val timelineStartMs: Long,
    val durationMs: Long,
    val sourceStartMs: Long = 0L,
    val sourceEndMs: Long = durationMs,
    val sourceDurationMs: Long = durationMs,
    val isReversed: Boolean = false,
    val reversedCachePath: String? = null,
    val maskedMediaCachePath: String? = null, // Cached cutout PNG/MP4 from offline BG removal
    val isFreezeFrame: Boolean = false,
    val audioDetached: Boolean = false,
    val speedConfig: SpeedCurveConfig = SpeedCurveConfig(),
    val transform: TransformConfig = TransformConfig(),
    val keyframes: List<Keyframe> = emptyList(),
    val animation: ClipAnimationConfig = ClipAnimationConfig(),
    val layerStyle: LayerStyleConfig = LayerStyleConfig(),
    val colorAdjustments: ColorAdjustments = ColorAdjustments(),
    val chromaKey: ChromaKeyConfig = ChromaKeyConfig(),
    val alphaKey: AlphaKeyConfig = AlphaKeyConfig(),
    val bgRemoval: BgRemovalConfig = BgRemovalConfig(),
    val camera3d: Camera3DConfig? = null,
    val videoEffects: List<VideoEffectItem> = emptyList(),
    val audioSettings: ClipAudioSettings = ClipAudioSettings(),
    val textData: TextOverlayData? = null,
    val stickerData: StickerOverlayData? = null,
    val handwritingData: HandwritingData? = null,
    val groupId: String? = null,
    val waveformPeaks: List<Float> = emptyList()
)

@Serializable
data class TimelineTrack(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: TrackType,
    val order: Int,
    val isVisible: Boolean = true,
    val isLocked: Boolean = false,
    val isMuted: Boolean = false,
    val isSolo: Boolean = false,
    val volume: Float = 1f,
    val pan: Float = 0f,
    val eqSettings: EqSettings = EqSettings(),
    val clips: List<TimelineClip> = emptyList()
)

@Serializable
enum class AspectRatioOption(val label: String, val ratioWidth: Int, val ratioHeight: Int) {
    RATIO_16_9("16:9", 16, 9),
    RATIO_9_16("9:16", 9, 16),
    RATIO_1_1("1:1", 1, 1),
    RATIO_4_5("4:5", 4, 5),
    RATIO_5_4("5:4", 5, 4),
    RATIO_4_3("4:3", 4, 3),
    RATIO_3_4("3:4", 3, 4),
    RATIO_21_9("21:9", 21, 9),
    CUSTOM("Custom", 16, 9)
}

@Serializable
enum class ResolutionPreset(val label: String, val shortSidePx: Int) {
    P360("360p", 360),
    P480("480p", 480),
    P720("720p HD", 720),
    P1080("1080p FHD", 1080),
    P1440("1440p QHD", 1440),
    P2160("2160p 4K", 2160),
    ORIGINAL("Original", 1080)
}

@Serializable
enum class ExportFormat(val label: String, val extension: String) {
    MP4("MP4", "mp4"),
    MOV("MOV", "mov"),
    GIF("GIF", "gif"),
    IMAGE_SEQUENCE("Image Sequence (PNG)", "zip")
}

@Serializable
enum class VideoCodecOption(val label: String, val mimeType: String) {
    H264("H.264 / AVC", "video/avc"),
    H265("H.265 / HEVC", "video/hevc"),
    VP9("VP9", "video/x-vnd.on2.vp9")
}

@Serializable
enum class BitratePreset(val label: String, val multiplier: Float) {
    LOW("Low", 0.5f),
    MEDIUM("Medium", 1.0f),
    HIGH("High", 1.8f),
    VERY_HIGH("Very High", 2.8f),
    CUSTOM("Custom", 1.0f)
}

@Serializable
data class ExportConfig(
    val format: ExportFormat = ExportFormat.MP4,
    val resolution: ResolutionPreset = ResolutionPreset.P1080,
    val aspectRatio: AspectRatioOption = AspectRatioOption.RATIO_16_9,
    val customWidth: Int = 1920,
    val customHeight: Int = 1080,
    val fps: Int = 30,
    val codec: VideoCodecOption = VideoCodecOption.H264,
    val bitratePreset: BitratePreset = BitratePreset.HIGH,
    val customBitrateKbps: Int = 12000,
    val audioBitrateKbps: Int = 192,
    val audioSampleRateHz: Int = 48000,
    val exportToGallery: Boolean = true
)

@Serializable
data class ZuvyraProject(
    val schemaVersion: Int = 1,
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val width: Int = 1920,
    val height: Int = 1080,
    val fps: Int = 30,
    val aspectRatio: AspectRatioOption = AspectRatioOption.RATIO_16_9,
    val tracks: List<TimelineTrack> = listOf(
        TimelineTrack(id = "track_v1", name = "Video 1", type = TrackType.VIDEO, order = 0),
        TimelineTrack(id = "track_o1", name = "Overlay / Text", type = TrackType.TEXT, order = 1),
        TimelineTrack(id = "track_a1", name = "Audio 1", type = TrackType.AUDIO, order = 2)
    ),
    val transitions: List<ClipTransition> = emptyList(),
    val exportConfig: ExportConfig = ExportConfig(),
    val thumbnailPath: String? = null
) {
    fun computedDurationMs(): Long {
        val maxEnd = tracks.flatMap { it.clips }.maxOfOrNull { it.timelineStartMs + it.durationMs } ?: 0L
        return maxEnd
    }
}
