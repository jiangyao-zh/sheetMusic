package com.sheetmusic.pitch.algorithm

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * 离线轻量乐器/人声判别：谐波结构 + 谱质心 + 帧间稳定性。
 * 偏宽松：优先不漏掉真实小提琴音（含弱信号、换把、高音区）。
 */
class InstrumentGate(
    private val minScore: Double = 0.26,
    private val confirmFrames: Int = 1,
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

        val harm = HarmonicScorer.bestHarmonicScore(samples, frequency, sampleRate)
        val h2Ratio = HarmonicScorer.secondHarmonicRatio(samples, frequency, sampleRate)
        val centroid = spectralCentroid(samples, sampleRate)
        val flatness = spectralFlatness(samples)

        // 弱基频小提琴（G 弦 / G4）：2f≫f 时谐波分在基频处偏低，给予补偿
        val weakFundamentalViolin = frequency in 180.0..880.0 && h2Ratio > 1.8

        // 小提琴全音域：谐波清晰、质心适中、谱较不平
        var score = yinConfidence * 0.40 + harm * 0.35
        if (frequency in 180.0..2700.0) score += 0.10
        if (weakFundamentalViolin) score += 0.12
        if (centroid in frequency * 1.0..frequency * 5.5) score += 0.08
        if (flatness < 0.58) score += 0.06 else score -= 0.08

        // 仅对明显人声特征重罚（弱谐波 + 高谱平 + 质心偏离）
        if (harm < 0.18 && flatness > 0.55) score -= 0.20
        if (harm < 0.14) score -= 0.10
        if (frequency > 0 && centroid / frequency > 4.0 && flatness > 0.48) score -= 0.15

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

        // 明显人声/噪声：弱谐波结构 + 偏平谱（弱基频小提琴除外）
        if (!weakFundamentalViolin && harm < 0.26 && flatness > 0.50 && yinConfidence < 0.78) {
            return Verdict(false, score.coerceIn(0.0, 1.0), "voice_like")
        }

        val accepted = score >= minScore && (
            stableCount >= confirmFrames - 1 ||
                score >= minScore + 0.08 ||
                yinConfidence >= 0.65 ||
                weakFundamentalViolin && yinConfidence >= 0.45 ||
                (yinConfidence >= 0.52 && frequency in 180.0..2700.0 && harm >= 0.12)
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
