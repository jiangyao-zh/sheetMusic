package com.sheetmusic.tuner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.sheetmusic.pitch.algorithm.PitchAnalyzer
import com.sheetmusic.pitch.audio.AudioPreprocessor
import com.sheetmusic.pitch.audio.AudioRecorder
import com.sheetmusic.pitch.keepalive.KeepAliveController
import com.sheetmusic.pitch.model.PitchResult
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * 原生调试页：
 * 1) 模拟 A4（不依赖麦克风）
 * 2) 麦克风实测
 * 3) WebSocket 投屏到 TV（中继 IP / 会话 / 端口）
 */
class DebugActivity : AppCompatActivity() {

    private lateinit var statusView: TextView
    private lateinit var castStatusView: TextView
    private lateinit var hostInput: EditText
    private lateinit var sessionInput: EditText
    private lateinit var portInput: EditText
    private lateinit var castBtn: Button
    private lateinit var micBtn: Button
    private lateinit var simBtn: Button

    private var recorder: AudioRecorder? = null
    private var analyzer: PitchAnalyzer? = null
    private var runningMic = false
    private var runningSim = false
    private var castConnected = false
    /** 用户已点击连接，直到主动断开前视为投屏会话中（含自动重连） */
    private var castActive = false
    private var castWasConnected = false
    private var keepAliveHeld = false

    private val handler = Handler(Looper.getMainLooper())
    private var simPhase = 0.0
    private var simTick = 0

    private lateinit var castClient: PitchCastClient

    private val prefs by lazy { getSharedPreferences("pitch_cast", Context.MODE_PRIVATE) }

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
            publishCast(result)
            render(
                mode = "模拟正弦波",
                rms = AudioPreprocessor.rms(floats),
                peak = floats.maxOfOrNull { abs(it) }?.toDouble() ?: 0.0,
                note = result.note,
                frequency = result.frequency,
                cent = result.cent,
            )
            simTick++
            handler.postDelayed(this, 66L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        KeepAliveController.bindActivity(this)
        // App 打开期间尽量不息屏；检测/投屏时再加 wake lock + 前台服务
        KeepAliveController.setScreenAlwaysOn(this, true)
        ensureNotificationPermission()

        castClient = PitchCastClient(
            onStatus = { status, _ ->
                if (status == "connected") castWasConnected = true
                if (status == "idle") {
                    castWasConnected = false
                    castActive = false
                }
                castConnected = status == "connected"
                castBtn.text = if (castActive) "断开 TV" else "连接 TV"
                updateCastStatusView(status)
                syncKeepAlive()
            },
            onBeat = { _, suppressMs ->
                analyzer?.notifyMetronomeBeat(System.currentTimeMillis(), suppressMs)
            },
        )

        val root = ScrollView(this)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
            setBackgroundColor(0xFF0F1115.toInt())
        }

        statusView = TextView(this).apply {
            text = buildString {
                append("音准检测 · 调试页\n\n")
            }
            setTextColor(0xFFF3F5F7.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        }

        val castTitle = TextView(this).apply {
            text = "投屏到 TV"
            setTextColor(0xFFF3F5F7.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 32, 0, 12)
        }

        hostInput = field("TV 局域网 IP", prefs.getString("host", "") ?: "")
        sessionInput = field("会话（与 TV 一致）", prefs.getString("session", "") ?: "")
        portInput = field("端口", prefs.getString("port", "9091") ?: "9091").apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }

