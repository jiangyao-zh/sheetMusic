import type { PitchResult } from '@/types/pitch'

export type PitchSocketStatus = 'idle' | 'connecting' | 'connected' | 'error'

export interface PitchSocketConfig {
  /** 例如 192.168.1.8 */
  host: string
  /** 默认 9091 */
  port?: number
  /** 与 TV session 一致 */
  session: string
  a4?: number
}

type StatusListener = (status: PitchSocketStatus, detail?: string) => void

/**
 * 手机端 WebSocket 发布者：只发送 PitchResult JSON，不传 PCM。
 * 发送策略：只保留最新一帧，避免背压堆积。
 */
class PitchSocketServiceImpl {
  // uni.connectSocket 返回 SocketTask；用宽松类型避免跨端类型差异
  private socket: { send: Function; close: Function; onOpen: Function; onMessage: Function; onError: Function; onClose: Function } | null = null
  private status: PitchSocketStatus = 'idle'
  private detail = ''
  private config: PitchSocketConfig | null = null
  private seq = 0
  private pending: PitchResult | null = null
  private sending = false
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null
  private heartbeatTimer: ReturnType<typeof setInterval> | null = null
  private manualClose = false
  private listeners = new Set<StatusListener>()

  onStatus(listener: StatusListener) {
    this.listeners.add(listener)
    listener(this.status, this.detail)
    return () => this.listeners.delete(listener)
  }

  getStatus() {
    return { status: this.status, detail: this.detail, config: this.config }
  }

  connect(config: PitchSocketConfig) {
    this.manualClose = false
    this.config = {
      host: config.host.trim(),
      port: config.port ?? 9091,
      session: config.session.trim() || 'default',
      a4: config.a4 ?? 440,
    }
    this.open()
  }

  disconnect() {
    this.manualClose = true
    this.clearTimers()
    this.pending = null
    try {
      this.socket?.close({})
    } catch {
      // ignore
    }
    this.socket = null
    this.setStatus('idle', '已断开')
  }

  setA4(a4: number) {
    if (this.config) this.config.a4 = a4
  }

  /** 检测回调中调用：只缓存最新结果并尽快发送 */
  publish(result: PitchResult) {
    if (!this.config || this.status !== 'connected') return
    this.pending = result
    this.flush()
  }

  private open() {
    if (!this.config) return
    this.clearTimers()
    try {
      this.socket?.close({})
    } catch {
      // ignore
    }

    const { host, port = 9091, session } = this.config
    const url = `ws://${host}:${port}/ws/pitch?session=${encodeURIComponent(session)}&role=phone`
    this.setStatus('connecting', url)

    const task = uni.connectSocket({
      url,
      complete: () => {},
    })
    this.socket = task

    task.onOpen(() => {
      this.setStatus('connected', `已连接 ${host}:${port}`)
      this.startHeartbeat()
      this.flush()
    })

    task.onMessage(() => {
      // ready/pong/ack 暂不处理 UI，保留扩展点
    })

    task.onError((err) => {
      this.setStatus('error', (err && (err as { errMsg?: string }).errMsg) || '连接失败')
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

  private flush() {
    if (!this.socket || this.status !== 'connected' || !this.config) return
    if (this.sending || !this.pending) return

    const result = this.pending
    this.pending = null
    this.sending = true
    this.seq += 1

    const payload = JSON.stringify({
      type: 'pitch',
      seq: this.seq,
      ts: Date.now(),
      a4: this.config.a4 ?? 440,
      result,
    })

    this.socket.send({
      data: payload,
      complete: () => {
        this.sending = false
        // 发送期间又来了更新：立刻再发最新帧
        if (this.pending) this.flush()
      },
    })
  }

  private startHeartbeat() {
    this.stopHeartbeat()
    this.heartbeatTimer = setInterval(() => {
      if (!this.socket || this.status !== 'connected') return
      try {
        this.socket.send({ data: JSON.stringify({ type: 'ping', ts: Date.now() }) })
      } catch {
        // ignore
      }
    }, 15000)
  }

  private stopHeartbeat() {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer)
      this.heartbeatTimer = null
    }
  }

  private scheduleReconnect() {
    if (this.manualClose || this.reconnectTimer) return
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null
      if (!this.manualClose) this.open()
    }, 1500)
  }

  private clearTimers() {
    this.stopHeartbeat()
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
  }

  private setStatus(status: PitchSocketStatus, detail = '') {
    this.status = status
    this.detail = detail
    this.listeners.forEach((fn) => fn(status, detail))
  }
}

export const pitchSocketService = new PitchSocketServiceImpl()
