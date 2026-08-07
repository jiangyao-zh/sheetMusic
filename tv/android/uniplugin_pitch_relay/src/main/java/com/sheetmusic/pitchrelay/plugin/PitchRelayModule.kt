package com.sheetmusic.pitchrelay.plugin

import com.sheetmusic.pitchrelay.PitchRelayServer
import io.dcloud.feature.uniapp.annotation.UniJSMethod
import io.dcloud.feature.uniapp.bridge.UniJSCallback
import io.dcloud.feature.uniapp.common.UniModule

/**
 * uni-app 原生模块：在 TV 进程内启动音准 WebSocket 中继。
 *
 * JS:
 *   const relay = uni.requireNativePlugin('PitchRelay')
 *   relay.start({ port: 9091 }, (st) => {})
 *   relay.getStatus((st) => {})
 *   relay.getLanIp((ip) => {})
 *   relay.stop()
 */
class PitchRelayModule : UniModule() {

    @UniJSMethod(uiThread = false)
    fun start(options: Map<String, Any>?, callback: UniJSCallback?) {
        val port = (options?.get("port") as? Number)?.toInt() ?: PitchRelayServer.DEFAULT_PORT
        val status = PitchRelayServer.start(port)
        callback?.invoke(status)
    }

    @UniJSMethod(uiThread = false)
    fun stop(callback: UniJSCallback?) {
        PitchRelayServer.stop()
        callback?.invoke(PitchRelayServer.getStatus())
    }

    @UniJSMethod(uiThread = false)
    fun getStatus(callback: UniJSCallback?) {
        callback?.invoke(PitchRelayServer.getStatus())
    }

    @UniJSMethod(uiThread = false)
    fun getLanIp(callback: UniJSCallback?) {
        callback?.invoke(mapOf("lanIp" to PitchRelayServer.getLanIp()))
    }
}
