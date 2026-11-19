package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlendMode
import android.graphics.BlurMaskFilter
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.core.model.BlendModeType
import com.example.core.model.HandwritingData
import com.example.core.model.MediaType
import com.example.core.model.StickerCategory
import com.example.core.model.StickerOverlayData
import com.example.core.model.StickerShapeKind
import com.example.core.model.TextAlignment
import com.example.core.model.TextEffectType
import com.example.core.model.TextOverlayData
import com.example.core.model.ZuvyraProject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Renders Text, Stickers, Handwriting layers, and Layer Borders/Shadows onto an Android Canvas
 * with 3D perspective transforms, Null Object parenting, 3D Camera motion, Z-depth sorting,
 * and BlendMode compositing so Preview and Export match identically.
 */
object OverlayCanvasRenderer {

    fun toAndroidBlendMode(type: BlendModeType): BlendMode? = when (type) {
        BlendModeType.NORMAL -> null
        BlendModeType.MULTIPLY -> BlendMode.MULTIPLY
        BlendModeType.SCREEN -> BlendMode.SCREEN
        BlendModeType.OVERLAY -> BlendMode.OVERLAY
        BlendModeType.DARKEN -> BlendMode.DARKEN
        BlendModeType.LIGHTEN -> BlendMode.LIGHTEN
        BlendModeType.ADD, BlendModeType.LINEAR_DODGE -> BlendMode.PLUS
        BlendModeType.COLOR_DODGE -> BlendMode.COLOR_DODGE
        BlendModeType.COLOR_BURN -> BlendMode.COLOR_BURN
        BlendModeType.DIFFERENCE -> BlendMode.DIFFERENCE
        BlendModeType.EXCLUSION -> BlendMode.EXCLUSION
        BlendModeType.SOFT_LIGHT -> BlendMode.SOFT_LIGHT
        BlendModeType.HARD_LIGHT, BlendModeType.PIN_LIGHT -> BlendMode.HARD_LIGHT
        BlendModeType.SUBTRACT -> BlendMode.DIFFERENCE
    }

    fun loadTypeface(context: Context, fontName: String, customFontPath: String?, bold: Boolean, italic: Boolean): Typeface {
        val style = when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        val base = when {
            !customFontPath.isNullOrBlank() -> {
                runCatching { Typeface.createFromFile(customFontPath) }.getOrNull()
            }
            fontName.equals("Space Grotesk", ignoreCase = true) -> {
                runCatching { ResourcesCompat.getFont(context, R.font.space_grotesk) }.getOrNull()
            }
            fontName.equals("Plus Jakarta Sans", ignoreCase = true) -> {
                runCatching { ResourcesCompat.getFont(context, R.font.plus_jakarta_sans) }.getOrNull()
            }
            fontName.equals("JetBrains Mono", ignoreCase = true) -> {
                runCatching { ResourcesCompat.getFont(context, R.font.jetbrains_mono) }.getOrNull()
            }
            fontName.equals("Serif", ignoreCase = true) -> Typeface.SERIF
            fontName.equals("Monospace", ignoreCase = true) -> Typeface.MONOSPACE
            fontName.equals("Sans Serif", ignoreCase = true) -> Typeface.SANS_SERIF
            else -> Typeface.DEFAULT
        } ?: Typeface.DEFAULT
        return Typeface.create(base, style)
    }

