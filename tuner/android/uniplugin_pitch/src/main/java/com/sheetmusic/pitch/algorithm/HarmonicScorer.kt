package com.sheetmusic.pitch.algorithm

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * 基于整数倍谐波能量的基频候选评分，用于修正弱基频 / 倍频误判（如 G4→G3/G5）。
 */
object HarmonicScorer {

    /** 返回 [0,1]，越高表示越像稳定乐音基频。 */
    fun score(samples: FloatArray, frequency: Double, sampleRate: Int): Double {
        if (frequency <= 0 || samples.isEmpty()) return 0.0
        val omega = 2.0 * PI * frequency / sampleRate
        var fundamental = 0.0
        var harmonicSum = 0.0
        var totalEnergy = 0.0

        for (h in 1..5) {
            val fh = frequency * h
            if (fh >= sampleRate / 2.0 - 50) break
            val weight = when (h) {
                1 -> 1.0
                2 -> 0.55
                3 -> 0.35
                4 -> 0.22
                else -> 0.15
            }
            val proj = projectCos(samples, omega * h)
            val energy = proj * proj
            totalEnergy += energy
            if (h == 1) fundamental = energy
            else harmonicSum += energy * weight
        }

        if (totalEnergy < 1e-12) return 0.0
        val ratio = (fundamental + harmonicSum) / totalEnergy
        return ratio.coerceIn(0.0, 1.0)
    }

    /** 对 YIN 候选频率做谐波修正：优先弱基频回落，否则按谐波列拟合选最优。 */
    fun pickBestCandidate(
        samples: FloatArray,
        sampleRate: Int,
        rawFrequency: Double,
        rawConfidence: Double,
        minFrequency: Double,
        maxFrequency: Double,
        previousFrequency: Double? = null,
    ): Pair<Double, Double> {
        val half = rawFrequency * 0.5
        if (half in minFrequency..maxFrequency &&
            isSecondHarmonicLock(samples, sampleRate, half, rawFrequency)
        ) {
            val conf = (rawConfidence * 0.65 + 0.35).coerceIn(0.0, 1.0)
            return half to conf
        }

        var bestF = rawFrequency
        var bestScore = fundamentalLikelihood(samples, rawFrequency, sampleRate)
        val halfCandidate = rawFrequency * 0.5
        if (halfCandidate in minFrequency..maxFrequency &&
            isSecondHarmonicLock(samples, sampleRate, halfCandidate, rawFrequency)
        ) {
            val s = fundamentalLikelihood(samples, halfCandidate, sampleRate)
            if (s > bestScore * 1.02) {
                bestScore = s
                bestF = halfCandidate
            }
        }

        if (previousFrequency != null && previousFrequency > 0) {
            val cents = abs(1200.0 * ln(bestF / previousFrequency) / ln(2.0))
            if (cents < 35) bestScore += 0.15
        }

        val conf = (rawConfidence * 0.55 + (bestScore / (bestScore + 1.0)) * 0.45).coerceIn(0.0, 1.0)
        return bestF to conf
    }

    /** 检测 YIN 是否锁在 candidate 的二次谐波上（弱基频典型特征）。 */
    private fun isSecondHarmonicLock(
        samples: FloatArray,
        sampleRate: Int,
        candidateF: Double,
        detectedF: Double,
    ): Boolean {
        if (abs(detectedF / candidateF - 2.0) > 0.04) return false
        val omega = 2.0 * PI * candidateF / sampleRate
        val e1 = projectCos(samples, omega)
        val e2 = projectCos(samples, omega * 2.0)
        if (e1 < 1e-5) return false
        return (e2 * e2) > (e1 * e1 * 2.5) && e1 > e2 * 0.12
    }

    /** 谐波衰减模式拟合（小提琴 1:0.45:0.25:0.12）。 */
    private fun fundamentalLikelihood(samples: FloatArray, frequency: Double, sampleRate: Int): Double {
        val omega = 2.0 * PI * frequency / sampleRate
        val e1 = projectCos(samples, omega).let { it * it }
        if (e1 < 1e-10) return 0.0
        val e2 = projectCos(samples, omega * 2.0).let { it * it }
        val e3 = projectCos(samples, omega * 3.0).let { it * it }
        val e4 = projectCos(samples, omega * 4.0).let { it * it }
        val r2 = e2 / e1
        val r3 = e3 / e1
        val r4 = e4 / e1
        val err = abs(r2 - 0.45) + abs(r3 - 0.25) + abs(r4 - 0.12)
        var s = 1.0 / (1.0 + err)
        s *= (1.0 + ln(1.0 + e1 * 1000.0))
        if (r2 > 2.0) s *= 0.35
        return s
    }

    private fun projectCos(samples: FloatArray, omega: Double): Double {
        var re = 0.0
        var im = 0.0
        for (i in samples.indices) {
            val phase = omega * i
            re += samples[i] * cos(phase)
            im += samples[i] * kotlin.math.sin(phase)
        }
        return sqrt(re * re + im * im) / samples.size.coerceAtLeast(1)
    }
}
