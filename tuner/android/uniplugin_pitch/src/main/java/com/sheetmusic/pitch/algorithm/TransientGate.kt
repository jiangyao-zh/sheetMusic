package com.sheetmusic.pitch.algorithm

import com.sheetmusic.pitch.audio.AudioPreprocessor
import kotlin.math.abs
import kotlin.math.max

/**
 * 谱通量 / 帧间能量突变检测，用于屏蔽节拍器短促点击（兜底，配合 TV 节拍同步）。
 */
class TransientGate(
    private val fluxThreshold: Double = 0.42,
    private val attackRatioThreshold: Double = 2.4,
) {
    private var prevSpectrum: DoubleArray? = null
    private var prevRms = 0.0

    fun reset() {
        prevSpectrum = null
        prevRms = 0.0
    }

    /** true 表示当前帧像短促瞬态（应屏蔽）。 */
    fun isTransient(samples: FloatArray): Boolean {
        val rms = AudioPreprocessor.rms(samples)
        val attack = if (prevRms > 1e-8) rms / max(prevRms, 1e-8) else 1.0
        prevRms = rms

        val spec = magnitudeSpectrum(samples)
        val prev = prevSpectrum
        prevSpectrum = spec

        if (prev == null || prev.size != spec.size) {
            return false
        }

        var flux = 0.0
        for (i in spec.indices) {
            val d = spec[i] - prev[i]
            if (d > 0) flux += d
        }
        flux /= spec.size.coerceAtLeast(1)
        return flux > fluxThreshold && attack > attackRatioThreshold
    }

    private fun magnitudeSpectrum(samples: FloatArray): DoubleArray {
        val n = minOf(512, samples.size)
        val bins = n / 2
        val out = DoubleArray(bins)
        for (k in 1 until bins) {
            var re = 0.0
            var im = 0.0
            val omega = 2.0 * kotlin.math.PI * k / n
            for (i in 0 until n) {
                re += samples[i] * kotlin.math.cos(omega * i)
                im -= samples[i] * kotlin.math.sin(omega * i)
            }
            out[k] = kotlin.math.sqrt(re * re + im * im)
        }
        return out
    }
}
