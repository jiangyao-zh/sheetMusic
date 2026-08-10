package com.sheetmusic.pitch.model

/**
 * 与前端 PitchResult 字段一一对应。
 */
data class PitchResult(
    val frequency: Double,
    val confidence: Double,
    val note: String,
    val midi: Double,
    val cent: Double,
    val score: Double,
    val status: String,
    /** 乐器可能性评分 0–1，仅调试/展示用 */
    val instrumentScore: Double = 0.0,
) {
    fun toMap(): Map<String, Any> = buildMap {
        put("frequency", frequency)
        put("confidence", confidence)
        put("note", note)
        put("midi", midi)
        put("cent", cent)
        put("score", score)
        put("status", status)
        if (instrumentScore > 0) put("instrumentScore", instrumentScore)
    }

    companion object {
        fun idle(): PitchResult = PitchResult(
            frequency = 0.0,
            confidence = 0.0,
            note = "--",
            midi = 0.0,
            cent = 0.0,
            score = 0.0,
            status = "idle",
        )

        fun detecting(): PitchResult = idle().copy(status = "detecting")

        fun noSignal(): PitchResult = idle().copy(status = "no_signal")

        fun metronomeSuppressed(): PitchResult = idle().copy(status = "metronome_suppressed")

        fun voiceRejected(score: Double = 0.0): PitchResult = idle().copy(
            status = "voice_rejected",
            instrumentScore = score,
        )

        fun stabilizing(from: PitchResult): PitchResult = from.copy(status = "stabilizing")
    }
}
