package com.sheetmusic.pitch

import com.sheetmusic.pitch.algorithm.NoteConverter
import com.sheetmusic.pitch.algorithm.PitchAnalyzer
import com.sheetmusic.pitch.algorithm.PitchScorer
import com.sheetmusic.pitch.algorithm.YinDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class YinDetectorTest {

    private val sampleRate = 44100
    private val yin = YinDetector(sampleRate = sampleRate)

    @Test
    fun detectsPureSineWithinOneCent_violinRange() {
        val freqs = doubleArrayOf(196.0, 293.66, 440.0, 659.25, 987.77, 1318.5, 2093.0)
        for (f in freqs) {
            val samples = synthesize(f, harmonics = false)
            val result = yin.detect(samples)
            assertNotNull("should detect $f Hz", result)
            val cents = abs(1200.0 * kotlin.math.ln(result!!.frequency / f) / kotlin.math.ln(2.0))
            assertTrue("freq=$f detected=${result.frequency} cents=$cents", cents < 1.0)
            assertTrue(result.confidence > 0.8)
        }
    }

    @Test
    fun detectsHarmonicRichToneNearA4() {
        val f = 440.0
        val samples = synthesize(f, harmonics = true)
        val result = yin.detect(samples)
        assertNotNull(result)
        val cents = abs(1200.0 * kotlin.math.ln(result!!.frequency / f) / kotlin.math.ln(2.0))
        assertTrue("cents=$cents", cents < 2.0)
    }

    @Test
    fun noteConverterA440() {
        val midi = NoteConverter.frequencyToMidi(440.0, 440.0)
        assertEquals(69.0, midi, 1e-6)
        assertEquals("A4", NoteConverter.midiToNoteName(69))
        assertEquals(69, NoteConverter.noteNameToMidi("A4"))
    }

    @Test
    fun pitchScorerBands() {
        assertEquals(100.0, PitchScorer.scoreFromCent(3.0), 0.0)
        assertEquals(95.0, PitchScorer.scoreFromCent(10.0), 0.0)
        assertEquals(85.0, PitchScorer.scoreFromCent(20.0), 0.0)
        assertEquals(70.0, PitchScorer.scoreFromCent(40.0), 0.0)
        val eval = PitchScorer.evaluate(442.0, 440.0)
        assertTrue(eval.cent in 7.0..9.0)
        assertEquals(95.0, eval.score, 0.0)
    }

    @Test
    fun analyzerReturnsValidForA4() {
        val analyzer = PitchAnalyzer(sampleRate = sampleRate, a4 = 440.0, rmsThreshold = 0.001)
        val samples = synthesize(440.0, harmonics = true)
        val result = analyzer.analyzeFloat(samples)
        assertEquals("valid", result.status)
        assertEquals("A4", result.note)
        assertTrue(abs(result.cent) < 2.0)
        assertTrue(result.score >= 95.0)
    }

    private fun synthesize(
        frequency: Double,
        harmonics: Boolean,
        durationSec: Double = 4096.0 / sampleRate,
        noise: Double = 0.01,
    ): FloatArray {
        val n = (sampleRate * durationSec).toInt().coerceAtLeast(4096)
        val out = FloatArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            var v = sin(2.0 * PI * frequency * t)
            if (harmonics) {
                v += 0.45 * sin(2.0 * PI * frequency * 2 * t)
                v += 0.25 * sin(2.0 * PI * frequency * 3 * t)
                v += 0.12 * sin(2.0 * PI * frequency * 4 * t)
            }
            v += (Math.random() * 2 - 1) * noise
            out[i] = (v * 0.35).toFloat()
        }
        return out
    }
}
