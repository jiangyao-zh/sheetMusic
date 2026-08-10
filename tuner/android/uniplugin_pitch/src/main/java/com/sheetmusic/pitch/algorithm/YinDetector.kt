package com.sheetmusic.pitch.algorithm

import kotlin.math.max
import kotlin.math.min

/**
 * YIN 基频检测（小提琴单音）。
 * 流程：Difference → CMND → Absolute Threshold → Parabolic Interpolation → 谐波候选修正。
 */
class YinDetector(
    private val sampleRate: Int = 44100,
    private val threshold: Double = 0.1,
    /** 小提琴有效音域 G3–E7（略放宽下界便于 G4 边缘） */
    private val minFrequency: Double = 190.0,
    private val maxFrequency: Double = 2700.0,
) {
    data class Result(val frequency: Double, val confidence: Double)

    /** 上一帧频率，供谐波候选连续性加权（由 PitchAnalyzer 设置）。 */
    @Volatile
    var previousFrequency: Double? = null

    fun detect(samples: FloatArray): Result? {
        val n = samples.size
        if (n < 64) return null

        val tauMin = max(2, (sampleRate / maxFrequency).toInt())
        val tauMax = min(n / 2, (sampleRate / minFrequency).toInt())
        if (tauMax <= tauMin + 2) return null

        val diff = differenceFunction(samples, tauMax)
        val cmnd = cumulativeMeanNormalizedDifference(diff)

        val tauEstimate = absoluteThreshold(cmnd, tauMin, tauMax) ?: return null
        val tauParabola = parabolicInterpolation(cmnd, tauEstimate)
        val tauRefined = refineFractionalTau(samples, tauParabola)
        if (tauRefined <= 0.0) return null

        val rawFrequency = sampleRate / tauRefined
        if (rawFrequency < minFrequency || rawFrequency > maxFrequency) return null

        val rawConf = (1.0 - cmnd[tauEstimate]).coerceIn(0.0, 1.0)
        val (frequency, confidence) = HarmonicScorer.pickBestCandidate(
            samples = samples,
            sampleRate = sampleRate,
            rawFrequency = rawFrequency,
            rawConfidence = rawConf,
            minFrequency = minFrequency,
            maxFrequency = maxFrequency,
            previousFrequency = previousFrequency,
        )
        return Result(frequency = frequency, confidence = confidence)
    }

    private fun refineFractionalTau(samples: FloatArray, tau: Double): Double {
        if (tau < 2.0) return tau
        var bestTau = tau
        var bestVal = fractionalDifference(samples, tau)
        var t = tau - 0.75
        val end = tau + 0.75
        while (t <= end + 1e-12) {
            val v = fractionalDifference(samples, t)
            if (v < bestVal) {
                bestVal = v
                bestTau = t
            }
            t += 0.01
        }
        return bestTau
    }

    private fun fractionalDifference(samples: FloatArray, tau: Double): Double {
        val tauFloor = kotlin.math.floor(tau).toInt()
        val frac = tau - tauFloor
        if (tauFloor < 1 || tauFloor + 1 >= samples.size) return Double.MAX_VALUE
        var sum = 0.0
        val limit = samples.size - tauFloor - 1
        for (j in 0 until limit) {
            val delayed = samples[j + tauFloor] * (1.0 - frac) + samples[j + tauFloor + 1] * frac
            val delta = samples[j] - delayed
            sum += delta * delta
        }
        return sum
    }

    private fun differenceFunction(samples: FloatArray, tauMax: Int): DoubleArray {
        val d = DoubleArray(tauMax + 1)
        for (tau in 1..tauMax) {
            var sum = 0.0
            val limit = samples.size - tau
            for (j in 0 until limit) {
                val delta = samples[j] - samples[j + tau]
                sum += delta * delta
            }
            d[tau] = sum
        }
        return d
    }

    private fun cumulativeMeanNormalizedDifference(diff: DoubleArray): DoubleArray {
        val cmnd = DoubleArray(diff.size)
        cmnd[0] = 1.0
        var runningSum = 0.0
        for (tau in 1 until diff.size) {
            runningSum += diff[tau]
            cmnd[tau] = if (runningSum > 0) diff[tau] * tau / runningSum else 1.0
        }
        return cmnd
    }

    private fun absoluteThreshold(cmnd: DoubleArray, tauMin: Int, tauMax: Int): Int? {
        var tau = tauMin
        while (tau < tauMax) {
            if (cmnd[tau] < threshold) {
                while (tau + 1 < tauMax && cmnd[tau + 1] < cmnd[tau]) {
                    tau++
                }
                return tau
            }
            tau++
        }
        var best = tauMin
        var bestVal = cmnd[tauMin]
        for (t in tauMin + 1..tauMax) {
            if (cmnd[t] < bestVal) {
                bestVal = cmnd[t]
                best = t
            }
        }
        return if (bestVal < 0.32) best else null
    }

    private fun parabolicInterpolation(cmnd: DoubleArray, tau: Int): Double {
        if (tau <= 0 || tau >= cmnd.size - 1) return tau.toDouble()
        val s0 = cmnd[tau - 1]
        val s1 = cmnd[tau]
        val s2 = cmnd[tau + 1]
        val denom = s0 - 2.0 * s1 + s2
        if (kotlin.math.abs(denom) < 1e-12) return tau.toDouble()
        val delta = 0.5 * (s0 - s2) / denom
        return tau + delta
    }
}
