package com.sheetmusic.tuner

import android.os.Handler
import android.os.Looper
import com.sheetmusic.pitch.model.PitchResult
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 调试页 WebSocket 发布者：只推送 PitchResult JSON；接收 TV 节拍 beat 事件。
 */
class PitchCastClient(
    private val onStatus: (status: String, detail: String) -> Unit,
    private val onBeat: ((ts: Long, suppressMs: Long) -> Unit)? = null,
) {
    private val main = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var socket: WebSocket? = null
    private var host = ""
    private var port = 9091
    private var session = "default"
    private var a4 = 440.0
    private val connected = AtomicBoolean(false)
    private val manualClose = AtomicBoolean(false)
    private val seq = AtomicLong(0)
    @Volatile private var pending: PitchResult? = null
    @Volatile private var beatGateUntil: Long = 0L

    val isConnected: Boolean get() = connected.get()

    fun connect(host: String, port: Int, session: String, a4: Double = 440.0) {
        manualClose.set(false)
        this.host = host.trim()
        this.port = if (port > 0) port else 9091
        this.session = session.trim().ifEmpty { "default" }
        this.a4 = a4
        open()
    }

    fun disconnect() {
        manualClose.set(true)
        connected.set(false)
        pending = null
        beatGateUntil = 0L
        socket?.close(1000, "bye")
        socket = null
        emit("idle", "")
    }

    fun publish(result: PitchResult) {
        if (!connected.get()) return
        if (System.currentTimeMillis() < beatGateUntil) return
        if (result.status == "metronome_suppressed") return
        pending = result
        flush()
    }

    private fun open() {
        socket?.cancel()
        connected.set(false)
        val url =
            "ws://$host:$port/ws/pitch?session=${java.net.URLEncoder.encode(session, "UTF-8")}&role=phone"
        emit("connecting", "")
        val req = Request.Builder().url(url).build()
        socket = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                connected.set(true)
                emit("connected", "")
                flush()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val msg = JSONObject(text)
                    if (msg.optString("type") == "beat") {
                        val ts = msg.optLong("ts", System.currentTimeMillis())
                        val suppressMs = msg.optLong("suppressMs", 120L)
                        beatGateUntil = maxOf(beatGateUntil, ts + suppressMs)
                        main.post { onBeat?.invoke(ts, suppressMs) }
                    }
                } catch (_: Exception) {
                    // ignore
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                connected.set(false)
                socket = null
                if (!manualClose.get()) {
                    emit("error", "")
                    main.postDelayed({ if (!manualClose.get()) open() }, 1500)
                } else {
                    emit("idle", "")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                connected.set(false)
                socket = null
                if (!manualClose.get()) {
                    emit("error", "")
                    main.postDelayed({ if (!manualClose.get()) open() }, 1500)
                }
            }
        })
    }

    private fun flush() {
        val ws = socket ?: return
        if (!connected.get()) return
        if (System.currentTimeMillis() < beatGateUntil) return
        val result = pending ?: return
        pending = null
        val n = seq.incrementAndGet()
        val payload = JSONObject()
            .put("type", "pitch")
            .put("seq", n)
            .put("ts", System.currentTimeMillis())
            .put("a4", a4)
            .put(
                "result",
                JSONObject()
                    .put("frequency", result.frequency)
                    .put("note", result.note)
                    .put("midi", result.midi)
                    .put("cent", result.cent)
                    .put("score", result.score)
                    .put("confidence", result.confidence)
                    .put("status", result.status),
            )
            .toString()
        ws.send(payload)
    }

    private fun emit(status: String, detail: String) {
        main.post { onStatus(status, detail) }
    }
}
