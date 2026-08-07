/**
 * TV 端音准 WebSocket 订阅。
 * App：默认连本机 127.0.0.1（中继嵌在 TV 进程）；对外展示用 lanIp 给手机填。
 * H5：连页面 host / 外部 control-server。
 */

import { pitchRelay } from './pitchRelay'

const STORAGE_SESSION = 'tv_pitch_session'
const STORAGE_HOST = 'tv_pitch_ws_host'
const STORAGE_PORT = 'tv_pitch_ws_port'
const STORAGE_LAN = 'tv_pitch_lan_ip'

function isAppPlus() {
  try {
    if (typeof plus !== 'undefined') return true
    const info = uni.getSystemInfoSync && uni.getSystemInfoSync()
    return !!(info && (info.uniPlatform === 'app' || info.uniPlatform === 'app-plus'))
  } catch (e) {
    return false
  }
}

function defaultSubscribeHost() {
  // App：中继在本机，订阅走 loopback
  if (isAppPlus()) return '127.0.0.1'
  try {
    if (typeof location !== 'undefined' && location.hostname) {
      return location.hostname
    }
  } catch (e) {
    // ignore
  }
  return uni.getStorageSync(STORAGE_HOST) || '127.0.0.1'
}

class PitchSocketClient {
  constructor() {
    this.socket = null
    this.status = 'idle'
    this.detail = ''
    this.session = uni.getStorageSync(STORAGE_SESSION) || ''
    this.host = defaultSubscribeHost()
    this.port = Number(uni.getStorageSync(STORAGE_PORT)) || 9091
    this.lanIp = uni.getStorageSync(STORAGE_LAN) || ''
    this.latest = null
    this.listeners = new Set()
    this.manualClose = false
    this.reconnectTimer = null
    this.heartbeatTimer = null
    this.lastMsgAt = 0
    this.relayMode = 'unknown'
  }

  onUpdate(fn) {
    this.listeners.add(fn)
    fn(this.snapshot())
    return () => this.listeners.delete(fn)
  }

  snapshot() {
    return {
      status: this.status,
      detail: this.detail,
      latest: this.latest,
      session: this.session,
      host: this.host,
      port: this.port,
      lanIp: this.lanIp,
      relayMode: this.relayMode,
      lastMsgAt: this.lastMsgAt,
      /** 给手机填写的地址（App 为局域网 IP，H5 为页面 host） */
      phoneHost: this.lanIp || this.host,
    }
  }

  ensureSession() {
    if (!this.session || this.session === 'default') {
      const fromList = uni.getStorageSync('tv_session_id')
      this.session = fromList || `tv-${Date.now().toString(36)}`
      uni.setStorageSync(STORAGE_SESSION, this.session)
      uni.setStorageSync('tv_session_id', this.session)
    }
    return this.session
  }

  configure({ host, port, session, lanIp } = {}) {
    if (host) {
      this.host = String(host).trim()
      uni.setStorageSync(STORAGE_HOST, this.host)
    }
    if (port) {
      this.port = Number(port) || 9091
      uni.setStorageSync(STORAGE_PORT, String(this.port))
    }
    if (session) {
      this.session = String(session).trim()
      uni.setStorageSync(STORAGE_SESSION, this.session)
      uni.setStorageSync('tv_session_id', this.session)
    }
    if (lanIp) {
      this.lanIp = String(lanIp).trim()
      uni.setStorageSync(STORAGE_LAN, this.lanIp)
    }
    this.emit()
  }

  async connect(opts = {}) {
    this.configure(opts)
    this.ensureSession()
    this.manualClose = false

    // 先确保本机/外部中继就绪
    const relay = await pitchRelay.start({ port: this.port })
    this.relayMode = relay.mode
    this.lanIp = relay.lanIp || this.lanIp
    if (this.lanIp) uni.setStorageSync(STORAGE_LAN, this.lanIp)

    if (isAppPlus()) {
      // 内嵌中继：TV 页面始终订本机
      this.host = '127.0.0.1'
    } else if (!this.host || this.host === '127.0.0.1') {
      this.host = defaultSubscribeHost()
    }

    if (isAppPlus() && !relay.running) {
      this.setStatus('error', relay.error || '内嵌中继未启动')
      return
    }

    this.open()
  }

  disconnect() {
    this.manualClose = true
    this.clearTimers()
    try {
      this.socket && this.socket.close({})
    } catch (e) {
      // ignore
    }
    this.socket = null
    this.setStatus('idle', '已断开')
  }

  open() {
    this.clearTimers()
    try {
      this.socket && this.socket.close({})
    } catch (e) {
      // ignore
    }

    const url = `ws://${this.host}:${this.port}/ws/pitch?session=${encodeURIComponent(this.session)}&role=tv`
    this.setStatus('connecting', url)

    const task = uni.connectSocket({
      url,
      complete() {},
    })
    this.socket = task

    task.onOpen(() => {
      this.setStatus('connected', `已订阅 ${this.session}`)
      this.startHeartbeat()
    })

    task.onMessage((res) => {
      let msg = null
      try {
        msg = typeof res.data === 'string' ? JSON.parse(res.data) : res.data
      } catch (e) {
        return
      }
      if (!msg || !msg.type) return
      if (msg.type === 'pitch' && msg.result) {
        this.latest = {
          ...msg.result,
          a4: msg.a4,
          seq: msg.seq,
          ts: msg.ts || Date.now(),
        }
        this.lastMsgAt = Date.now()
        this.emit()
        return
      }
      if (msg.type === 'ready') {
        this.setStatus('connected', `已订阅 ${msg.session || this.session}`)
      }
    })

    task.onError((err) => {
      this.setStatus('error', (err && err.errMsg) || '连接失败')
      this.scheduleReconnect()
    })

    task.onClose(() => {
      this.socket = null
      this.stopHeartbeat()
      if (!this.manualClose) {
        this.setStatus('error', '连接断开，重连中…')
        this.scheduleReconnect()
      } else {
        this.setStatus('idle', '已断开')
      }
    })
  }

  startHeartbeat() {
    this.stopHeartbeat()
    this.heartbeatTimer = setInterval(() => {
      if (!this.socket || this.status !== 'connected') return
      try {
        this.socket.send({ data: JSON.stringify({ type: 'ping', ts: Date.now() }) })
      } catch (e) {
        // ignore
      }
    }, 15000)
  }

  stopHeartbeat() {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer)
      this.heartbeatTimer = null
    }
  }

  scheduleReconnect() {
    if (this.manualClose || this.reconnectTimer) return
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null
      if (!this.manualClose) this.open()
    }, 1500)
  }

  clearTimers() {
    this.stopHeartbeat()
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
  }

  setStatus(status, detail) {
    this.status = status
    this.detail = detail || ''
    this.emit()
  }

  emit() {
    const snapshot = this.snapshot()
    this.listeners.forEach((fn) => {
      try {
        fn(snapshot)
      } catch (e) {
        // ignore
      }
    })
  }
}

export const pitchSocket = new PitchSocketClient()
