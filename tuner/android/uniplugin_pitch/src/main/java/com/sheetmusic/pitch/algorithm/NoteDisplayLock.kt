package com.sheetmusic.pitch.algorithm

/**
 * 音名显示锁：小跳变（颤音/半音边界）仍需连续确认；
 * 大跨度高置信换音允许尽快切到目标音，不经过中间半音。
 */
class NoteDisplayLock(
    private val confirmFrames: Int = 2,
    private val largeJumpConfirmFrames: Int = 1,
    private val highConfidence: Double = 0.80,
) {
    private var lockedMidi: Int? = null
    private var pendingMidi: Int? = null
    private var pendingCount = 0

    fun reset() {
        lockedMidi = null
        pendingMidi = null
        pendingCount = 0
    }

    fun resolve(
        frequency: Double,
        a4: Double,
        targetNote: String? = null,
        confidence: Double = 1.0,
    ): NoteConverter.NoteInfo {
        val raw = NoteConverter.fromFrequency(frequency, a4, targetNote)
        val nearest = raw.nearestMidi

        if (lockedMidi == null) {
            lockedMidi = nearest
            return raw
        }

        val locked = lockedMidi!!
        if (nearest == locked) {
            pendingMidi = null
            pendingCount = 0
            return centAgainstLocked(frequency, a4, targetNote, locked)
        }

        if (pendingMidi != nearest) {
            pendingMidi = nearest
            pendingCount = 1
        } else {
            pendingCount++
        }

        val semitones = kotlin.math.abs(nearest - locked)
        val needed = when {
            semitones >= LARGE_JUMP_SEMITONES && confidence >= highConfidence -> largeJumpConfirmFrames
            semitones >= LARGE_JUMP_SEMITONES -> confirmFrames
            else -> confirmFrames
        }
        if (pendingCount >= needed) {
            lockedMidi = nearest
            pendingMidi = null
            pendingCount = 0
            return raw
        }

        return centAgainstLocked(frequency, a4, targetNote, locked)
    }

    private fun centAgainstLocked(
        frequency: Double,
        a4: Double,
        targetNote: String?,
        midi: Int,
    ): NoteConverter.NoteInfo {
        val note = NoteConverter.midiToNoteName(midi)
        val targetMidi = targetNote?.let { NoteConverter.noteNameToMidi(it) } ?: midi
        val targetFrequency = NoteConverter.midiToFrequency(targetMidi.toDouble(), a4)
        val midiVal = NoteConverter.frequencyToMidi(frequency, a4)
        return NoteConverter.NoteInfo(
            frequency = frequency,
            note = note,
            midi = midiVal,
            nearestMidi = midi,
            targetFrequency = targetFrequency,
        )
    }

    companion object {
        /** 3 半音 ≈ 300 cent */
        const val LARGE_JUMP_SEMITONES = 3
    }
}
