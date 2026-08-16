package com.sheetmusic.pitch.algorithm

/**
 * 音名显示锁：防止相邻帧在音符边界附近来回切换（如 G4↔G#4）。
 */
class NoteDisplayLock(
    private val confirmFrames: Int = 2,
) {
    private var lockedMidi: Int? = null
    private var pendingMidi: Int? = null
    private var pendingCount = 0

    fun reset() {
        lockedMidi = null
        pendingMidi = null
        pendingCount = 0
    }

    fun resolve(frequency: Double, a4: Double, targetNote: String?): NoteConverter.NoteInfo {
        val raw = NoteConverter.fromFrequency(frequency, a4, targetNote)
        val nearest = raw.nearestMidi

        if (lockedMidi == null) {
            lockedMidi = nearest
            return raw
        }

        if (nearest == lockedMidi) {
            pendingMidi = null
            pendingCount = 0
            return centAgainstLocked(frequency, a4, targetNote, lockedMidi!!)
        }

        if (pendingMidi != nearest) {
            pendingMidi = nearest
            pendingCount = 1
        } else {
            pendingCount++
        }

        if (pendingCount >= confirmFrames) {
            lockedMidi = nearest
            pendingMidi = null
            pendingCount = 0
            return raw
        }

        return centAgainstLocked(frequency, a4, targetNote, lockedMidi!!)
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
}
