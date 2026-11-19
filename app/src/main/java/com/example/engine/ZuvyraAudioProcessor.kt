package com.example.engine

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.example.core.model.ClipAudioSettings
import com.example.core.model.EqSettings
import com.example.core.model.Keyframe
import com.example.core.model.KeyframeProperty
import com.example.core.model.VolumeEnvelopePoint
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure-Kotlin 512-point STFT (50% Overlap-Add Hann Window) Spectral Subtraction + Wiener Noise Remover.
 * Tracks per-bin noise power floor adaptively via Minimum Statistics (no cloud AI or internet required).
 */
class SpectralNoiseReducer(
    private val fftSize: Int = 512
) {
    private val hopSize = fftSize / 2
    private val binCount = fftSize / 2 + 1
    private val hannWindow = DoubleArray(fftSize) { i ->
        sqrt(0.5 * (1.0 - cos(2.0 * PI * i / fftSize)))
    }

    // Ring & accumulation buffers (zero-allocation per frame)
    private val inputRing = DoubleArray(fftSize)
    private val outputAccum = DoubleArray(fftSize)
    private val fftReal = DoubleArray(fftSize)
    private val fftImag = DoubleArray(fftSize)
    private val framePower = DoubleArray(binCount)
    private val smoothedPower = DoubleArray(binCount)
    private val noiseFloor = DoubleArray(binCount) { 1e-6 }
    private val prevGain = DoubleArray(binCount) { 1.0 }

    private var hopCounter = 0
    private var framesInitialized = 0

    // Precomputed twiddle tables for fast Radix-2 FFT
    private val cosTable = DoubleArray(fftSize / 2) { k -> cos(-2.0 * PI * k / fftSize) }
    private val sinTable = DoubleArray(fftSize / 2) { k -> sin(-2.0 * PI * k / fftSize) }

    fun reset() {
        inputRing.fill(0.0)
        outputAccum.fill(0.0)
        framePower.fill(0.0)
        smoothedPower.fill(0.0)
        noiseFloor.fill(1e-6)
        prevGain.fill(1.0)
        hopCounter = 0
        framesInitialized = 0
    }

    fun processSample(sample: Double, strength: Float): Double {
        System.arraycopy(inputRing, 1, inputRing, 0, fftSize - 1)
        inputRing[fftSize - 1] = sample

        val out = outputAccum[hopCounter]
        outputAccum[hopCounter] = 0.0
        hopCounter++

        if (hopCounter >= hopSize) {
            hopCounter = 0
            System.arraycopy(outputAccum, hopSize, outputAccum, 0, hopSize)
            for (i in hopSize until fftSize) outputAccum[i] = 0.0
            processHop(strength.coerceIn(0f, 1f))
        }
        return out
    }

    /**
     * Convenience batch processor for unit testing & offline diagnostics.
     */
    fun processBuffer(samples: FloatArray, strength: Float): FloatArray {
        reset()
        val out = FloatArray(samples.size)
        for (i in samples.indices) {
            out[i] = processSample(samples[i].toDouble(), strength).toFloat()
        }
        return out
    }

    private fun processHop(strength: Float) {
        for (i in 0 until fftSize) {
            fftReal[i] = inputRing[i] * hannWindow[i]
            fftImag[i] = 0.0
        }

        fftInPlace(fftReal, fftImag, inverse = false)

        val alphaOverSub = 1.4 + strength * 2.8
        val spectralFloor = (0.12 - strength * 0.10).coerceIn(0.015, 0.2)
        framesInitialized++

        for (k in 0 until binCount) {
            val re = fftReal[k]
            val im = fftImag[k]
            val p = re * re + im * im
            framePower[k] = p
            smoothedPower[k] = if (framesInitialized == 1) p else smoothedPower[k] * 0.65 + p * 0.35
        }

        val localWin = DoubleArray(13)
        for (k in 0 until binCount) {
            val re = fftReal[k]
            val im = fftImag[k]
            val p = framePower[k]
            val sp = smoothedPower[k]

            // Estimate local spectral median across neighboring bins so broadband noise
            // tracks accurately while sharp tonal/vocal harmonics remain untouched.
            var winCount = 0
            val kLo = max(0, k - 6)
            val kHi = min(binCount - 1, k + 6)
            for (nk in kLo..kHi) {
                localWin[winCount++] = smoothedPower[nk]
            }
            java.util.Arrays.sort(localWin, 0, winCount)
            val neighborMedian = localWin[winCount / 2]
            val tonalCeiling = neighborMedian * 4.5 + 1e-4

            val boundedSamplePow = min(sp, tonalCeiling)
            noiseFloor[k] = if (framesInitialized <= 4) {
                (noiseFloor[k] * (framesInitialized - 1) + boundedSamplePow) / framesInitialized
            } else if (boundedSamplePow < noiseFloor[k]) {
                noiseFloor[k] * 0.85 + boundedSamplePow * 0.15
            } else {
                noiseFloor[k] * 0.90 + boundedSamplePow * 0.10
            }

            // Spectral subtraction + Wiener gain using smoothed power & adaptive noise floor
            val nPow = max(noiseFloor[k], 1e-10)
            val estPow = max(p, sp * 0.7)
            val cleanPow = max(0.0, estPow - alphaOverSub * nPow)
            val wienerGain = if (estPow > 1e-12) {
                val g = cleanPow / (cleanPow + nPow)
                max(spectralFloor, g)
            } else {
                spectralFloor
            }

            // Temporal gain smoothing suppresses musical noise artifacts
            val smoothedGain = prevGain[k] * 0.4 + wienerGain * 0.6
            prevGain[k] = smoothedGain

            fftReal[k] = re * smoothedGain
            fftImag[k] = im * smoothedGain

            if (k in 1 until (fftSize / 2)) {
                val mirror = fftSize - k
                fftReal[mirror] = fftReal[mirror] * smoothedGain
                fftImag[mirror] = fftImag[mirror] * smoothedGain
            }
        }

        fftInPlace(fftReal, fftImag, inverse = true)

        // Exact COLA synthesis with sqrt-Hann (sqrt(hann)^2 sums to 1.0 at 50% overlap)
        for (i in 0 until fftSize) {
            outputAccum[i] += fftReal[i] * hannWindow[i]
        }
    }

    private fun fftInPlace(real: DoubleArray, imag: DoubleArray, inverse: Boolean) {
        val n = fftSize
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while ((j and bit) != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
        }

        var len = 2
        while (len <= n) {
            val halfLen = len shr 1
            val step = n / len
            for (i in 0 until n step len) {
                var idx = 0
                for (k in 0 until halfLen) {
                    val wr = cosTable[idx]
                    val wi = if (inverse) -sinTable[idx] else sinTable[idx]
                    val evenIdx = i + k
                    val oddIdx = evenIdx + halfLen
                    val tr = wr * real[oddIdx] - wi * imag[oddIdx]
                    val ti = wr * imag[oddIdx] + wi * real[oddIdx]
                    real[oddIdx] = real[evenIdx] - tr
                    imag[oddIdx] = imag[evenIdx] - ti
                    real[evenIdx] += tr
                    imag[evenIdx] += ti
                    idx += step
                }
            }
            len = len shl 1
        }

        if (inverse) {
            val invN = 1.0 / n.toDouble()
            for (i in 0 until n) {
                real[i] *= invN
                imag[i] *= invN
            }
        }
    }
}

