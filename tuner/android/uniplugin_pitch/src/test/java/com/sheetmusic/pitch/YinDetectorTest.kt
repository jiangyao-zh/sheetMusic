package com.sheetmusic.pitch

import com.sheetmusic.pitch.algorithm.MetronomeGate
import com.sheetmusic.pitch.algorithm.NoteConverter
import com.sheetmusic.pitch.algorithm.PitchAnalyzer
import com.sheetmusic.pitch.algorithm.PitchScorer
import com.sheetmusic.pitch.algorithm.TransientGate
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
        val freqs = doubleArrayOf(196.0, 293.66, 392.0, 440.0, 659.25, 987.77, 1318.5, 2093.0)
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
    fun detectsG4WithWeakFundamentalAndStrongHarmonics() {
        val f = 392.0
        val samples = synthesize(f, harmonics = true, weakFundamental = true)
        val result = yin.detect(samples)
        assertNotNull(result)
        val cents = abs(1200.0 * kotlin.math.ln(result!!.frequency / f) / kotlin.math.ln(2.0))
        assertTrue("G4 weak fundamental cents=$cents", cents < 2.5)
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
        // 连续两帧以满足稳定性
        analyzer.analyzeFloat(samples)
        val result = analyzer.analyzeFloat(samples)
        assertEquals("valid", result.status)
        assertEquals("A4", result.note)
        assertTrue(abs(result.cent) < 2.0)
        assertTrue(result.score >= 95.0)
    }

    @Test
    fun analyzerReturnsValidForG4() {
        val analyzer = PitchAnalyzer(sampleRate = sampleRate, a4 = 440.0, rmsThreshold = 0.001)
        val samples = synthesize(392.0, harmonics = true)
        analyzer.analyzeFloat(samples)
        val result = analyzer.analyzeFloat(samples)
        assertEquals("valid", result.status)
        assertEquals("G4", result.note)
        assertTrue(abs(result.frequency - 392.0) < 5.0)
    }

    @Test
    fun metronomeGateSuppressesDuringBeatWindow() {
        val gate = MetronomeGate(defaultSuppressMs = 120L)
        val t0 = 1_000_000L
        gate.notifyBeat(t0, 120L)
        assertTrue(gate.isSuppressed(t0 + 50))
        assertTrue(!gate.isSuppressed(t0 + 200))
    }

    @Test
    fun analyzerMetronomeSuppressedDuringBeat() {
        val analyzer = PitchAnalyzer(sampleRate = sampleRate, rmsThreshold = 0.001)
        val samples = synthesize(440.0, harmonics = true)
        analyzer.notifyMetronomeBeat(System.currentTimeMillis(), 200L)
        val result = analyzer.analyzeFloat(samples)
        assertEquals("metronome_suppressed", result.status)
    }

    @Test
    fun transientGateDetectsClick() {
        val gate = TransientGate()
        val click = synthesizeClick(1500.0)
        assertTrue(gate.isTransient(click))
    }

    @Test
    fun analyzerRejectsVoiceLikeSignal() {
        val analyzer = PitchAnalyzer(sampleRate = sampleRate, rmsThreshold = 0.001)
        val samples = synthesizeVoiceLike(220.0)
        analyzer.analyzeFloat(samples)
        val result = analyzer.analyzeFloat(samples)
        assertTrue(
            "status=${result.status}",
            result.status == "voice_rejected" || result.status == "too_low" || result.status == "no_signal",
        )
    }

    private fun synthesizeVoiceLike(f0: Double): FloatArray {
        val n = 4096
        val out = FloatArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            // 以共振峰为主、弱周期，模拟说话
            var v = 0.03 * sin(2.0 * PI * f0 * t)
            v += 0.48 * sin(2.0 * PI * 850.0 * t)
            v += 0.38 * sin(2.0 * PI * 1250.0 * t)
            v += 0.22 * sin(2.0 * PI * 2600.0 * t)
            v += sin(i * 0.017 + 850.0) * 0.10
            out[i] = v.toFloat()
        }
        return out
    }

    private fun synthesizeClick(freq: Double): FloatArray {
        val n = 4096
        val out = FloatArray(n)
        for (i in 0 until minOf(80, n)) {
            val t = i.toDouble() / sampleRate
            out[i] = (1.2 * sin(2.0 * PI * freq * t) * kotlin.math.exp(-t * 180)).toFloat()
        }
        return out
    }

    private fun synthesize(
        frequency: Double,
        harmonics: Boolean,
        weakFundamental: Boolean = false,
        durationSec: Double = 4096.0 / sampleRate,
        noise: Double = 0.01,
    ): FloatArray {
        val n = (sampleRate * durationSec).toInt().coerceAtLeast(4096)
        val out = FloatArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            var v = sin(2.0 * PI * frequency * t)
            if (harmonics) {
                if (weakFundamental) v *= 0.18
                v += 0.45 * sin(2.0 * PI * frequency * 2 * t)
                v += 0.25 * sin(2.0 * PI * frequency * 3 * t)
                v += 0.12 * sin(2.0 * PI * frequency * 4 * t)
            }
            v += sin(i * 0.013 + frequency) * noise
            out[i] = (v * 0.35).toFloat()
        }
        return out
    }
}
