package com.sheetmusic.tv

import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.sheetmusic.pitchrelay.PitchRelayServer

/**
 * 独立中继宿主页（无 DCloud SDK 时也可在 TV 上跑通手机直连）。
 * 正式 uni-app 打包后，由 PitchRelay 原生插件在乐谱 App 内启动同一套服务。
 */
class RelayHostActivity : AppCompatActivity() {

    private lateinit var statusView: TextView
    private lateinit var sessionInput: EditText
    private lateinit var portInput: EditText
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 2000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = ScrollView(this)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 64, 64, 64)
            setBackgroundColor(0xFF0F1115.toInt())
        }

        statusView = TextView(this).apply {
            setTextColor(0xFFF3F5F7.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.MONOSPACE
        }

        sessionInput = EditText(this).apply {
            hint = "会话 ID（给手机填）"
            setText(getSharedPreferences("pitch_relay", MODE_PRIVATE).getString("session", "") ?: "")
            setTextColor(0xFFF3F5F7.toInt())
            setHintTextColor(0xFF5C6578.toInt())
            setBackgroundColor(0xFF1B2230.toInt())
            setPadding(28, 24, 28, 24)
        }

        portInput = EditText(this).apply {
            hint = "端口"
            setText("9091")
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setTextColor(0xFFF3F5F7.toInt())
            setHintTextColor(0xFF5C6578.toInt())
            setBackgroundColor(0xFF1B2230.toInt())
            setPadding(28, 24, 28, 24)
        }

        val startBtn = Button(this).apply {
            text = "启动中继"
            setOnClickListener { startRelay() }
        }
        val stopBtn = Button(this).apply {
            text = "停止中继"
            setOnClickListener {
                PitchRelayServer.stop()
                render()
            }
        }

        panel.addView(statusView)
        panel.addView(label("会话"))
        panel.addView(sessionInput)
        panel.addView(label("端口"))
        panel.addView(portInput)
        panel.addView(startBtn)
        panel.addView(stopBtn)
        root.addView(panel)
        setContentView(root)

        if (sessionInput.text.isNullOrBlank()) {
            sessionInput.setText("tv-${System.currentTimeMillis().toString(36)}")
        }
        startRelay()
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(0xFF8B93A7.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setPadding(0, 20, 0, 8)
    }

    private fun startRelay() {
        val port = portInput.text.toString().toIntOrNull() ?: PitchRelayServer.DEFAULT_PORT
        val session = sessionInput.text.toString().trim().ifEmpty { "default" }
        getSharedPreferences("pitch_relay", MODE_PRIVATE)
            .edit()
            .putString("session", session)
            .apply()
        PitchRelayServer.start(port)
        render()
    }

    private fun render() {
        val st = PitchRelayServer.getStatus()
        val running = st["running"] == true
        val lanIp = st["lanIp"]?.toString() ?: "0.0.0.0"
        val port = st["port"]
        val session = sessionInput.text.toString().trim().ifEmpty { "default" }
        val err = st["error"]?.toString().orEmpty()
        statusView.text = buildString {
            append("乐谱 TV · 内嵌音准中继\n\n")
            append(if (running) "状态: 运行中\n" else "状态: 已停止\n")
            append("TV IP: $lanIp\n")
            append("端口: $port\n")
            append("会话: $session\n\n")
            append("手机填写：\n")
            append("  中继 IP = $lanIp\n")
            append("  会话   = $session\n")
            append("  端口   = $port\n\n")
            append("WS: ws://$lanIp:$port/ws/pitch?session=$session&role=phone\n")
            if (err.isNotBlank()) append("\n错误: $err\n")
            append("\n手机与 TV 需同一 Wi-Fi；无需再开电脑 control:server。")
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(refresh)
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    override fun onDestroy() {
        // 保持进程内服务；若需随页停止可改成 PitchRelayServer.stop()
        super.onDestroy()
    }
}
