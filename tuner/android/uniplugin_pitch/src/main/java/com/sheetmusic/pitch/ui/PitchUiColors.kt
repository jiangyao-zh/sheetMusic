package com.sheetmusic.pitch.ui

import kotlin.math.abs

/**
 * 音准 UI 颜色（与 tuner/app/src/utils/format.ts statusColor 保持一致）
 */
object PitchUiColors {
    const val GRAY = 0xFF8B93A7.toInt()
    const val GREEN = 0xFF3DD68C.toInt()
    const val LIGHT_GREEN = 0xFF7EE787.toInt()
    const val YELLOW = 0xFFE3B341.toInt()
    const val RED = 0xFFFF7B72.toInt()

    fun accent(status: String, cent: Double): Int {
        if (status == "stabilizing") return GRAY
        if (status != "valid") return GRAY
        val absCent = abs(cent)
        return when {
            absCent <= 5 -> GREEN
            absCent <= 15 -> LIGHT_GREEN
            absCent <= 30 -> YELLOW
            else -> RED
        }
    }
}