    fun renderAllOverlaysBitmap(
        context: Context,
        project: ZuvyraProject,
        timelineMs: Long,
        width: Int,
        height: Int
    ): Bitmap {
        val bmp = Bitmap.createBitmap(width.coerceAtLeast(16), height.coerceAtLeast(16), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        renderAllOverlaysOnCanvas(context, canvas, project, timelineMs, width.toFloat(), height.toFloat())
        return bmp
    }

    fun renderAllOverlaysOnCanvas(
        context: Context,
        canvas: Canvas,
        project: ZuvyraProject,
        timelineMs: Long,
        canvasW: Float,
        canvasH: Float
    ) {
        val allClips = project.tracks.flatMap { it.clips }
        val activeOverlayClips = project.tracks
            .filter { it.isVisible }
            .sortedBy { it.order }
            .flatMap { track ->
                track.clips.mapNotNull { clip ->
                    if (clip.mediaType in setOf(MediaType.TEXT, MediaType.STICKER, MediaType.HANDWRITING) &&
                        timelineMs >= clip.timelineStartMs &&
                        timelineMs <= (clip.timelineStartMs + clip.durationMs)
                    ) {
                        val state = AnimationEvaluator.evaluateClipWorld3D(clip, allClips, timelineMs)
                        Triple(track.order, clip, state)
                    } else null
                }
            }
            // Depth-sort back-to-front by Z coordinate (higher posZ is farther away, rendered first)
            .sortedWith(compareByDescending<Triple<Int, com.example.core.model.TimelineClip, EvaluatedClipState>> { it.third.posZ }.thenBy { it.first })

        val cam = Camera()
        val matrix3d = Matrix()

        for ((_, clip, state) in activeOverlayClips) {
            val localMs = timelineMs - clip.timelineStartMs
            if (state.opacity <= 0.01f) continue

            val androidBlend = toAndroidBlendMode(clip.layerStyle.blendMode)
            val saveLayerId = if (androidBlend != null) {
                val blendPaint = Paint().apply { blendMode = androidBlend }
                canvas.saveLayer(0f, 0f, canvasW, canvasH, blendPaint)
            } else {
                canvas.save()
            }

            val cx = canvasW * 0.5f + state.posX * (canvasW * 0.5f)
            val cy = canvasH * 0.5f + state.posY * (canvasH * 0.5f)
            canvas.translate(cx, cy)

            val has3dTilt = kotlin.math.abs(state.rotationX) > 0.1f || kotlin.math.abs(state.rotationY) > 0.1f
            if (has3dTilt) {
                cam.save()
                val camZ = -8f * (55f / state.perspectiveFov.coerceIn(20f, 120f))
                cam.setLocation(0f, 0f, camZ)
                cam.rotateX(state.rotationX)
                cam.rotateY(-state.rotationY)
                cam.rotateZ(-state.rotationDeg)
                cam.getMatrix(matrix3d)
                cam.restore()
                canvas.concat(matrix3d)
            } else {
                canvas.rotate(state.rotationDeg)
            }

            val pScale = state.perspectiveScale
            canvas.scale(state.scaleX * pScale, state.scaleY * pScale)

            when (clip.mediaType) {
                MediaType.TEXT -> clip.textData?.let {
                    drawTextLayer(context, canvas, it, localMs, state.opacity, canvasW, canvasH)
                }
                MediaType.STICKER -> clip.stickerData?.let {
                    drawStickerLayer(context, canvas, it, state.opacity, canvasW, canvasH)
                }
                MediaType.HANDWRITING -> clip.handwritingData?.let {
                    drawHandwritingLayer(canvas, it, localMs, clip.durationMs, state.opacity, canvasW, canvasH)
                }
                else -> Unit
            }
            canvas.restoreToCount(saveLayerId)
        }
    }

    private fun drawTextLayer(
        context: Context,
        canvas: Canvas,
        data: TextOverlayData,
        localMs: Long,
        layerOpacity: Float,
        canvasW: Float,
        canvasH: Float
    ) {
        val scaleFactor = min(canvasW, canvasH) / 400f
        val effDur = data.textEffectDurationMs.coerceAtLeast(100L)
        val effP = (localMs.toFloat() / effDur.toFloat()).coerceIn(0f, 1f)

        var displayText = data.text
        var effectAlpha = 1f
        var effectScale = 1f
        var effectOffsetY = 0f

        when (data.textEffect) {
            TextEffectType.FADE -> effectAlpha = effP
            TextEffectType.TYPEWRITER -> {
                val count = (data.text.length * effP).toInt().coerceIn(0, data.text.length)
                displayText = data.text.substring(0, count)
            }
            TextEffectType.POP -> {
                effectScale = if (effP < 0.7f) (effP / 0.7f) * 1.15f else 1.15f - 0.15f * ((effP - 0.7f) / 0.3f)
            }
            TextEffectType.BOUNCE -> {
                effectOffsetY = -sin(effP * PI).toFloat() * 28f * scaleFactor * (1f - effP)
            }
            TextEffectType.SLIDE -> {
                effectOffsetY = (1f - effP) * 50f * scaleFactor
                effectAlpha = effP
            }
            TextEffectType.ZOOM -> {
                effectScale = 0.4f + 0.6f * effP
                effectAlpha = effP
            }
            TextEffectType.REVEAL -> {
                effectAlpha = effP
                effectScale = 0.92f + 0.08f * effP
            }
            TextEffectType.NONE -> Unit
        }

        if (displayText.isEmpty()) return

        val finalAlpha = (data.textOpacity * layerOpacity * effectAlpha).coerceIn(0f, 1f)
        val typeface = loadTypeface(context, data.fontFamilyName, data.customFontPath, data.isBold, data.isItalic)
        val textPx = data.fontSizeSp * scaleFactor

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = textPx
            this.typeface = typeface
            letterSpacing = data.letterSpacingEm
            isUnderlineText = data.isUnderline
            alpha = (finalAlpha * 255).toInt()
            color = data.colorArgb.toInt()
        }

        val maxLayoutW = (canvasW * 0.86f).toInt().coerceAtLeast(100)
        val layoutAlign = when (data.alignment) {
            TextAlignment.LEFT -> Layout.Alignment.ALIGN_NORMAL
            TextAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
            TextAlignment.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
        }

        val measuredLines = displayText.split("\n").maxOfOrNull { textPaint.measureText(it) } ?: 0f
        val layoutW = (measuredLines.toInt() + (24 * scaleFactor).toInt()).coerceIn(60, maxLayoutW)

        val layout = StaticLayout.Builder.obtain(displayText, 0, displayText.length, textPaint, layoutW)
            .setAlignment(layoutAlign)
            .setLineSpacing(0f, data.lineHeightMultiplier)
            .build()

        val boxW = layout.width.toFloat()
        val boxH = layout.height.toFloat()
        val pad = data.backgroundPaddingDp * scaleFactor

        canvas.save()
        canvas.translate(0f, effectOffsetY)
        canvas.scale(effectScale, effectScale)
        canvas.translate(-boxW * 0.5f, -boxH * 0.5f)

        // Background box & border
        if (data.backgroundOpacity > 0.01f || data.borderWidth > 0.2f) {
            val rect = RectF(-pad, -pad, boxW + pad, boxH + pad)
            val rad = data.backgroundCornerRadius * scaleFactor
            if (data.backgroundOpacity > 0.01f) {
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = data.backgroundColorArgb.toInt()
                    alpha = (data.backgroundOpacity * finalAlpha * 255).toInt()
                    style = Paint.Style.FILL
                }
                canvas.drawRoundRect(rect, rad, rad, bgPaint)
            }
            if (data.borderWidth > 0.2f) {
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = data.borderColorArgb.toInt()
                    alpha = (finalAlpha * 255).toInt()
                    style = Paint.Style.STROKE
                    strokeWidth = data.borderWidth * scaleFactor
                }
                canvas.drawRoundRect(rect, rad, rad, borderPaint)
            }
        }

        // Glow pass
        if (data.glowRadius > 0.5f) {
            val glowPaint = TextPaint(textPaint).apply {
                shader = null
                color = data.glowColorArgb.toInt()
                alpha = (finalAlpha * 200).toInt()
                maskFilter = BlurMaskFilter(data.glowRadius * scaleFactor, BlurMaskFilter.Blur.OUTER)
            }
            StaticLayout.Builder.obtain(displayText, 0, displayText.length, glowPaint, layoutW)
                .setAlignment(layoutAlign)
                .setLineSpacing(0f, data.lineHeightMultiplier)
                .build()
                .draw(canvas)
        }

        // Stroke pass
        if (data.strokeWidth > 0.2f) {
            val strokePaint = TextPaint(textPaint).apply {
                shader = null
                style = Paint.Style.STROKE
                strokeWidth = data.strokeWidth * scaleFactor * 2f
                strokeJoin = Paint.Join.ROUND
                color = data.strokeColorArgb.toInt()
                alpha = (finalAlpha * 255).toInt()
            }
            StaticLayout.Builder.obtain(displayText, 0, displayText.length, strokePaint, layoutW)
                .setAlignment(layoutAlign)
                .setLineSpacing(0f, data.lineHeightMultiplier)
                .build()
                .draw(canvas)
        }

        // Shadow + Fill pass
        if (data.shadowBlur > 0.1f) {
            textPaint.setShadowLayer(
                data.shadowBlur * scaleFactor,
                data.shadowDx * scaleFactor,
                data.shadowDy * scaleFactor,
                data.shadowColorArgb.toInt()
            )
        }
        if (data.useGradient) {
            textPaint.shader = LinearGradient(
                0f, 0f, boxW, boxH,
                data.gradientStartArgb.toInt(),
                data.gradientEndArgb.toInt(),
                Shader.TileMode.CLAMP
            )
        }
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawStickerLayer(
        context: Context,
        canvas: Canvas,
        data: StickerOverlayData,
        opacity: Float,
        canvasW: Float,
        canvasH: Float
    ) {
        val baseSize = min(canvasW, canvasH) * 0.28f
        val half = baseSize * 0.5f
        val alphaInt = (opacity * 255).toInt().coerceIn(0, 255)

        if (data.category == StickerCategory.EMOJI) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = baseSize * 0.8f
                textAlign = Paint.Align.CENTER
                alpha = alphaInt
            }
            val fm = paint.fontMetrics
            val yOff = -(fm.ascent + fm.descent) * 0.5f
            canvas.drawText(data.emoji, 0f, yOff, paint)
            return
        }

        if (data.category == StickerCategory.CUSTOM_IMAGE && !data.customImageUri.isNullOrBlank()) {
            val uri = Uri.parse(data.customImageUri)
            val bmp = runCatching {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
            if (bmp != null) {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { alpha = alphaInt }
                val aspect = bmp.width.toFloat() / bmp.height.coerceAtLeast(1).toFloat()
                val drawW = if (aspect >= 1f) baseSize else baseSize * aspect
                val drawH = if (aspect >= 1f) baseSize / aspect else baseSize
                canvas.drawBitmap(bmp, null, RectF(-drawW * 0.5f, -drawH * 0.5f, drawW * 0.5f, drawH * 0.5f), paint)
                bmp.recycle()
                return
            }
        }

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = LinearGradient(-half, -half, half, half, data.primaryColorArgb.toInt(), data.secondaryColorArgb.toInt(), Shader.TileMode.CLAMP)
            alpha = alphaInt
            if (data.shadowBlur > 0.5f) {
                setShadowLayer(data.shadowBlur, 2f, 4f, data.shadowColorArgb.toInt())
            }
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = data.strokeColorArgb.toInt()
            strokeWidth = data.strokeWidth * (min(canvasW, canvasH) / 400f)
            alpha = alphaInt
        }

        when (data.shapeKind) {
            StickerShapeKind.CIRCLE -> {
                canvas.drawCircle(0f, 0f, half, fillPaint)
                if (data.strokeWidth > 0f) canvas.drawCircle(0f, 0f, half, strokePaint)
            }
            StickerShapeKind.RECTANGLE -> {
                val r = RectF(-half, -half * 0.7f, half, half * 0.7f)
                canvas.drawRect(r, fillPaint)
                if (data.strokeWidth > 0f) canvas.drawRect(r, strokePaint)
            }
            StickerShapeKind.ROUNDED_RECT -> {
                val r = RectF(-half, -half * 0.7f, half, half * 0.7f)
                canvas.drawRoundRect(r, half * 0.25f, half * 0.25f, fillPaint)
                if (data.strokeWidth > 0f) canvas.drawRoundRect(r, half * 0.25f, half * 0.25f, strokePaint)
            }
            StickerShapeKind.STAR -> {
                val path = buildStarPath(5, half, half * 0.45f)
                canvas.drawPath(path, fillPaint)
                if (data.strokeWidth > 0f) canvas.drawPath(path, strokePaint)
            }
            StickerShapeKind.ARROW_RIGHT -> drawArrow(canvas, half, 0f, fillPaint, strokePaint, data.strokeWidth > 0f)
            StickerShapeKind.ARROW_LEFT -> drawArrow(canvas, half, 180f, fillPaint, strokePaint, data.strokeWidth > 0f)
            StickerShapeKind.ARROW_UP -> drawArrow(canvas, half, -90f, fillPaint, strokePaint, data.strokeWidth > 0f)
            StickerShapeKind.LINE -> {
                val linePaint = Paint(fillPaint).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = (data.strokeWidth.coerceAtLeast(4f)) * (min(canvasW, canvasH) / 400f)
                    strokeCap = Paint.Cap.ROUND
                }
                canvas.drawLine(-half, 0f, half, 0f, linePaint)
            }
            StickerShapeKind.SPEECH_BUBBLE -> {
                val path = Path().apply {
                    val r = RectF(-half, -half * 0.7f, half, half * 0.45f)
                    addRoundRect(r, half * 0.22f, half * 0.22f, Path.Direction.CW)
                    moveTo(-half * 0.3f, half * 0.45f)
                    lineTo(-half * 0.55f, half * 0.85f)
                    lineTo(half * 0.05f, half * 0.45f)
                    close()
                }
                canvas.drawPath(path, fillPaint)
                if (data.strokeWidth > 0f) canvas.drawPath(path, strokePaint)
            }
            StickerShapeKind.SPARKLE_RING -> {
                val ring = Paint(strokePaint).apply {
                    strokeWidth = half * 0.14f
                    color = data.primaryColorArgb.toInt()
                }
                canvas.drawCircle(0f, 0f, half * 0.85f, ring)
                val star = buildStarPath(4, half * 0.55f, half * 0.18f)
                canvas.drawPath(star, fillPaint)
            }
            StickerShapeKind.FRAME_CORNER -> {
                val p = Paint(strokePaint).apply {
                    strokeWidth = half * 0.16f
                    strokeCap = Paint.Cap.SQUARE
                    color = data.primaryColorArgb.toInt()
                }
                val path = Path().apply {
                    moveTo(-half, -half * 0.3f); lineTo(-half, -half); lineTo(-half * 0.3f, -half)
                    moveTo(half * 0.3f, -half); lineTo(half, -half); lineTo(half, -half * 0.3f)
                    moveTo(-half, half * 0.3f); lineTo(-half, half); lineTo(-half * 0.3f, half)
                    moveTo(half * 0.3f, half); lineTo(half, half); lineTo(half, half * 0.3f)
                }
                canvas.drawPath(path, p)
            }
        }
    }

    private fun drawArrow(
        canvas: Canvas,
        half: Float,
        angleDeg: Float,
        fillPaint: Paint,
        strokePaint: Paint,
        hasStroke: Boolean
    ) {
        canvas.save()
        canvas.rotate(angleDeg)
        val path = Path().apply {
            moveTo(-half, -half * 0.25f)
            lineTo(half * 0.2f, -half * 0.25f)
            lineTo(half * 0.2f, -half * 0.65f)
            lineTo(half, 0f)
            lineTo(half * 0.2f, half * 0.65f)
            lineTo(half * 0.2f, half * 0.25f)
            lineTo(-half, half * 0.25f)
            close()
        }
        canvas.drawPath(path, fillPaint)
        if (hasStroke) canvas.drawPath(path, strokePaint)
        canvas.restore()
    }

    private fun buildStarPath(points: Int, outerR: Float, innerR: Float): Path {
        val path = Path()
        val step = PI / points
        var angle = -PI / 2.0
        for (i in 0 until points * 2) {
            val r = if (i % 2 == 0) outerR else innerR
            val x = (cos(angle) * r).toFloat()
            val y = (sin(angle) * r).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            angle += step
        }
        path.close()
        return path
    }

    private fun drawHandwritingLayer(
        canvas: Canvas,
        data: HandwritingData,
        localMs: Long,
        durationMs: Long,
        opacity: Float,
        canvasW: Float,
        canvasH: Float
    ) {
        val totalStrokes = data.strokes.size
        if (totalStrokes == 0) return

        val progress = if (data.animateStrokes && durationMs > 0) {
            (localMs.toFloat() / (durationMs * 0.7f).coerceAtLeast(300f)).coerceIn(0f, 1f)
        } else 1f

        val scale = min(canvasW, canvasH) / 400f
        canvas.save()
        canvas.translate(-canvasW * 0.5f, -canvasH * 0.5f)

        val maxPointsTotal = data.strokes.sumOf { it.points.size }
        var drawnPointsBudget = (maxPointsTotal * progress).toInt().coerceAtLeast(1)

        for (stroke in data.strokes) {
            if (stroke.points.size < 2 || drawnPointsBudget <= 0) continue
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                strokeWidth = stroke.brushSize * scale
                color = if (stroke.isEraser) 0xFF000000.toInt() else stroke.colorArgb.toInt()
                alpha = ((if (stroke.isEraser) 0.4f else stroke.opacity) * opacity * 255).toInt().coerceIn(0, 255)
            }
            val limit = min(stroke.points.size, drawnPointsBudget)
            val path = Path()
            val first = stroke.points[0]
            path.moveTo(first.x * canvasW, first.y * canvasH)
            for (i in 1 until limit) {
                val prev = stroke.points[i - 1]
                val curr = stroke.points[i]
                if (stroke.smoothing > 0.1f) {
                    val midX = ((prev.x + curr.x) * 0.5f) * canvasW
                    val midY = ((prev.y + curr.y) * 0.5f) * canvasH
                    path.quadTo(prev.x * canvasW, prev.y * canvasH, midX, midY)
                } else {
                    path.lineTo(curr.x * canvasW, curr.y * canvasH)
                }
            }
            canvas.drawPath(path, paint)
            drawnPointsBudget -= limit
        }
        canvas.restore()
    }
}
