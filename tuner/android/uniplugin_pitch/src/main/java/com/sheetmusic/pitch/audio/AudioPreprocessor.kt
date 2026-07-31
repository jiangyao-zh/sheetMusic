package com.sheetmusic.pitch.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * PCM 浮点帧预处理：去直流 → 归一化 → 汉宁窗。
 * 每步均为纯函数，便于单测。
 */
object AudioPreprocessor {

    fun process(samples: FloatArray): FloatArray {
        if (samples.isEmpty()) return samples
        val dcRemoved = removeDcOffset(samples)
        val normalized = normalize(dcRemoved)
        return hanningWindow(normalized)
    }

    fun removeDcOffset(samples: FloatArray): FloatArray {
        var sum = 0.0
        for (s in samples) sum += s
        val mean = (sum / samples.size).toFloat()
        return FloatArray(samples.size) { i -> samples[i] - mean }
    }

    fun normalize(samples: FloatArray): FloatArray {
        var peak = 0f
        for (s in samples) {
            val a = kotlin.math.abs(s)
            if (a > peak) peak = a
        }
        if (peak < 1e-8f) return samples.copyOf()
        val inv = 1f / peak
        return FloatArray(samples.size) { i -> samples[i] * inv }
    }

    fun hanningWindow(samples: FloatArray): FloatArray {
        val n = samples.size
        if (n <= 1) return samples.copyOf()
        val denom = (n - 1).toDouble()
        return FloatArray(n) { i ->
            val w = (0.5 * (1.0 - cos(2.0 * PI * i / denom))).toFloat()
            samples[i] * w
        }
    }

    /** RMS 能量，用于静音门限。 */
    fun rms(samples: FloatArray): Double {
        if (samples.isEmpty()) return 0.0
        var acc = 0.0
        for (s in samples) acc += s * s.toDouble()
        return sqrt(acc / samples.size)
    }

    /** PCM16 LE byte → float [-1, 1] */
    fun pcm16ToFloat(bytes: ByteArray, sampleCount: Int): FloatArray {
        val out = FloatArray(sampleCount)
        var bi = 0
        for (i in 0 until sampleCount) {
            if (bi + 1 >= bytes.size) break
            val lo = bytes[bi].toInt() and 0xff
            val hi = bytes[bi + 1].toInt()
            val sample = ((hi shl 8) or lo).toShort()
            out[i] = sample / 32768f
            bi += 2
        }
        return out
    }
}
