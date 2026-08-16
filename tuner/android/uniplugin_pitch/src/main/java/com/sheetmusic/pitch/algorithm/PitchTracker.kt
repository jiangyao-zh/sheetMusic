package com.sheetmusic.pitch.algorithm

import kotlin.math.abs
import kotlin.math.ln

/**
 * 短窗口中值平滑 + 帧间连续性，避免 G4 等音高在相邻帧间跳变。
 */
class PitchTracker(
    private val windowSize: Int = 5,
    private val maxJumpCents: Double = 80.0,
) {
    private val history = ArrayDeque<Double>(windowSize)

    fun reset() {
        history.clear()
    }

    /** 返回平滑后的频率；窗口内取中值，起音阶段抑制离群跳变。 */
    fun smooth(frequency: Double): Double {
        if (frequency <= 0) {
            history.clear()
            return frequency
        }
        if (history.isNotEmpty()) {
            val last = history.last()
            val cents = abs(1200.0 * ln(frequency / last) / ln(2.0))
            when {
                // 起音/前几帧：八度级跳变先丢弃，避免连跳多个音名
                cents > 300 && history.size < 4 -> { /* skip outlier */ }
                cents > maxJumpCents -> history.addLast(last * 0.7 + frequency * 0.3)
                else -> history.addLast(frequency)
            }
        } else {
            history.addLast(frequency)
        }
        while (history.size > windowSize) history.removeFirst()
        return median(history)
    }

    private fun median(values: Collection<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }
}
