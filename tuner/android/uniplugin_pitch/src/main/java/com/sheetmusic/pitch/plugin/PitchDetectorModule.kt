package com.sheetmusic.pitch.plugin

import android.app.Activity
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import com.sheetmusic.pitch.algorithm.PitchAnalyzer
import com.sheetmusic.pitch.audio.AudioRecorder
import com.sheetmusic.pitch.keepalive.KeepAliveController
import com.sheetmusic.pitch.model.PitchResult
import io.dcloud.feature.uniapp.annotation.UniJSMethod
import io.dcloud.feature.uniapp.bridge.UniJSCallback
import io.dcloud.feature.uniapp.common.UniModule

/**
 * uni-app 原生模块桥接。
 *
 * JS:
 *   const pitch = uni.requireNativePlugin('PitchDetector')
 *   pitch.start({ a4: 440 }, (data) => {})
 *   pitch.setTargetNote('A4')
 *   pitch.stop()
 */
class PitchDetectorModule : UniModule() {

    private var recorder: AudioRecorder? = null
    private var analyzer: PitchAnalyzer? = null
    private var resultCallback: UniJSCallback? = null
    private var analyzeThread: HandlerThread? = null
    private var analyzeHandler: Handler? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var keepAliveHeld = false

    @UniJSMethod(uiThread = true)
    fun start(options: Map<String, Any>?, callback: UniJSCallback?) {
        stopInternal()
        resultCallback = callback

        val sampleRate = (options?.get("sampleRate") as? Number)?.toInt()
            ?: AudioRecorder.DEFAULT_SAMPLE_RATE
        val bufferSize = (options?.get("bufferSize") as? Number)?.toInt()
            ?: AudioRecorder.DEFAULT_BUFFER_SIZE
        val a4 = (options?.get("a4") as? Number)?.toDouble() ?: 440.0

        val ctx = mUniSDKInstance ?: return
        if (ctx is Activity) {
            KeepAliveController.bindActivity(ctx)
        }
        KeepAliveController.acquire(ctx, keepScreenOn = true)
        keepAliveHeld = true

        analyzer = PitchAnalyzer(sampleRate = sampleRate, a4 = a4)
        val thread = HandlerThread("pitch-analyze").also { it.start() }
        analyzeThread = thread
        analyzeHandler = Handler(thread.looper)
        callback?.invokeAndKeepAlive(PitchResult.detecting().toMap())

        try {
            val rec = AudioRecorder(sampleRate = sampleRate, bufferSize = bufferSize)
            recorder = rec
            rec.start { pcm, count ->
                analyzeHandler?.post {
                    val result = analyzer?.analyzePcm16(pcm, count) ?: PitchResult.noSignal()
                    val payload = result.toMap()
                    mainHandler.post {
                        resultCallback?.invokeAndKeepAlive(payload)
                    }
                }
            }
        } catch (e: Exception) {
            callback?.invokeAndKeepAlive(
                mapOf(
                    "frequency" to 0.0,
                    "confidence" to 0.0,
                    "note" to "--",
                    "midi" to 0.0,
                    "cent" to 0.0,
                    "score" to 0.0,
                    "status" to "idle",
                    "error" to (e.message ?: "start failed"),
                ),
            )
            stopInternal()
        }
    }

    @UniJSMethod(uiThread = true)
    fun stop() {
        stopInternal()
        resultCallback?.invoke(
            PitchResult.idle().toMap(),
        )
        resultCallback = null
    }

    @UniJSMethod(uiThread = true)
    fun setTargetNote(note: Any?) {
        val value = when (note) {
            null -> null
            is String -> note.ifBlank { null }
            else -> note.toString().ifBlank { null }
        }
        analyzer?.targetNote = value
    }

    /** TV 节拍同步：在 suppressMs 内暂停音高分析 */
    @UniJSMethod(uiThread = false)
    fun notifyMetronomeBeat(options: Map<String, Any>?) {
        val ts = (options?.get("ts") as? Number)?.toLong() ?: System.currentTimeMillis()
        val suppressMs = (options?.get("suppressMs") as? Number)?.toLong() ?: 120L
        analyzer?.notifyMetronomeBeat(ts, suppressMs)
    }

    private fun stopInternal() {
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }
        recorder = null
        analyzer?.reset()
        analyzeHandler?.removeCallbacksAndMessages(null)
        analyzeThread?.quitSafely()
        analyzeHandler = null
        analyzeThread = null
        mainHandler.removeCallbacksAndMessages(null)
        if (keepAliveHeld) {
            val ctx = mUniSDKInstance
            if (ctx != null) {
                KeepAliveController.release(ctx)
            }
            keepAliveHeld = false
        }
    }
}
