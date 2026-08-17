package com.sheetmusic.pitch.algorithm

import kotlin.math.abs
import kotlin.math.ln

/**
 * 起音稳定器：静音→发声需连续若干帧频率一致才输出。
 * 已锁定后的大跨度换音直接 snap，不再二次低通拖住目标音。
 */
class AttackStabilizer(
    private val agreeFrames: Int = 3,
    private val agreeCents: Double = 50.0,
) {
    private enum class Phase { SILENT, WARMUP, READY }

    private var phase = Phase.SILENT
    private val window = ArrayDeque<Double>(agreeFrames)
    private var lockedFreq = 0.0

    fun reset() = onSilence()

    fun onSilence() {
        phase = Phase.SILENT
        window.clear()
        lockedFreq = 0.0
    }

    val isReady: Boolean get() = phase == Phase.READY

    fun outputFrequency(): Double = if (phase == Phase.READY) lockedFreq else 0.0

    /**
     * @return true 表示已通过起音校验，可以展示音名
     */
    fun feed(frequency: Double): Boolean {
        if (frequency <= 0) {
            onSilence()
            return false
        }
        when (phase) {
            Phase.SILENT -> {
                phase = Phase.WARMUP
                window.clear()
                window.addLast(frequency)
                return false
            }
            Phase.WARMUP -> {
                window.addLast(frequency)
                while (window.size > agreeFrames) window.removeFirst()
                if (window.size < agreeFrames) return false
                val med = median(window)
                if (!window.all { centsBetween(it, med) <= agreeCents }) return false
                phase = Phase.READY
                lockedFreq = med
                return true
            }
            Phase.READY -> {
                val jump = centsBetween(lockedFreq, frequency)
                lockedFreq = if (jump >= PitchTracker.LARGE_JUMP_CENTS) {
                    frequency
                } else {
                    lockedFreq * 0.55 + frequency * 0.45
                }
                return true
            }
        }
    }

    private fun median(values: Collection<Double>): Double {
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }

    private fun centsBetween(a: Double, b: Double): Double {
        if (a <= 0 || b <= 0) return 9999.0
        return abs(1200.0 * ln(a / b) / ln(2.0))
    }
}
