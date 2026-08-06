package com.sheetmusic.tuner

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.sheetmusic.pitch.algorithm.PitchAnalyzer
import com.sheetmusic.pitch.audio.AudioPreprocessor
import com.sheetmusic.pitch.audio.AudioRecorder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * 原生调试页：
 * 1) 模拟 A4（不依赖麦克风）
 * 2) 麦克风实测（显示帧计数 + RMS，便于判断是否采到声）
 */
class DebugActivity : AppCompatActivity() {

    private lateinit var statusView: TextView
    private lateinit var micBtn: Button
    private lateinit var simBtn: Button

    private var recorder: AudioRecorder? = null
    private var analyzer: PitchAnalyzer? = null
    private var runningMic = false
    private var runningSim = false

    private val handler = Handler(Looper.getMainLooper())
    private var simPhase = 0.0
    private var simTick = 0
    private var micFrame = 0

    private val simRunnable = object : Runnable {
        override fun run() {
            if (!runningSim) return
            val analyzer = analyzer ?: return
            val bases = doubleArrayOf(196.0, 293.66, 440.0, 659.25)
            val base = bases[(simTick / 45) % bases.size]
            val wobble = sin(simTick / 8.0) * 1.5
            val hz = base + wobble
            val floats = synthesize(hz, AudioRecorder.DEFAULT_BUFFER_SIZE, AudioRecorder.DEFAULT_SAMPLE_RATE)
            val result = analyzer.analyzeFloat(floats)
            render(
                mode = "模拟正弦波",
                frame = simTick,
                rms = AudioPreprocessor.rms(floats),
                peak = floats.maxOfOrNull { abs(it) }?.toDouble() ?: 0.0,
                note = result.note,
                frequency = result.frequency,
                cent = result.cent,
                score = result.score,
                status = result.status,
                confidence = result.confidence,
            )
            simTick++
            handler.postDelayed(this, 66L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = ScrollView(this)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
            setBackgroundColor(0xFF0F1115.toInt())
        }
        statusView = TextView(this).apply {
            text = buildString {
                append("小提琴音准检测 · 调试页\n\n")
                append("重要：\n")
                append("· 说话/打响指通常不会出稳定音符\n")
                append("· 但「帧计数」和「RMS」必须会变\n")
                append("· 帧计数不动 = 麦克风根本没在采\n\n")
                append("步骤：\n")
                append("1. 先点「模拟 A4」看数字是否跳动\n")
                append("2. 再点「麦克风检测」看帧计数是否增加\n")
            }
            setTextColor(0xFFF3F5F7.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        }
        simBtn = Button(this).apply {
            text = "模拟 A4（不需麦克风）"
            setOnClickListener { toggleSim() }
        }
        micBtn = Button(this).apply {
            text = "麦克风检测"
            setOnClickListener { toggleMic() }
        }
        panel.addView(statusView)
        panel.addView(simBtn)
        panel.addView(micBtn)
        root.addView(panel)
        setContentView(root)
    }

    private fun toggleSim() {
        if (runningSim) {
            stopSim()
            return
        }
        stopMic()
        analyzer = PitchAnalyzer(a4 = 440.0)
        runningSim = true
        simTick = 0
        simPhase = 0.0
        simBtn.text = "停止模拟"
        micBtn.isEnabled = false
        handler.post(simRunnable)
    }

    private fun stopSim() {
        runningSim = false
        handler.removeCallbacks(simRunnable)
        simBtn.text = "模拟 A4（不需麦克风）"
        micBtn.isEnabled = true
        if (!runningMic) statusView.text = "已停止模拟"
    }

    private fun toggleMic() {
        if (runningMic) {
            stopMic()
            return
        }
        if (!ensurePermission()) {
            statusView.text = "正在申请麦克风权限…\n请在弹窗中点「允许」。"
            return
        }
        stopSim()
        startMic()
    }

    private fun ensurePermission(): Boolean {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) return true
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1001)
        return false
    }

    private fun startMic() {
        analyzer = PitchAnalyzer(a4 = 440.0, rmsThreshold = 0.005)
        val rec = AudioRecorder()
        recorder = rec
        runningMic = true
        micFrame = 0
        micBtn.text = "停止麦克风"
        simBtn.isEnabled = false
        statusView.text = "麦克风启动中…"
        try {
            rec.start { pcm, count ->
                micFrame++
                val floats = AudioPreprocessor.pcm16ToFloat(pcm, count)
                val rms = AudioPreprocessor.rms(floats)
                val peak = floats.maxOfOrNull { abs(it) }?.toDouble() ?: 0.0
                val r = analyzer?.analyzeFloat(floats)
                render(
                    mode = "麦克风(${rec.activeSourceName})",
                    frame = micFrame,
                    rms = rms,
                    peak = peak,
                    note = r?.note ?: "--",
                    frequency = r?.frequency ?: 0.0,
                    cent = r?.cent ?: 0.0,
                    score = r?.score ?: 0.0,
                    status = r?.status ?: "--",
                    confidence = r?.confidence ?: 0.0,
                )
            }
        } catch (e: Exception) {
            runningMic = false
            micBtn.text = "麦克风检测"
            simBtn.isEnabled = true
            statusView.text = "麦克风启动失败：\n${e.message}\n\n请检查权限，或改用真机。"
        }
    }

