package com.sheetmusic.pitch.algorithm

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 频率 ↔ MIDI ↔ 音名（A4 可配置）。
 */
object NoteConverter {
    private val NOTE_NAMES = arrayOf(
        "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B",
    )

    data class NoteInfo(
        val frequency: Double,
        val note: String,
        val midi: Double,
        val nearestMidi: Int,
        val targetFrequency: Double,
    )

    fun frequencyToMidi(frequency: Double, a4: Double = 440.0): Double {
        require(frequency > 0 && a4 > 0)
        return 69.0 + 12.0 * (ln(frequency / a4) / ln(2.0))
    }

    fun midiToFrequency(midi: Double, a4: Double = 440.0): Double {
        return a4 * 2.0.pow((midi - 69.0) / 12.0)
    }

    fun midiToNoteName(midi: Int): String {
        val name = NOTE_NAMES[((midi % 12) + 12) % 12]
        val octave = midi / 12 - 1
        return "$name$octave"
    }

    fun noteNameToMidi(note: String): Int? {
        val trimmed = note.trim()
        if (trimmed.length < 2) return null
        val last = trimmed.last()
        if (!last.isDigit() && last != '-') return null
        // 支持 A4 / A#4 / Bb3
        val octaveStart = trimmed.indexOfFirst { it.isDigit() || it == '-' }
        if (octaveStart <= 0) return null
        val pitch = trimmed.substring(0, octaveStart).uppercase().replace('♭', 'B')
        val octave = trimmed.substring(octaveStart).toIntOrNull() ?: return null
        val semitone = when (pitch) {
            "C" -> 0
            "C#", "DB" -> 1
            "D" -> 2
            "D#", "EB" -> 3
            "E" -> 4
            "F" -> 5
            "F#", "GB" -> 6
            "G" -> 7
            "G#", "AB" -> 8
            "A" -> 9
            "A#", "BB" -> 10
            "B" -> 11
            else -> return null
        }
        return (octave + 1) * 12 + semitone
    }

    fun fromFrequency(frequency: Double, a4: Double = 440.0, targetNote: String? = null): NoteInfo {
        val midi = frequencyToMidi(frequency, a4)
        val nearest = midi.roundToInt()
        val note = midiToNoteName(nearest)
        val targetMidi = targetNote?.let { noteNameToMidi(it) } ?: nearest
        val targetFrequency = midiToFrequency(targetMidi.toDouble(), a4)
        return NoteInfo(
            frequency = frequency,
            note = note,
            midi = midi,
            nearestMidi = nearest,
            targetFrequency = targetFrequency,
        )
    }
}
