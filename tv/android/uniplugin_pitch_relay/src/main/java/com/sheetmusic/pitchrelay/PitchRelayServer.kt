package com.sheetmusic.pitchrelay

import android.util.Log
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TV 内嵌音准 WebSocket 中继。
 *
 * 协议与 tv/tools/control-server.mjs 对齐：
 *   WS /ws/pitch?session=xxx&role=phone|tv
 *   phone 发布 {type:'pitch',...}，转发给同 session 的 tv。
 */
object PitchRelayServer {
    private const val TAG = "PitchRelay"
    const val DEFAULT_PORT = 9091

    private val running = AtomicBoolean(false)
    @Volatile private var port = DEFAULT_PORT
    @Volatile private var server: InnerServer? = null
    @Volatile private var lastError: String = ""

    fun isRunning(): Boolean = running.get()

    fun getPort(): Int = port

    fun getLanIp(): String = LanIp.resolve()

    fun getStatus(): Map<String, Any> = mapOf(
        "running" to running.get(),
        "port" to port,
        "lanIp" to getLanIp(),
        "error" to lastError,
        "rooms" to (server?.roomCount() ?: 0),
    )

    @Synchronized
    fun start(listenPort: Int = DEFAULT_PORT): Map<String, Any> {
        if (running.get() && server != null && port == listenPort) {
            return getStatus()
        }
        stop()
        port = if (listenPort > 0) listenPort else DEFAULT_PORT
        lastError = ""
        return try {
            val s = InnerServer(InetSocketAddress("0.0.0.0", port))
            s.isReuseAddr = true
            // 音准帧很小（~150B）且频率高，Nagle 会攒包造成几十毫秒抖动
            s.isTcpNoDelay = true
            s.start()
            server = s
            running.set(true)
            Log.i(TAG, "listening on 0.0.0.0:$port lan=${getLanIp()}")
            getStatus()
        } catch (e: Exception) {
            lastError = e.message ?: "start failed"
            running.set(false)
            server = null
            Log.e(TAG, "start failed", e)
            getStatus()
        }
    }

    @Synchronized
    fun stop() {
        running.set(false)
        try {
            server?.stop(500)
        } catch (_: Exception) {
        }
        server = null
    }

    private class InnerServer(address: InetSocketAddress) : WebSocketServer(address) {
        private val rooms = ConcurrentHashMap<String, MutableSet<WebSocket>>()

        fun roomCount(): Int = rooms.size

        override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
            val path = handshake.resourceDescriptor ?: "/"
            if (!path.startsWith("/ws/pitch")) {
                conn.close(1008, "only /ws/pitch")
                return
            }
            val query = path.substringAfter('?', "")
            val params = parseQuery(query)
            val session = params["session"]?.ifBlank { null } ?: "default"
            val role = if (params["role"] == "phone") "phone" else "tv"
            conn.setAttachment(Peer(session, role))

            val room = rooms.getOrPut(session) { ConcurrentHashMap.newKeySet() }
            room.add(conn)

            safeSend(
                conn,
                JSONObject()
                    .put("type", "ready")
                    .put("session", session)
                    .put("role", role)
                    .put("peers", room.size)
                    .put("ts", System.currentTimeMillis())
                    .toString(),
            )
            Log.i(TAG, "+ $role session=$session peers=${room.size}")
        }

