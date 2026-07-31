package com.sheetmusic.pitch.algorithm

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max

/**
 * cent 偏差与分段评分。
 */
object PitchScorer {

    data class ScoreResult(val cent: Double, val score: Double)

    fun cents(actualHz: Double, targetHz: Double): Double {
        require(actualHz > 0 && targetHz > 0)
        return 1200.0 * (ln(actualHz / targetHz) / ln(2.0))
    }

    fun scoreFromCent(cent: Double): Double {
        val absCent = abs(cent)
        return when {
            absCent <= 5 -> 100.0
            absCent <= 15 -> 95.0
            absCent <= 30 -> 85.0
            absCent <= 50 -> 70.0
            else -> max(0.0, 60.0 - (absCent - 50.0) / 2.0)
        }
    }

    fun evaluate(actualHz: Double, targetHz: Double): ScoreResult {
        val cent = cents(actualHz, targetHz)
        return ScoreResult(cent = cent, score = scoreFromCent(cent))
    }
}
