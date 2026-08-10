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

    /** 返回平滑后的频率；样本不足时返回原值。 */
    fun smooth(frequency: Double): Double {
        if (frequency <= 0) {
            history.clear()
            return frequency
        }
        if (history.isNotEmpty()) {
            val last = history.last()
            val cents = abs(1200.0 * ln(frequency / last) / ln(2.0))
            if (cents > maxJumpCents) {
                // 突变：先保留历史，用加权平均过渡
                history.addLast(last * 0.7 + frequency * 0.3)
            } else {
                history.addLast(frequency)
            }
        } else {
            history.addLast(frequency)
        }
        while (history.size > windowSize) history.removeFirst()
        if (history.size < 3) return frequency
        val sorted = history.sorted()
        return sorted[sorted.size / 2]
    }
}
