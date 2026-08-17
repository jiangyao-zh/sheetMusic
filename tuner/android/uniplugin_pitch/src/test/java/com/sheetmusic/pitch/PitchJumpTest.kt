package com.sheetmusic.pitch

import com.sheetmusic.pitch.algorithm.AttackStabilizer
import com.sheetmusic.pitch.algorithm.NoteDisplayLock
import com.sheetmusic.pitch.algorithm.PitchTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PitchJumpTest {

    @Test
    fun trackerSnapsLargeJumpInsteadOfGliding() {
        val tracker = PitchTracker()
        repeat(5) { tracker.smooth(440.0) }
        val snapped = tracker.smooth(659.25)
        assertTrue("expected snap near 659, got $snapped", abs(snapped - 659.25) < 5.0)
    }

    @Test
    fun trackerKeepsMedianOnSmallJitter() {
        val tracker = PitchTracker()
        repeat(5) { tracker.smooth(440.0) }
        val out = tracker.smooth(442.0)
        assertTrue("small jitter should stay near 440, got $out", abs(out - 440.0) < 3.0)
    }

    @Test
    fun attackStabilizerSnapsAfterReady() {
        val gate = AttackStabilizer()
        repeat(3) { gate.feed(440.0) }
        assertTrue(gate.isReady)
        gate.feed(659.25)
        assertTrue(abs(gate.outputFrequency() - 659.25) < 1.0)
    }

    @Test
    fun attackStabilizerStillWaitsOnWarmup() {
        val gate = AttackStabilizer()
        assertTrue(!gate.feed(330.0))
        assertTrue(!gate.feed(494.0))
        assertTrue(!gate.feed(392.0))
        assertTrue(!gate.isReady)
    }

    @Test
    fun noteLockSwitchesA4ToE5OnFirstHighConfidenceFrame() {
        val lock = NoteDisplayLock()
        lock.resolve(440.0, 440.0, confidence = 0.95)
        val next = lock.resolve(659.25, 440.0, confidence = 0.95)
        assertEquals("E5", next.note)
    }

    @Test
    fun noteLockKeepsA4OnSemitoneBoundaryFlicker() {
        val lock = NoteDisplayLock()
        lock.resolve(440.0, 440.0, confidence = 0.95)
        val flicker = lock.resolve(466.16, 440.0, confidence = 0.95) // A#4, 1 frame
        assertEquals("A4", flicker.note)
    }
}
