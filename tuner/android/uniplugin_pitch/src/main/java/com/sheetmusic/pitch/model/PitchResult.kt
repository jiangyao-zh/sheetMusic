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
) {
    fun toMap(): Map<String, Any> = mapOf(
        "frequency" to frequency,
        "confidence" to confidence,
        "note" to note,
        "midi" to midi,
        "cent" to cent,
        "score" to score,
        "status" to status,
    )

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
    }
}
