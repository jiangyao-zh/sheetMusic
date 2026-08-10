package com.sheetmusic.pitch.algorithm

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * 离线轻量乐器/人声判别：谐波结构 + 谱质心 + 帧间稳定性。
 * 偏保守：优先不漏掉真实小提琴音（尤其 G4）。
 */
class InstrumentGate(
    private val minScore: Double = 0.42,
    private val confirmFrames: Int = 2,
) {
    private var stableCount = 0
    private var lastFrequency = 0.0

    fun reset() {
        stableCount = 0
        lastFrequency = 0.0
    }

    data class Verdict(val accepted: Boolean, val score: Double, val reason: String)

    fun evaluate(
        samples: FloatArray,
        sampleRate: Int,
        frequency: Double,
        yinConfidence: Double,
    ): Verdict {
        if (frequency <= 0) {
            stableCount = 0
            return Verdict(false, 0.0, "no_freq")
        }

        val harm = HarmonicScorer.score(samples, frequency, sampleRate)
        val centroid = spectralCentroid(samples, sampleRate)
        val flatness = spectralFlatness(samples)

        // 小提琴中音区：谐波清晰、质心适中、谱较不平
        var score = yinConfidence * 0.35 + harm * 0.40
        if (frequency in 180.0..1100.0) score += 0.08
        if (centroid in frequency * 1.2..frequency * 4.5) score += 0.10
        if (flatness < 0.55) score += 0.07 else score -= 0.12

        // 人声倾向：基频弱、谐波差、谱平、质心偏离
        if (harm < 0.30 && flatness > 0.42) score -= 0.22
        if (harm < 0.22) score -= 0.15
        if (frequency > 0 && centroid / frequency > 3.2) score -= 0.22
        if (flatness > 0.50) score -= 0.12

        // 帧间稳定性
        if (lastFrequency > 0) {
            val cents = abs(1200.0 * ln(frequency / lastFrequency) / ln(2.0))
            if (cents < 25) {
                stableCount++
                score += 0.06
            } else {
                stableCount = 0
            }
        }
        lastFrequency = frequency

        val accepted = score >= minScore && (
            stableCount >= confirmFrames - 1 ||
            score >= minScore + 0.10 ||
            yinConfidence >= 0.82
            )
        val reason = when {
            accepted -> "ok"
            harm < 0.25 -> "weak_harmonics"
            flatness > 0.6 -> "too_flat"
            else -> "low_score"
        }
        return Verdict(accepted, score.coerceIn(0.0, 1.0), reason)
    }

    private fun spectralCentroid(samples: FloatArray, sampleRate: Int): Double {
        val n = minOf(512, samples.size)
        var num = 0.0
        var den = 0.0
        for (k in 1 until n / 2) {
            var re = 0.0
            var im = 0.0
            val omega = 2.0 * kotlin.math.PI * k / n
            for (i in 0 until n) {
                re += samples[i] * kotlin.math.cos(omega * i)
                im -= samples[i] * kotlin.math.sin(omega * i)
            }
            val mag = sqrt(re * re + im * im)
            val freq = k * sampleRate.toDouble() / n
            num += freq * mag
            den += mag
        }
        return if (den > 1e-12) num / den else 0.0
    }

    private fun spectralFlatness(samples: FloatArray): Double {
        val n = minOf(512, samples.size)
        val mags = DoubleArray(n / 2 - 1)
        for (k in 1 until n / 2) {
            var re = 0.0
            var im = 0.0
            val omega = 2.0 * kotlin.math.PI * k / n
            for (i in 0 until n) {
                re += samples[i] * kotlin.math.cos(omega * i)
                im -= samples[i] * kotlin.math.sin(omega * i)
            }
            mags[k - 1] = sqrt(re * re + im * im) + 1e-12
        }
        if (mags.isEmpty()) return 1.0
        val logMean = mags.map { kotlin.math.ln(it) }.average()
        val geo = kotlin.math.exp(logMean)
        val arith = mags.average()
        return (geo / arith).coerceIn(0.0, 1.0)
    }
}
