package com.sheetmusic.pitch.algorithm

import kotlin.math.max
import kotlin.math.min

/**
 * YIN 基频检测（小提琴单音）。
 * 流程：Difference → CMND → Absolute Threshold → Parabolic Interpolation。
 */
class YinDetector(
    private val sampleRate: Int = 44100,
    private val threshold: Double = 0.1,
    /** 小提琴有效音域 G3–E7 */
    private val minFrequency: Double = 196.0,
    private val maxFrequency: Double = 2637.0,
) {
    data class Result(val frequency: Double, val confidence: Double)

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
        // 高音区周期样本少，再对分数 tau 做局部搜索细化
        val tauRefined = refineFractionalTau(samples, tauParabola)
        if (tauRefined <= 0.0) return null

        val frequency = sampleRate / tauRefined
        if (frequency < minFrequency || frequency > maxFrequency) return null

        val conf = (1.0 - cmnd[tauEstimate]).coerceIn(0.0, 1.0)
        return Result(frequency = frequency, confidence = conf)
    }

    /**
     * 在 [tau-0.75, tau+0.75] 内以 0.01 步长搜索分数时延，提升短周期精度。
     */
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
        // 回退：取范围内全局最小
        var best = tauMin
        var bestVal = cmnd[tauMin]
        for (t in tauMin + 1..tauMax) {
            if (cmnd[t] < bestVal) {
                bestVal = cmnd[t]
                best = t
            }
        }
        return if (bestVal < 0.3) best else null
    }

    private fun parabolicInterpolation(cmnd: DoubleArray, tau: Int): Double {
        if (tau <= 0 || tau >= cmnd.size - 1) return tau.toDouble()
        val s0 = cmnd[tau - 1]
        val s1 = cmnd[tau]
        val s2 = cmnd[tau + 1]
        // x = 0.5 * (y[-1] - y[1]) / (y[-1] - 2y[0] + y[1])
        val denom = s0 - 2.0 * s1 + s2
        if (kotlin.math.abs(denom) < 1e-12) return tau.toDouble()
        val delta = 0.5 * (s0 - s2) / denom
        return tau + delta
    }
}