    private fun stopMic() {
        runningMic = false
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }
        recorder = null
        micBtn.text = "麦克风检测"
        simBtn.isEnabled = true
        if (!runningSim) statusView.text = "已停止麦克风"
    }

    private fun render(
        mode: String,
        frame: Int,
        rms: Double,
        peak: Double,
        note: String,
        frequency: Double,
        cent: Double,
        score: Double,
        status: String,
        confidence: Double,
    ) {
        val levelBars = (rms * 40).toInt().coerceIn(0, 20)
        val meter = "█".repeat(levelBars) + "░".repeat(20 - levelBars)
        val accent = when {
            status == "valid" && abs(cent) <= 5 -> 0xFF3DD68C.toInt()
            status == "valid" && abs(cent) <= 15 -> 0xFF7EE787.toInt()
            status == "valid" -> 0xFFE3B341.toInt()
            else -> 0xFFF3F5F7.toInt()
        }
        val sb = SpannableStringBuilder()

        fun appendPlain(text: String, sp: Int = 15, color: Int = 0xFF8B93A7.toInt()) {
            val start = sb.length
            sb.append(text)
            sb.setSpan(AbsoluteSizeSpan(sp, true), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(ForegroundColorSpan(color), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        fun appendBig(text: String, sp: Int, color: Int = 0xFFF3F5F7.toInt(), bold: Boolean = true) {
            val start = sb.length
            sb.append(text)
            sb.setSpan(AbsoluteSizeSpan(sp, true), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(ForegroundColorSpan(color), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (bold) {
                sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        appendPlain("模式: $mode\n")
        appendPlain("帧计数: $frame\n")
        appendPlain("音量RMS: ${"%.5f".format(rms)}   Peak: ${"%.5f".format(peak)}\n")
        appendPlain("电平: [$meter]\n\n")

        appendPlain("当前音符\n")
        appendBig("$note\n", 56, accent)
        appendPlain("\n频率\n")
        appendBig("${"%.1f".format(frequency)} Hz\n", 34)
        appendPlain("\n偏差\n")
        val centText = if (status == "no_signal" || status == "idle") "--" else {
            val sign = if (cent > 0) "+" else ""
            "$sign${"%.0f".format(cent)} cent"
        }
        appendBig("$centText\n", 34, accent)

        appendPlain("\n评分: ${"%.0f".format(score)}    状态: $status\n")
        appendPlain("置信度: ${"%.2f".format(confidence)}\n")

        if (mode.startsWith("麦克风")) {
            appendPlain("\n")
            when {
                frame > 5 && rms < 0.001 -> {
                    appendPlain(
                        "诊断: 在采帧，但电平始终为 0\n" +
                            "这不是算法问题，是麦克风输入没进来。\n" +
                            "请检查 macOS/模拟器麦克风路由，或换真机。\n",
                    )
                }
                frame > 5 && rms >= 0.001 && status == "no_signal" -> {
                    appendPlain("诊断: 已采到环境声，但不是稳定乐音。\n可点「模拟 A4」验证算法。\n")
                }
                frame == 0 -> appendPlain("诊断: 还没收到音频帧，等待中…\n")
            }
        }

        statusView.text = sb
    }

    private fun synthesize(frequency: Double, n: Int, sampleRate: Int): FloatArray {
        val out = FloatArray(n)
        val twoPiF = 2.0 * PI * frequency / sampleRate
        for (i in 0 until n) {
            var v = sin(simPhase)
            v += 0.35 * sin(simPhase * 2)
            v += 0.18 * sin(simPhase * 3)
            out[i] = (v * 0.35).toFloat()
            simPhase += twoPiF
            if (simPhase > 2 * PI) simPhase -= 2 * PI
        }
        return out
    }

    override fun onDestroy() {
        stopSim()
        stopMic()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startMic()
        } else if (requestCode == 1001) {
            statusView.text = "未授予麦克风权限。\n设置 → 应用 → 小提琴音准检测 → 权限 → 麦克风 → 允许\n\n或先用「模拟 A4」。"
        }
    }
}