        override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
            val peer = conn.getAttachment<Peer>() ?: return
            val room = rooms[peer.session] ?: return
            room.remove(conn)
            if (room.isEmpty()) rooms.remove(peer.session)
            Log.i(TAG, "- ${peer.role} session=${peer.session} peers=${room.size}")
        }

        override fun onMessage(conn: WebSocket, message: String) {
            val peer = conn.getAttachment<Peer>() ?: return
            val msg = try {
                JSONObject(message)
            } catch (_: Exception) {
                safeSend(conn, JSONObject().put("type", "error").put("error", "invalid json").toString())
                return
            }

            when (msg.optString("type")) {
                "ping" -> {
                    safeSend(
                        conn,
                        JSONObject().put("type", "pong").put("ts", System.currentTimeMillis()).toString(),
                    )
                }
                "hello" -> {
                    val room = rooms[peer.session]
                    safeSend(
                        conn,
                        JSONObject()
                            .put("type", "ready")
                            .put("session", peer.session)
                            .put("role", peer.role)
                            .put("peers", room?.size ?: 0)
                            .put("ts", System.currentTimeMillis())
                            .toString(),
                    )
                }
                "pitch" -> {
                    if (peer.role != "phone") {
                        safeSend(
                            conn,
                            JSONObject().put("type", "error").put("error", "tv cannot publish pitch").toString(),
                        )
                        return
                    }
                    val envelope = JSONObject()
                        .put("type", "pitch")
                        .put("session", peer.session)
                        .put("seq", msg.optLong("seq", 0))
                        .put("ts", msg.optLong("ts", System.currentTimeMillis()))
                        .put("a4", msg.optDouble("a4", 440.0))
                        .put("result", msg.opt("result"))
                    val n = broadcastToTv(peer.session, envelope.toString(), conn)
                    if (msg.optBoolean("ack", false)) {
                        safeSend(
                            conn,
                            JSONObject()
                                .put("type", "ack")
                                .put("seq", envelope.optLong("seq"))
                                .put("delivered", n)
                                .put("ts", System.currentTimeMillis())
                                .toString(),
                        )
                    }
                }
                "beat" -> {
                    if (peer.role != "tv") {
                        safeSend(
                            conn,
                            JSONObject().put("type", "error").put("error", "only tv can publish beat").toString(),
                        )
                        return
                    }
                    val envelope = JSONObject()
                        .put("type", "beat")
                        .put("session", peer.session)
                        .put("ts", msg.optLong("ts", System.currentTimeMillis()))
                        .put("bpm", msg.optInt("bpm", 0))
                        .put("beatIndex", msg.optInt("beatIndex", 0))
                        .put("beatsPerBar", msg.optInt("beatsPerBar", 4))
                        .put("suppressMs", msg.optLong("suppressMs", 120L))
                    broadcastToPhones(peer.session, envelope.toString(), conn)
                }
                else -> {
                    safeSend(
                        conn,
                        JSONObject()
                            .put("type", "error")
                            .put("error", "unknown type: ${msg.optString("type")}")
                            .toString(),
                    )
                }
            }
        }

        override fun onError(conn: WebSocket?, ex: Exception) {
            Log.w(TAG, "socket error: ${ex.message}")
        }

        override fun onStart() {
            Log.i(TAG, "server started")
        }

        private fun broadcastToTv(session: String, payload: String, except: WebSocket): Int {
            val room = rooms[session] ?: return 0
            var n = 0
            for (client in room) {
                if (client === except) continue
                val peer = client.getAttachment<Peer>() ?: continue
                if (peer.role == "phone") continue
                if (safeSend(client, payload)) n += 1
            }
            return n
        }

        private fun broadcastToPhones(session: String, payload: String, except: WebSocket): Int {
            val room = rooms[session] ?: return 0
            var n = 0
            for (client in room) {
                if (client === except) continue
                val peer = client.getAttachment<Peer>() ?: continue
                if (peer.role != "phone") continue
                if (safeSend(client, payload)) n += 1
            }
            return n
        }

        private fun safeSend(conn: WebSocket, payload: String): Boolean {
            return try {
                if (conn.isOpen) {
                    conn.send(payload)
                    true
                } else {
                    false
                }
            } catch (_: Exception) {
                false
            }
        }

        private fun parseQuery(query: String): Map<String, String> {
            if (query.isBlank()) return emptyMap()
            return query.split('&').mapNotNull { part ->
                val i = part.indexOf('=')
                if (i <= 0) return@mapNotNull null
                val k = decode(part.substring(0, i))
                val v = decode(part.substring(i + 1))
                k to v
            }.toMap()
        }

        private fun decode(s: String): String =
            URLDecoder.decode(s, StandardCharsets.UTF_8.name())
    }

    private data class Peer(val session: String, val role: String)
}
