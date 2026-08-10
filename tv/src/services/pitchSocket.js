/**
 * TV 端音准 WebSocket 订阅。
 * App：默认连本机 127.0.0.1（中继嵌在 TV 进程）；对外展示用 lanIp 给手机填。
 * H5：连页面 host / 外部 control-server。
 */

import { pitchRelay } from './pitchRelay'
import { isLoopback } from '../utils/lanIp'
import { ensurePitchSession, isFourDigitSession } from '../utils/pitchSession'

const STORAGE_SESSION = 'tv_pitch_session'
const STORAGE_HOST = 'tv_pitch_ws_host'
const STORAGE_PORT = 'tv_pitch_ws_port'
const STORAGE_LAN = 'tv_pitch_lan_ip'
const DEFAULT_PORT = 9091
/**
 * 渲染节流：WebView 单帧重绘约百毫秒，逐条 emit 会让 JS 主线程堆积、
 * 画面越落越后。收包只更新数据，按此间隔合并渲染最新一帧。
 */
const RENDER_THROTTLE_MS = 80

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
    this.session = ensurePitchSession()
    this.host = defaultSubscribeHost()
    this.port = Number(uni.getStorageSync(STORAGE_PORT)) || DEFAULT_PORT
    this.lanIp = uni.getStorageSync(STORAGE_LAN) || ''
    this.latest = null
    this.listeners = new Set()
    this.manualClose = false
    this.reconnectTimer = null
    this.heartbeatTimer = null
    this.lastMsgAt = 0
    this.relayMode = 'unknown'
    this.wsHostCandidates = []
    this.wsHostIndex = 0
    this.lastSeq = 0
    this.renderTimer = null
    this.lastRenderAt = 0
  }

  onUpdate(fn) {
    this.listeners.add(fn)
    fn(this.snapshot())
    return () => this.listeners.delete(fn)
  }

  snapshot() {
    const phoneHost =
      (this.lanIp && !isLoopback(this.lanIp) && this.lanIp) ||
      (!isLoopback(this.host) && this.host) ||
      this.lanIp ||
      this.host ||
      ''
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
      /** 给手机填写的局域网地址（避免 localhost） */
      phoneHost,
    }
  }

  ensureSession() {
    this.session = ensurePitchSession({ preferred: this.session })
    return this.session
  }

  configure({ host, port, session, lanIp } = {}) {
    if (host) {
      this.host = String(host).trim()
      uni.setStorageSync(STORAGE_HOST, this.host)
    }
    if (port) {
      this.port = Number(port) || DEFAULT_PORT
      uni.setStorageSync(STORAGE_PORT, String(this.port))
    }
    if (session && isFourDigitSession(session)) {
      this.session = ensurePitchSession({ preferred: session })
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

    if (relay.mode === 'external-dev') {
      // 必须与手机相同：只连 Mac 局域网 IP（勿用 10.0.2.2，adb forward 会劫持）
      const lan = relay.lanIp && !isLoopback(relay.lanIp) ? relay.lanIp : relay.wsHost
      this.wsHostCandidates = lan ? [lan] : ['10.0.2.2']
      this.wsHostIndex = 0
      this.host = this.wsHostCandidates[0]
    } else if (isAppPlus()) {
      // 内嵌中继：TV 页面始终订本机
      this.host = '127.0.0.1'
    } else if (!this.host || this.host === '127.0.0.1') {
      this.host = defaultSubscribeHost()
    }

    if (isAppPlus() && !relay.running) {
      this.setStatus('error', relay.error || '中继未就绪')
      return
    }

    this.open()
  }

  disconnect() {
    this.manualClose = true
    this.clearTimers()
    try {
      this.socket?.close({})
    } catch (e) {
      // ignore
    }
    this.socket = null
    this.setStatus('idle', '已断开')
  }

  /** TV 节拍器：向同 session 手机广播 beat 事件 */
  publishBeat({ bpm, beatIndex, beatsPerBar, suppressMs = 120 } = {}) {
    if (!this.socket || this.status !== 'connected') return false
    const payload = JSON.stringify({
      type: 'beat',
      ts: Date.now(),
      bpm: Number(bpm) || 0,
      beatIndex: Number(beatIndex) || 0,
      beatsPerBar: Number(beatsPerBar) || 4,
      suppressMs: Number(suppressMs) || 120,
    })
    try {
      this.socket.send({ data: payload })
      return true
    } catch (e) {
      return false
    }
  }

  open() {
    this.clearTimers()
    // 手机重连后 seq 会从头计数
    this.lastSeq = 0
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
        const seq = Number(msg.seq) || 0
        // 丢弃迟到/重复帧，只认最新一帧，避免画面回退
        if (seq && this.lastSeq && seq <= this.lastSeq) return
        this.lastSeq = seq
        this.latest = {
          ...msg.result,
          a4: msg.a4,
          seq: msg.seq,
          ts: msg.ts || Date.now(),
        }
        this.lastMsgAt = Date.now()
        this.scheduleRender()
        return
      }
      if (msg.type === 'ready') {
        this.lastSeq = 0
        this.setStatus('connected', `已订阅 ${msg.session || this.session}`)
      }
    })

    task.onError((err) => {
      if (this.tryNextExternalHost()) return
      this.setStatus('error', (err && err.errMsg) || '连接失败')
      this.scheduleReconnect()
    })

    task.onClose(() => {
      this.socket = null
      this.stopHeartbeat()
      if (!this.manualClose) {
        if (this.tryNextExternalHost()) return
        this.setStatus('error', '连接断开，重连中…')
        this.scheduleReconnect()
      } else {
        this.setStatus('idle', '已断开')
      }
    })
  }

  tryNextExternalHost() {
    if (this.relayMode !== 'external-dev' || !this.wsHostCandidates.length) return false
    const next = this.wsHostIndex + 1
    if (next >= this.wsHostCandidates.length) {
      this.wsHostIndex = 0
      return false
    }
    this.wsHostIndex = next
    this.host = this.wsHostCandidates[next]
    setTimeout(() => {
      if (!this.manualClose) this.open()
    }, 300)
    return true
  }

  /** 合并高频音准帧：最快 RENDER_THROTTLE_MS 渲染一次，始终取最新数据 */
  scheduleRender() {
    if (this.renderTimer) return
    const wait = Math.max(0, RENDER_THROTTLE_MS - (Date.now() - this.lastRenderAt))
    this.renderTimer = setTimeout(() => {
      this.renderTimer = null
      this.emit()
    }, wait)
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
    if (this.renderTimer) {
      clearTimeout(this.renderTimer)
      this.renderTimer = null
    }
  }

  setStatus(status, detail) {
    this.status = status
    this.detail = detail || ''
    this.emit()
  }

  emit() {
    this.lastRenderAt = Date.now()
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
