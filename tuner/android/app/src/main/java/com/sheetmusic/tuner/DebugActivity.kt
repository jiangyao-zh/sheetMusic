package com.sheetmusic.tuner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.sheetmusic.pitch.algorithm.PitchAnalyzer
import com.sheetmusic.pitch.audio.AudioRecorder

/**
 * 无 DCloud 离线 SDK 时的原生调试页：直接驱动 AudioRecorder + PitchAnalyzer。
 * 集成 uni 离线 SDK 后可删除，改用 PandoraEntry + 前端页面。
 */
class DebugActivity : AppCompatActivity() {

    private lateinit var statusView: TextView
    private lateinit var toggleBtn: Button
    private var recorder: AudioRecorder? = null
    private var analyzer: PitchAnalyzer? = null
    private var running = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = ScrollView(this)
        val panel = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
            setBackgroundColor(0xFF0F1115.toInt())
        }
        statusView = TextView(this).apply {
            text = "小提琴音准检测\n原生调试模式\n\n点击开始后对着琴发声"
            setTextColor(0xFFF3F5F7.toInt())
            textSize = 18f
        }
        toggleBtn = Button(this).apply {
            text = "开始检测"
            setOnClickListener { toggle() }
        }
        panel.addView(statusView)
        panel.addView(toggleBtn)
        root.addView(panel)
        setContentView(root)
    }

    private fun toggle() {
        if (running) {
            stopDetect()
        } else {
            if (!ensurePermission()) return
            startDetect()
        }
    }

    private fun ensurePermission(): Boolean {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) return true
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1001)
        return false
    }

    private fun startDetect() {
        analyzer = PitchAnalyzer(a4 = 440.0)
        val rec = AudioRecorder()
        recorder = rec
        running = true
        toggleBtn.text = "停止检测"
        rec.start { pcm, count ->
            val r = analyzer?.analyzePcm16(pcm, count)
            statusView.text = buildString {
                append("小提琴音准检测\n原生调试模式\n\n")
                append("音符: ${r?.note ?: "--"}\n")
                append("频率: ${r?.frequency ?: 0} Hz\n")
                append("偏差: ${r?.cent ?: 0} cent\n")
                append("评分: ${r?.score ?: 0}\n")
                append("状态: ${r?.status ?: "--"}\n")
                append("置信度: ${r?.confidence ?: 0}")
            }
        }
    }

    private fun stopDetect() {
        running = false
        recorder?.stop()
        recorder = null
        toggleBtn.text = "开始检测"
        statusView.text = "已停止"
    }

    override fun onDestroy() {
        stopDetect()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startDetect()
        }
    }
}
