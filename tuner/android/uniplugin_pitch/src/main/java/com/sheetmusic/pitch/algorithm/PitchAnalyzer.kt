package com.sheetmusic.pitch.algorithm

import com.sheetmusic.pitch.audio.AudioPreprocessor
import com.sheetmusic.pitch.model.PitchResult

/**
 * 将预处理 + YIN + 音符转换 + 评分串联为一次分析。
 */
class PitchAnalyzer(
    sampleRate: Int = 44100,
    private val a4: Double = 440.0,
    private val rmsThreshold: Double = 0.01,
    private val confidenceThreshold: Double = 0.6,
) {
    private val yin = YinDetector(sampleRate = sampleRate)
    @Volatile
    var targetNote: String? = null

    fun analyzePcm16(bytes: ByteArray, sampleCount: Int): PitchResult {
        val floats = AudioPreprocessor.pcm16ToFloat(bytes, sampleCount)
        return analyzeFloat(floats)
    }

    fun analyzeFloat(samples: FloatArray): PitchResult {
        val rms = AudioPreprocessor.rms(samples)
        if (rms < rmsThreshold) {
            return PitchResult.noSignal()
        }
        // YIN 对汉宁窗敏感，检测前只做去直流；窗函数留给频谱类算法扩展
        val processed = AudioPreprocessor.removeDcOffset(samples)
        val yinResult = yin.detect(processed) ?: return PitchResult.noSignal()
        if (yinResult.confidence < confidenceThreshold) {
            return PitchResult(
                frequency = round2(yinResult.frequency),
                confidence = round2(yinResult.confidence),
                note = "--",
                midi = 0.0,
                cent = 0.0,
                score = 0.0,
                status = "too_low",
            )
        }
        val noteInfo = NoteConverter.fromFrequency(
            frequency = yinResult.frequency,
            a4 = a4,
            targetNote = targetNote,
        )
        val scored = PitchScorer.evaluate(yinResult.frequency, noteInfo.targetFrequency)
        return PitchResult(
            frequency = round2(yinResult.frequency),
            confidence = round2(yinResult.confidence),
            note = noteInfo.note,
            midi = round2(noteInfo.midi),
            cent = round1(scored.cent),
            score = scored.score,
            status = "valid",
        )
    }

    private fun round2(v: Double): Double = (v * 100.0).let { kotlin.math.round(it) / 100.0 }
    private fun round1(v: Double): Double = (v * 10.0).let { kotlin.math.round(it) / 10.0 }
}