        castStatusView = TextView(this).apply {
            text = formatCastLine("idle")
            setTextColor(castLineColor("idle"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, 8, 0, 8)
            maxLines = 1
        }

        castBtn = Button(this).apply {
            text = "连接 TV"
            setOnClickListener { toggleCast() }
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
        panel.addView(castTitle)
        panel.addView(label("中继 IP"))
        panel.addView(hostInput)
        panel.addView(label("会话"))
        panel.addView(sessionInput)
        panel.addView(label("端口"))
        panel.addView(portInput)
        panel.addView(castStatusView)
        panel.addView(castBtn)
        panel.addView(simBtn)
        panel.addView(micBtn)
        root.addView(panel)
        setContentView(root)
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(0xFF8B93A7.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setPadding(0, 12, 0, 4)
    }

    private fun field(hint: String, value: String) = EditText(this).apply {
        this.hint = hint
        setText(value)
        setTextColor(0xFFF3F5F7.toInt())
        setHintTextColor(0xFF5C6578.toInt())
        setBackgroundColor(0xFF1B2230.toInt())
        setPadding(28, 24, 28, 24)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    private fun toggleCast() {
        if (castActive) {
            castClient.disconnect()
            castConnected = false
            castActive = false
            castWasConnected = false
            updateCastStatusView("idle")
            syncKeepAlive()
            return
        }
        val host = hostInput.text.toString().trim()
        val session = sessionInput.text.toString().trim()
        val port = portInput.text.toString().trim().toIntOrNull() ?: 9091
        if (host.isEmpty()) {
            showCastError("请填写 TV 局域网 IP")
            return
        }
        if (session.isEmpty()) {
            showCastError("请填写与 TV 一致的会话 ID")
            return
        }
        prefs.edit()
            .putString("host", host)
            .putString("session", session)
            .putString("port", port.toString())
            .apply()
        castActive = true
        castWasConnected = false
        castClient.connect(host, port, session, a4 = 440.0)
        syncKeepAlive()
    }

    private fun showCastError(reason: String) {
        castStatusView.text = "投屏：连接失败 · $reason"
        castStatusView.setTextColor(0xFFFF7B72.toInt())
    }

    private fun updateCastStatusView(status: String) {
        castStatusView.text = formatCastLine(status)
        castStatusView.setTextColor(castLineColor(status))
    }

    private fun formatCastLine(status: String): String {
        val label = when (status) {
            "connected" -> "已连接 TV"
            "connecting" -> "连接中…"
            "error" -> if (castActive && castWasConnected) "重连中…" else "连接失败"
            else -> "未连接"
        }
        return "投屏：$label"
    }

    private fun castLineColor(status: String): Int {
        return when {
            status == "connected" -> 0xFF3DD68C.toInt()
            status == "connecting" -> 0xFFE3B341.toInt()
            status == "error" && castActive && castWasConnected -> 0xFFE3B341.toInt()
            status == "error" -> 0xFFFF7B72.toInt()
            else -> 0xFF8B93A7.toInt()
        }
    }

    private fun castDisplayLabel(): String {
        return when {
            castConnected -> "已连接"
            castActive && castWasConnected -> "重连中"
            castActive -> "连接中"
            else -> "未连接"
        }
    }

    private fun publishCast(result: PitchResult) {
        castClient.publish(result)
    }

    private fun syncKeepAlive() {
        val need = runningMic || runningSim || castActive
        if (need && !keepAliveHeld) {
            KeepAliveController.acquire(this, keepScreenOn = true)
            keepAliveHeld = true
        } else if (!need && keepAliveHeld) {
            KeepAliveController.release(this)
            keepAliveHeld = false
        }
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1002,
            )
        }
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
        syncKeepAlive()
        handler.post(simRunnable)
    }

    private fun stopSim() {
        runningSim = false
        handler.removeCallbacks(simRunnable)
        simBtn.text = "模拟 A4（不需麦克风）"
        micBtn.isEnabled = true
        if (!runningMic) statusView.text = "已停止模拟"
        syncKeepAlive()
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
        micBtn.text = "停止麦克风"
        simBtn.isEnabled = false
        statusView.text = "麦克风启动中…"
        syncKeepAlive()
        try {
            rec.start { pcm, count ->
                val floats = AudioPreprocessor.pcm16ToFloat(pcm, count)
                val rms = AudioPreprocessor.rms(floats)
                val peak = floats.maxOfOrNull { abs(it) }?.toDouble() ?: 0.0
                val r = analyzer?.analyzeFloat(floats)
                if (r != null) publishCast(r)
                render(
                    mode = "麦克风(${rec.activeSourceName})",
                    rms = rms,
                    peak = peak,
                    note = r?.note ?: "--",
                    frequency = r?.frequency ?: 0.0,
                    cent = r?.cent ?: 0.0,
                )
            }
        } catch (e: Exception) {
            runningMic = false
            micBtn.text = "麦克风检测"
            simBtn.isEnabled = true
            statusView.text = "麦克风启动失败：\n${e.message}\n\n请检查权限，或改用真机。"
            syncKeepAlive()
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
        syncKeepAlive()
    }

    private fun render(
        mode: String,
        rms: Double,
        peak: Double,
        note: String,
        frequency: Double,
        cent: Double,
    ) {
        val hasPitch = note != "--" && frequency > 0
        val accent = when {
            hasPitch && abs(cent) <= 5 -> 0xFF3DD68C.toInt()
            hasPitch && abs(cent) <= 15 -> 0xFF7EE787.toInt()
            hasPitch -> 0xFFE3B341.toInt()
            else -> 0xFFF3F5F7.toInt()
        }
        val sb = SpannableStringBuilder()
        val titleSp = 15

        fun appendPlain(text: String, sp: Int = titleSp, color: Int = 0xFF8B93A7.toInt()) {
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
        appendPlain("音量RMS: ${"%.5f".format(rms)}   Peak: ${"%.5f".format(peak)}\n")
        appendPlain(
            "投屏: ${castDisplayLabel()}\n\n",
            color = when {
                castConnected -> 0xFF3DD68C.toInt()
                castActive && castWasConnected -> 0xFFE3B341.toInt()
                castActive -> 0xFFE3B341.toInt()
                else -> 0xFF8B93A7.toInt()
            },
        )

        appendPlain("当前音符\n")
        appendBig("$note\n", 56, accent)

        val freqText = if (frequency <= 0) "--" else "${"%.1f".format(frequency)} Hz"
        val centText = if (!hasPitch) "--" else {
            val sign = if (cent > 0) "+" else ""
            "$sign${"%.0f".format(cent)} cent"
        }
        appendPlain("频率 $freqText   偏差 $centText\n", sp = titleSp, color = accent)

        /*
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
        */

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
        castClient.disconnect()
        castConnected = false
        castActive = false
        castWasConnected = false
        KeepAliveController.setScreenAlwaysOn(this, false)
        KeepAliveController.forceRelease(this)
        keepAliveHeld = false
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
            statusView.text = "未授予麦克风权限。\n设置 → 应用 → 音准检测 → 权限 → 麦克风 → 允许\n\n或先用「模拟 A4」。"
        }
    }
}
