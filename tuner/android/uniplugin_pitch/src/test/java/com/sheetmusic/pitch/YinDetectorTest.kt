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
    fun transientGateDoesNotBlockFirstInstrumentFrame() {
        val gate = TransientGate()
        val samples = synthesize(440.0, harmonics = true)
        assertTrue(!gate.isTransient(samples))
    }

    /** 起音稳定器需 3 帧一致后才输出 valid */
    private fun analyzeStable(analyzer: PitchAnalyzer, samples: FloatArray) {
        repeat(3) { analyzer.analyzeFloat(samples) }
    }

    @Test
    fun analyzerSuppressesNoteJumpOnAttack() {
        val analyzer = PitchAnalyzer(sampleRate = sampleRate, a4 = 440.0, rmsThreshold = 0.001)
        val jumpSeq = listOf(330.0, 494.0, 392.0, 392.0, 392.0, 392.0)
        val notes = mutableListOf<String>()
        for (hz in jumpSeq) {
            val r = analyzer.analyzeFloat(synthesize(hz, harmonics = true))
            if (r.note != "--") notes.add(r.note)
        }
        assertTrue("attack should not emit multiple notes: $notes", notes.distinct().size <= 1)
    }

    @Test
    fun analyzerJumpsA4ToE5WithoutIntermediateNotes() {
        val analyzer = PitchAnalyzer(sampleRate = sampleRate, a4 = 440.0, rmsThreshold = 0.001)
        val a4 = synthesize(440.0, harmonics = true)
        val e5 = synthesize(659.25, harmonics = true)
        analyzeStable(analyzer, a4)
        val notes = mutableListOf<String>()
        repeat(4) { analyzer.analyzeFloat(a4).note.takeIf { it != "--" }?.let { notes.add(it) } }
        repeat(6) { analyzer.analyzeFloat(e5).note.takeIf { it != "--" }?.let { notes.add(it) } }
        assertTrue("notes=$notes", notes.all { it == "A4" || it == "E5" })
        assertTrue("must reach E5, notes=$notes", notes.contains("E5"))
        assertTrue("must not walk through C#5/D#5, notes=$notes", notes.none { it == "C#5" || it == "D#5" })
    }

    @Test
    fun analyzerSustainedA4StaysA4() {
        val analyzer = PitchAnalyzer(sampleRate = sampleRate, a4 = 440.0, rmsThreshold = 0.001)
        val a4 = synthesize(440.0, harmonics = true)
        analyzeStable(analyzer, a4)
        repeat(8) {
            val r = analyzer.analyzeFloat(a4)
            assertEquals("status=${r.status}", "A4", r.note)
        }
    }

    @Test
    fun analyzerAcceptsViolinRangeTones() {
        val analyzer = PitchAnalyzer(sampleRate = sampleRate, rmsThreshold = 0.001)
        val freqs = doubleArrayOf(196.0, 293.66, 392.0, 440.0, 659.25, 987.77, 1318.5)
        for (f in freqs) {
            val samples = synthesize(f, harmonics = true)
            analyzeStable(analyzer, samples)
            val result = analyzer.analyzeFloat(samples)
            assertTrue("freq=$f status=${result.status}", result.status == "valid")
            assertTrue("freq=$f note=${result.note}", result.note != "--")
            // 模拟停弓静音，便于下一音高重新起音锁定
            analyzer.analyzeFloat(FloatArray(4096))
        }
    }

    @Test
    fun analyzerReturnsValidForA4() {
        val analyzer = PitchAnalyzer(sampleRate = sampleRate, a4 = 440.0, rmsThreshold = 0.001)
        val samples = synthesize(440.0, harmonics = true)
        analyzeStable(analyzer, samples)
        val result = analyzer.analyzeFloat(samples)
        assertEquals("valid", result.status)
        assertEquals("A4", result.note)
        assertTrue(abs(result.cent) < 2.0)
        assertTrue(result.score >= 95.0)
    }

    @Test
    fun analyzerReturnsValidForG4ExtremeWeakFundamental() {
        val analyzer = PitchAnalyzer(sampleRate = sampleRate, a4 = 440.0, rmsThreshold = 0.001)
        val samples = synthesize(392.0, harmonics = true, weakFundamental = true, noise = 0.06, fundScale = 0.04)
        analyzeStable(analyzer, samples)
        val result = analyzer.analyzeFloat(samples)
        assertTrue(
            "status=${result.status} note=${result.note} conf=${result.confidence}",
            result.status == "valid" || result.status == "stabilizing",
        )
        if (result.status == "valid") {
            assertTrue("note=${result.note}", result.note.startsWith("G"))
        }
    }

    @Test
    fun analyzerReturnsValidForG4VeryWeakFundamental() {
        val analyzer = PitchAnalyzer(sampleRate = sampleRate, a4 = 440.0, rmsThreshold = 0.001)
        val samples = synthesize(392.0, harmonics = true, weakFundamental = true, noise = 0.04, fundScale = 0.06)
        analyzeStable(analyzer, samples)
        val result = analyzer.analyzeFloat(samples)
        assertTrue("status=${result.status} note=${result.note} conf=${result.confidence}", result.status == "valid")
        assertTrue("note=${result.note} freq=${result.frequency}", result.note.startsWith("G"))
    }

    @Test
    fun analyzerReturnsValidForG4WeakFundamental() {
        val analyzer = PitchAnalyzer(sampleRate = sampleRate, a4 = 440.0, rmsThreshold = 0.001)
        val samples = synthesize(392.0, harmonics = true, weakFundamental = true)
        analyzeStable(analyzer, samples)
        val result = analyzer.analyzeFloat(samples)
        assertTrue("status=${result.status} note=${result.note}", result.status == "valid")
        assertTrue("note=${result.note} freq=${result.frequency}", result.note.startsWith("G"))
        assertTrue(abs(result.frequency - 392.0) < 8.0)
    }

    @Test
    fun analyzerReturnsValidForG3() {
        val analyzer = PitchAnalyzer(sampleRate = sampleRate, a4 = 440.0, rmsThreshold = 0.001)
        val samples = synthesize(196.0, harmonics = true, weakFundamental = true)
        analyzeStable(analyzer, samples)
        val result = analyzer.analyzeFloat(samples)
        assertTrue("status=${result.status} note=${result.note}", result.status == "valid")
        assertTrue("note=${result.note}", result.note.startsWith("G"))
    }

    @Test
    fun analyzerReturnsValidForG4() {
        val analyzer = PitchAnalyzer(sampleRate = sampleRate, a4 = 440.0, rmsThreshold = 0.001)
        val samples = synthesize(392.0, harmonics = true)
        analyzeStable(analyzer, samples)
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
        val baseline = FloatArray(4096) { i -> (0.008 * sin(i * 0.01)).toFloat() }
        gate.isTransient(baseline)
        val click = synthesizeClick(1500.0)
        assertTrue(gate.isTransient(click))
    }

    @Test
    fun analyzerRejectsVoiceLikeSignal() {
        val analyzer = PitchAnalyzer(sampleRate = sampleRate, rmsThreshold = 0.001)
        val samples = synthesizeVoiceLike(220.0)
        analyzeStable(analyzer, samples)
        val result = analyzer.analyzeFloat(samples)
        assertTrue(
            "status=${result.status}",
            result.status == "voice_rejected" || result.status == "too_low" || result.status == "no_signal",
        )
    }

    private fun synthesizeVoiceLike(f0: Double): FloatArray {
        val n = 4096
        val out = FloatArray(n)
        // 非整数倍共振峰，模拟说话（避免与某基频谐波列重合）
        val formants = doubleArrayOf(730.0, 1090.0, 2440.0)
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            var v = 0.02 * sin(2.0 * PI * f0 * t)
            for (f in formants) {
                v += 0.36 * sin(2.0 * PI * f * t)
            }
            v += sin(i * 0.017 + 850.0) * 0.22
            v += sin(i * 0.031 + 190.0) * 0.18
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
        fundScale: Double = 0.18,
        durationSec: Double = 4096.0 / sampleRate,
        noise: Double = 0.01,
    ): FloatArray {
        val n = (sampleRate * durationSec).toInt().coerceAtLeast(4096)
        val out = FloatArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            var v = sin(2.0 * PI * frequency * t)
            if (harmonics) {
                if (weakFundamental) v *= fundScale
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
