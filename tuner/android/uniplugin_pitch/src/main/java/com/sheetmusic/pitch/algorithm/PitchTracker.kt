package com.sheetmusic.pitch.algorithm

import kotlin.math.abs
import kotlin.math.ln

/**
 * 短窗口中值平滑：消除测量抖动，但不把合法大跳变抹成假滑音。
 *
 * < 100 cent：中值
 * 100–300 cent：轻度跟随
 * > 300 cent：直接采纳新频率
 */
class PitchTracker(
    private val windowSize: Int = 5,
) {
    private val history = ArrayDeque<Double>(windowSize)

    fun reset() {
        history.clear()
    }

    fun smooth(frequency: Double): Double {
        if (frequency <= 0) {
            history.clear()
            return frequency
        }
        if (history.isEmpty()) {
            history.addLast(frequency)
            return frequency
        }
        val last = history.last()
        val cents = centsBetween(last, frequency)
        when {
            cents >= LARGE_JUMP_CENTS -> {
                history.clear()
                history.addLast(frequency)
            }
            cents >= MEDIUM_JUMP_CENTS -> {
                history.addLast(last * 0.45 + frequency * 0.55)
            }
            else -> history.addLast(frequency)
        }
        while (history.size > windowSize) history.removeFirst()
        return median(history)
    }

    private fun median(values: Collection<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }

    private fun centsBetween(a: Double, b: Double): Double {
        if (a <= 0 || b <= 0) return 9999.0
        return abs(1200.0 * ln(a / b) / ln(2.0))
    }

    companion object {
        const val MEDIUM_JUMP_CENTS = 100.0
        const val LARGE_JUMP_CENTS = 300.0
    }
}
