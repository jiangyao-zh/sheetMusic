package com.sheetmusic.pitch.algorithm

import kotlin.math.abs
import kotlin.math.ln

/**
 * 起音稳定器：从静音到发声时，需连续若干帧频率一致才输出音符，避免拉弓瞬间连跳多个音名。
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

    /** 当前可输出的锁定频率；未就绪时为 0 */
    fun outputFrequency(): Double = if (phase == Phase.READY) lockedFreq else 0.0

    /**
     * 喂入本帧平滑频率。
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
                // 锁定后缓慢跟随，大幅跳变（换音）仍交给 NoteDisplayLock
                lockedFreq = lockedFreq * 0.72 + frequency * 0.28
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
