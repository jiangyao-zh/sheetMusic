package com.sheetmusic.pitch.algorithm

import com.sheetmusic.pitch.audio.AudioPreprocessor
import com.sheetmusic.pitch.model.PitchResult

/**
 * 将预处理 + YIN + 抗干扰门控 + 音符转换 + 评分串联为一次分析。
 *
 * 顺序：静音 → 节拍窗口 → YIN → 瞬态过滤 → 乐器门控 → 稳定跟踪 → 输出
 */
class PitchAnalyzer(
    private val sampleRate: Int = 44100,
    private val a4: Double = 440.0,
    private val rmsThreshold: Double = 0.01,
    private val confidenceThreshold: Double = 0.55,
) {
    private val yin = YinDetector(sampleRate = sampleRate)
    private val tracker = PitchTracker()
    private val transientGate = TransientGate()
    private val instrumentGate = InstrumentGate()
    private val metronomeGate = MetronomeGate()

    @Volatile
    var targetNote: String? = null

    /** 上一有效输出，过滤期间短暂保持显示 */
    private var lastValid: PitchResult? = null
    private var lastValidAtMs: Long = 0L
    private val holdValidMs = 350L

    fun reset() {
        tracker.reset()
        transientGate.reset()
        instrumentGate.reset()
        metronomeGate.reset()
        yin.previousFrequency = null
        lastValid = null
        lastValidAtMs = 0L
    }

    /** TV 节拍事件：在 suppressMs 内暂停分析 */
    fun notifyMetronomeBeat(tsMs: Long = System.currentTimeMillis(), suppressMs: Long = 120L) {
        metronomeGate.notifyBeat(tsMs, suppressMs)
    }

    fun isMetronomeSuppressed(): Boolean = metronomeGate.isSuppressed()

    fun analyzePcm16(bytes: ByteArray, sampleCount: Int): PitchResult {
        val floats = AudioPreprocessor.pcm16ToFloat(bytes, sampleCount)
        return analyzeFloat(floats)
    }

    fun analyzeFloat(samples: FloatArray): PitchResult {
        val now = System.currentTimeMillis()

        val rms = AudioPreprocessor.rms(samples)
        if (rms < rmsThreshold) {
            return holdOr(PitchResult.noSignal(), now)
        }

        if (metronomeGate.isSuppressed(now)) {
            return holdOr(PitchResult.metronomeSuppressed(), now)
        }

        val processed = AudioPreprocessor.removeDcOffset(samples)

        if (transientGate.isTransient(processed)) {
            return holdOr(PitchResult.metronomeSuppressed(), now)
        }

        yin.previousFrequency = lastValid?.frequency?.takeIf { it > 0 }
        val yinResult = yin.detect(processed) ?: return holdOr(PitchResult.noSignal(), now)

        if (yinResult.confidence < confidenceThreshold) {
            return holdOr(
                PitchResult(
                    frequency = round2(yinResult.frequency),
                    confidence = round2(yinResult.confidence),
                    note = "--",
                    midi = 0.0,
                    cent = 0.0,
                    score = 0.0,
                    status = "too_low",
                ),
                now,
            )
        }

        val smoothedFreq = tracker.smooth(yinResult.frequency)
        val gate = instrumentGate.evaluate(processed, sampleRate, smoothedFreq, yinResult.confidence)
        if (!gate.accepted) {
            return holdOr(PitchResult.voiceRejected(gate.score), now)
        }

        val noteInfo = NoteConverter.fromFrequency(
            frequency = smoothedFreq,
            a4 = a4,
            targetNote = targetNote,
        )
        val scored = PitchScorer.evaluate(smoothedFreq, noteInfo.targetFrequency)
        val result = PitchResult(
            frequency = round2(smoothedFreq),
            confidence = round2(yinResult.confidence),
            note = noteInfo.note,
            midi = round2(noteInfo.midi),
            cent = round1(scored.cent),
            score = scored.score,
            status = "valid",
            instrumentScore = round2(gate.score),
        )
        lastValid = result
        lastValidAtMs = now
        yin.previousFrequency = smoothedFreq
        return result
    }

    private fun holdOr(filtered: PitchResult, now: Long): PitchResult {
        if (filtered.status == "valid") return filtered
        val prev = lastValid
        if (prev != null && now - lastValidAtMs <= holdValidMs) {
            return prev.copy(status = "stabilizing")
        }
        return filtered
    }

    private fun round2(v: Double): Double = (v * 100.0).let { kotlin.math.round(it) / 100.0 }
    private fun round1(v: Double): Double = (v * 10.0).let { kotlin.math.round(it) / 10.0 }
}