/**
 * Real-time & export PCM AudioProcessor implementing:
 * - Advanced Offline STFT Spectral Noise Remover + Adaptive Minimum-Statistics Noise Floor
 * - 50/60 Hz Mains Hum Biquad Notch + Speech Clarity Presence Peaking Filter
 * - Master volume, mute, stereo pan, fade in/out, volume envelope automation & keyframes
 * - 3-band EQ (Bass/Mid/Treble) + Low-Pass & High-Pass biquad filters
 * - Echo/Delay circular buffer & lightweight Schroeder Reverb
 */
@OptIn(UnstableApi::class)
class ZuvyraAudioProcessor(
    private val clipDurationMs: Long,
    private val trackVolume: Float,
    private val trackMuted: Boolean,
    private val trackPan: Float,
    private val clipAudio: ClipAudioSettings,
    private val keyframes: List<Keyframe> = emptyList()
) : BaseAudioProcessor() {

    private var sampleRate = 48000
    private var channelCount = 2
    private var processedSamples = 0L

    // Biquad filter states per channel (max 8 channels)
    private val lowShelfState = Array(8) { BiquadState() }
    private val midPeakState = Array(8) { BiquadState() }
    private val highShelfState = Array(8) { BiquadState() }
    private val highPassState = Array(8) { BiquadState() }
    private val lowPassState = Array(8) { BiquadState() }
    private val humNotchState = Array(8) { BiquadState() }
    private val presencePeakState = Array(8) { BiquadState() }

    private var lowShelfCoeff = BiquadCoeff()
    private var midPeakCoeff = BiquadCoeff()
    private var highShelfCoeff = BiquadCoeff()
    private var highPassCoeff = BiquadCoeff()
    private var lowPassCoeff = BiquadCoeff()
    private var humNotchCoeff = BiquadCoeff()
    private var presencePeakCoeff = BiquadCoeff()

    // Per-channel STFT Spectral Noise Reducers
    private val spectralReducers = Array(8) { SpectralNoiseReducer(512) }

    // Delay / Reverb buffers
    private var delayBuffer = FloatArray(0)
    private var delayWritePos = 0
    private var reverbBuffer1 = FloatArray(0)
    private var reverbBuffer2 = FloatArray(0)
    private var reverbPos1 = 0
    private var reverbPos2 = 0

    private class BiquadState {
        var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0
        fun reset() { x1 = 0.0; x2 = 0.0; y1 = 0.0; y2 = 0.0 }
    }

    private data class BiquadCoeff(
        val b0: Double = 1.0, val b1: Double = 0.0, val b2: Double = 0.0,
        val a1: Double = 0.0, val a2: Double = 0.0
    ) {
        fun process(x: Double, s: BiquadState): Double {
            val y = b0 * x + b1 * s.x1 + b2 * s.x2 - a1 * s.y1 - a2 * s.y2
            s.x2 = s.x1; s.x1 = x
            s.y2 = s.y1; s.y1 = y
            return y
        }
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            return AudioFormat.NOT_SET
        }
        sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(8000)
        channelCount = inputAudioFormat.channelCount.coerceIn(1, 8)
        recomputeFilters(clipAudio.eqSettings)
        val maxDelaySamples = (sampleRate * 0.75 * channelCount).toInt().coerceAtLeast(channelCount)
        delayBuffer = FloatArray(maxDelaySamples)
        reverbBuffer1 = FloatArray((sampleRate * 0.037 * channelCount).toInt().coerceAtLeast(channelCount))
        reverbBuffer2 = FloatArray((sampleRate * 0.053 * channelCount).toInt().coerceAtLeast(channelCount))
        return inputAudioFormat
    }

    override fun onFlush() {
        processedSamples = 0L
        delayWritePos = 0
        reverbPos1 = 0
        reverbPos2 = 0
        delayBuffer.fill(0f)
        reverbBuffer1.fill(0f)
        reverbBuffer2.fill(0f)
        lowShelfState.forEach { it.reset() }
        midPeakState.forEach { it.reset() }
        highShelfState.forEach { it.reset() }
        highPassState.forEach { it.reset() }
        lowPassState.forEach { it.reset() }
        humNotchState.forEach { it.reset() }
        presencePeakState.forEach { it.reset() }
        spectralReducers.forEach { it.reset() }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val outputBuffer = replaceOutputBuffer(remaining)
        val eq = clipAudio.eqSettings
        val fx = clipAudio.audioEffects
        val hasEq = abs(eq.bassDb) > 0.1f || abs(eq.midDb) > 0.1f || abs(eq.trebleDb) > 0.1f ||
            eq.highPassHz > 30f || eq.lowPassHz < 19000f
        val delaySamples = ((fx.echoDelayMs / 1000f) * sampleRate).toInt() * channelCount
        val hasDelay = fx.echoDelayMs > 10 && fx.echoFeedback > 0.01f && delaySamples in 1 until delayBuffer.size
        val hasReverb = fx.reverbMix > 0.01f && reverbBuffer1.isNotEmpty()
        val useSpectral = fx.noiseReductionStrength > 0.02f && fx.spectralDenoiseEnabled
        val gateThresh = if (!fx.spectralDenoiseEnabled) fx.noiseReductionStrength * 0.04f else 0f
        val hasHumNotch = fx.humFilterHz == 50 || fx.humFilterHz == 60
        val hasVoicePresence = fx.voicePresenceBoost > 0.02f

        val combinedPan = (clipAudio.pan + trackPan).coerceIn(-1f, 1f)
        val leftPanGain = if (combinedPan > 0f) 1f - combinedPan else 1f
        val rightPanGain = if (combinedPan < 0f) 1f + combinedPan else 1f

        val frameBytes = channelCount * 2
        val frameCount = remaining / frameBytes

        for (f in 0 until frameCount) {
            val currentTimeMs = (processedSamples * 1000L) / sampleRate
            val envGain = evaluateVolumeAt(currentTimeMs)
            val totalGain = if (trackMuted || clipAudio.isMuted) 0f else trackVolume * envGain

            for (ch in 0 until channelCount) {
                val rawShort = inputBuffer.getShort()
                var sample = rawShort / 32768.0

                if (totalGain <= 0.0001f) {
                    outputBuffer.putShort(0)
                    continue
                }

                // 50/60 Hz Mains Hum Notch Filter
                if (hasHumNotch) {
                    sample = humNotchCoeff.process(sample, humNotchState[ch])
                }

                // Advanced Offline STFT Spectral Noise Remover or Lightweight Gate
                if (useSpectral) {
                    sample = spectralReducers[ch].processSample(sample, fx.noiseReductionStrength)
                } else if (gateThresh > 0f && abs(sample) < gateThresh) {
                    val ratio = (abs(sample) / gateThresh).coerceIn(0.0, 1.0)
                    sample *= ratio * ratio
                }

                // Vocal Presence Peaking Filter (2.8 kHz speech clarity boost)
                if (hasVoicePresence) {
                    sample = presencePeakCoeff.process(sample, presencePeakState[ch])
                }

                // EQ & Filters
                if (hasEq) {
                    if (eq.highPassHz > 30f) sample = highPassCoeff.process(sample, highPassState[ch])
                    if (eq.lowPassHz < 19000f) sample = lowPassCoeff.process(sample, lowPassState[ch])
                    if (abs(eq.bassDb) > 0.1f) sample = lowShelfCoeff.process(sample, lowShelfState[ch])
                    if (abs(eq.midDb) > 0.1f) sample = midPeakCoeff.process(sample, midPeakState[ch])
                    if (abs(eq.trebleDb) > 0.1f) sample = highShelfCoeff.process(sample, highShelfState[ch])
                }

                // Echo / Delay
                if (hasDelay) {
                    val readPos = (delayWritePos - delaySamples + delayBuffer.size) % delayBuffer.size
                    val delayed = delayBuffer[readPos].toDouble()
                    val fed = sample + delayed * fx.echoFeedback
                    delayBuffer[delayWritePos] = fed.toFloat().coerceIn(-1.5f, 1.5f)
                    delayWritePos = (delayWritePos + 1) % delayBuffer.size
                    sample += delayed * 0.45
                }

                // Reverb
                if (hasReverb) {
                    val r1 = reverbBuffer1[reverbPos1].toDouble()
                    val r2 = reverbBuffer2[reverbPos2].toDouble()
                    reverbBuffer1[reverbPos1] = (sample + r1 * 0.55).toFloat().coerceIn(-1.5f, 1.5f)
                    reverbBuffer2[reverbPos2] = (sample + r2 * 0.48).toFloat().coerceIn(-1.5f, 1.5f)
                    reverbPos1 = (reverbPos1 + 1) % reverbBuffer1.size
                    reverbPos2 = (reverbPos2 + 1) % reverbBuffer2.size
                    val wet = (r1 + r2) * 0.5
                    sample = sample * (1.0 - fx.reverbMix * 0.4) + wet * fx.reverbMix * 0.65
                }

                val panGain = if (channelCount >= 2) {
                    if (ch == 0) leftPanGain else if (ch == 1) rightPanGain else 1f
                } else 1f

                val finalSample = (sample * totalGain * panGain * 32767.0)
                    .toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                outputBuffer.putShort(finalSample)
            }
            processedSamples++
        }
        outputBuffer.flip()
    }

    private fun evaluateVolumeAt(timeMs: Long): Float {
        var vol = if (keyframes.any { it.property == KeyframeProperty.VOLUME }) {
            AnimationEvaluator.interpolateProperty(keyframes, KeyframeProperty.VOLUME, timeMs, clipAudio.volume)
        } else {
            clipAudio.volume
        }

        val pts = clipAudio.envelopePoints.sortedBy { it.timeOffsetMs }
        if (pts.isNotEmpty()) {
            vol *= interpolateEnvelope(pts, timeMs)
        }

        if (clipAudio.fadeInMs > 0 && timeMs < clipAudio.fadeInMs) {
            vol *= (timeMs.toFloat() / clipAudio.fadeInMs.toFloat()).coerceIn(0f, 1f)
        }
        val fadeOutStart = (clipDurationMs - clipAudio.fadeOutMs).coerceAtLeast(0L)
        if (clipAudio.fadeOutMs > 0 && timeMs > fadeOutStart) {
            val rem = (clipDurationMs - timeMs).toFloat() / clipAudio.fadeOutMs.toFloat()
            vol *= rem.coerceIn(0f, 1f)
        }
        return vol.coerceIn(0f, 2f)
    }

    private fun interpolateEnvelope(pts: List<VolumeEnvelopePoint>, tMs: Long): Float {
        if (tMs <= pts.first().timeOffsetMs) return pts.first().volume
        if (tMs >= pts.last().timeOffsetMs) return pts.last().volume
        for (i in 0 until pts.size - 1) {
            val a = pts[i]
            val b = pts[i + 1]
            if (tMs in a.timeOffsetMs..b.timeOffsetMs) {
                val span = (b.timeOffsetMs - a.timeOffsetMs).coerceAtLeast(1L)
                val f = (tMs - a.timeOffsetMs).toFloat() / span.toFloat()
                return a.volume + (b.volume - a.volume) * f
            }
        }
        return 1f
    }

    private fun recomputeFilters(eq: EqSettings) {
        lowShelfCoeff = makeLowShelf(120.0, eq.bassDb.toDouble())
        midPeakCoeff = makePeaking(1000.0, 1.0, eq.midDb.toDouble())
        highShelfCoeff = makeHighShelf(6000.0, eq.trebleDb.toDouble())
        highPassCoeff = makeHighPass(eq.highPassHz.toDouble().coerceIn(20.0, 4000.0))
        lowPassCoeff = makeLowPass(eq.lowPassHz.toDouble().coerceIn(300.0, 20000.0))
        val humFreq = clipAudio.audioEffects.humFilterHz.takeIf { it == 50 || it == 60 }?.toDouble() ?: 60.0
        humNotchCoeff = makeNotch(humFreq, 8.0)
        presencePeakCoeff = makePeaking(2800.0, 1.2, clipAudio.audioEffects.voicePresenceBoost * 7.5)
    }

    private fun makeNotch(freq: Double, q: Double): BiquadCoeff {
        val w0 = 2.0 * PI * freq / sampleRate
        val cosW = cos(w0)
        val alpha = sin(w0) / (2.0 * q)
        val a0 = 1.0 + alpha
        return BiquadCoeff(
            b0 = 1.0 / a0,
            b1 = (-2.0 * cosW) / a0,
            b2 = 1.0 / a0,
            a1 = (-2.0 * cosW) / a0,
            a2 = (1.0 - alpha) / a0
        )
    }

    private fun makeLowShelf(freq: Double, gainDb: Double): BiquadCoeff {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * freq / sampleRate
        val cosW = cos(w0); val sinW = sin(w0)
        val alpha = sinW / 2.0 * sqrt(2.0)
        val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha
        val a0 = (a + 1) + (a - 1) * cosW + twoSqrtAAlpha
        return BiquadCoeff(
            b0 = (a * ((a + 1) - (a - 1) * cosW + twoSqrtAAlpha)) / a0,
            b1 = (2 * a * ((a - 1) - (a + 1) * cosW)) / a0,
            b2 = (a * ((a + 1) - (a - 1) * cosW - twoSqrtAAlpha)) / a0,
            a1 = (-2 * ((a - 1) + (a + 1) * cosW)) / a0,
            a2 = ((a + 1) + (a - 1) * cosW - twoSqrtAAlpha) / a0
        )
    }

    private fun makeHighShelf(freq: Double, gainDb: Double): BiquadCoeff {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * freq / sampleRate
        val cosW = cos(w0); val sinW = sin(w0)
        val alpha = sinW / 2.0 * sqrt(2.0)
        val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha
        val a0 = (a + 1) - (a - 1) * cosW + twoSqrtAAlpha
        return BiquadCoeff(
            b0 = (a * ((a + 1) + (a - 1) * cosW + twoSqrtAAlpha)) / a0,
            b1 = (-2 * a * ((a - 1) + (a + 1) * cosW)) / a0,
            b2 = (a * ((a + 1) + (a - 1) * cosW - twoSqrtAAlpha)) / a0,
            a1 = (2 * ((a - 1) - (a + 1) * cosW)) / a0,
            a2 = ((a + 1) - (a - 1) * cosW - twoSqrtAAlpha) / a0
        )
    }

    private fun makePeaking(freq: Double, q: Double, gainDb: Double): BiquadCoeff {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * freq / sampleRate
        val cosW = cos(w0); val sinW = sin(w0)
        val alpha = sinW / (2.0 * q)
        val a0 = 1.0 + alpha / a
        return BiquadCoeff(
            b0 = (1.0 + alpha * a) / a0,
            b1 = (-2.0 * cosW) / a0,
            b2 = (1.0 - alpha * a) / a0,
            a1 = (-2.0 * cosW) / a0,
            a2 = (1.0 - alpha / a) / a0
        )
    }

    private fun makeHighPass(freq: Double): BiquadCoeff {
        val w0 = 2.0 * PI * freq / sampleRate
        val cosW = cos(w0); val alpha = sin(w0) / sqrt(2.0)
        val a0 = 1.0 + alpha
        return BiquadCoeff(
            b0 = ((1.0 + cosW) / 2.0) / a0,
            b1 = (-(1.0 + cosW)) / a0,
            b2 = ((1.0 + cosW) / 2.0) / a0,
            a1 = (-2.0 * cosW) / a0,
            a2 = (1.0 - alpha) / a0
        )
    }

    private fun makeLowPass(freq: Double): BiquadCoeff {
        val w0 = 2.0 * PI * freq / sampleRate
        val cosW = cos(w0); val alpha = sin(w0) / sqrt(2.0)
        val a0 = 1.0 + alpha
        return BiquadCoeff(
            b0 = ((1.0 - cosW) / 2.0) / a0,
            b1 = (1.0 - cosW) / a0,
            b2 = ((1.0 - cosW) / 2.0) / a0,
            a1 = (-2.0 * cosW) / a0,
            a2 = (1.0 - alpha) / a0
        )
    }
}
