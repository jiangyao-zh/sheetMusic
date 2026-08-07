package com.sheetmusic.pitchrelay

import java.net.Inet4Address
import java.net.NetworkInterface

object LanIp {
    /**
     * 取第一个非回环 IPv4，优先常见私网网段。
     */
    fun resolve(): String {
        val candidates = mutableListOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "0.0.0.0"
            while (interfaces.hasMoreElements()) {
                val nif = interfaces.nextElement()
                if (!nif.isUp || nif.isLoopback) continue
                val addrs = nif.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        candidates += addr.hostAddress ?: continue
                    }
                }
            }
        } catch (_: Exception) {
            return "0.0.0.0"
        }
        return candidates.firstOrNull { it.startsWith("192.168.") }
            ?: candidates.firstOrNull { it.startsWith("10.") }
            ?: candidates.firstOrNull { it.startsWith("172.") }
            ?: candidates.firstOrNull()
            ?: "0.0.0.0"
    }
}
