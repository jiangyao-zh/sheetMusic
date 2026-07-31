package com.sheetmusic.pitch

import com.sheetmusic.pitch.algorithm.NoteConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoteConverterTest {
    @Test
    fun roundTripCommonNotes() {
        assertEquals(55, NoteConverter.noteNameToMidi("G3"))
        assertEquals(62, NoteConverter.noteNameToMidi("D4"))
        assertEquals(69, NoteConverter.noteNameToMidi("A4"))
        assertEquals(76, NoteConverter.noteNameToMidi("E5"))
        assertEquals(61, NoteConverter.noteNameToMidi("C#4"))
        assertEquals(58, NoteConverter.noteNameToMidi("Bb3"))
        assertNull(NoteConverter.noteNameToMidi("H4"))
    }

    @Test
    fun a4Reference442() {
        val midi = NoteConverter.frequencyToMidi(442.0, 442.0)
        assertEquals(69.0, midi, 1e-6)
        val hz = NoteConverter.midiToFrequency(69.0, 442.0)
        assertEquals(442.0, hz, 1e-6)
    }
}
