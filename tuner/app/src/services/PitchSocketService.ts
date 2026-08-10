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

export interface MetronomeBeatEvent {
  ts: number
  bpm: number
  beatIndex: number
  beatsPerBar: number
  suppressMs: number
}

type StatusListener = (status: PitchSocketStatus) => void
type BeatListener = (beat: MetronomeBeatEvent) => void

/**
 * 手机端 WebSocket 发布者：只发送 PitchResult JSON，不传 PCM。
 * 同时接收 TV 节拍 beat 事件用于短时分析门控。
 */
class PitchSocketServiceImpl {
  private socket: { send: Function; close: Function; onOpen: Function; onMessage: Function; onError: Function; onClose: Function } | null = null
  private status: PitchSocketStatus = 'idle'
  private config: PitchSocketConfig | null = null
  private seq = 0
  private pending: PitchResult | null = null
  private sending = false
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null
  private heartbeatTimer: ReturnType<typeof setInterval> | null = null
  private manualClose = false
  private listeners = new Set<StatusListener>()
  private beatListeners = new Set<BeatListener>()
  private beatGateUntil = 0

  onStatus(listener: StatusListener) {
    this.listeners.add(listener)
    listener(this.status)
    return () => this.listeners.delete(listener)
  }

  onBeat(listener: BeatListener) {
    this.beatListeners.add(listener)
    return () => this.beatListeners.delete(listener)
  }

  isBeatGateActive(): boolean {
    return Date.now() < this.beatGateUntil
  }

  getStatus() {
    return { status: this.status, config: this.config }
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
    this.beatGateUntil = 0
    try {
      this.socket?.close({})
    } catch {
      // ignore
    }
    this.socket = null
    this.setStatus('idle')
  }

  setA4(a4: number) {
    if (this.config) this.config.a4 = a4
  }

  /** 检测回调中调用：节拍门控期间不发布 */
  publish(result: PitchResult) {
    if (!this.config || this.status !== 'connected') return
    if (this.isBeatGateActive()) return
    if (result.status === 'metronome_suppressed') return
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
    this.setStatus('connecting')

    const task = uni.connectSocket({
      url,
      complete: () => {},
    })
    this.socket = task

    task.onOpen(() => {
      this.setStatus('connected')
      this.startHeartbeat()
      this.flush()
    })

    task.onMessage((res) => {
      let msg: Record<string, unknown> | null = null
      try {
        msg = typeof res.data === 'string' ? JSON.parse(res.data) : res.data
      } catch {
        return
      }
      if (!msg?.type) return
      if (msg.type === 'beat') {
        const beat: MetronomeBeatEvent = {
          ts: Number(msg.ts) || Date.now(),
          bpm: Number(msg.bpm) || 0,
          beatIndex: Number(msg.beatIndex) || 0,
          beatsPerBar: Number(msg.beatsPerBar) || 4,
          suppressMs: Number(msg.suppressMs) || 120,
        }
        this.beatGateUntil = Math.max(this.beatGateUntil, beat.ts + beat.suppressMs)
        this.beatListeners.forEach((fn) => fn(beat))
      }
    })

    task.onError((err) => {
      this.setStatus('error')
      this.scheduleReconnect()
    })

    task.onClose(() => {
      this.socket = null
      this.stopHeartbeat()
      if (!this.manualClose) {
        this.setStatus('error')
        this.scheduleReconnect()
      } else {
        this.setStatus('idle')
      }
    })
  }

  private flush() {
    if (!this.socket || this.status !== 'connected' || !this.config) return
    if (this.sending || !this.pending) return
    if (this.isBeatGateActive()) return

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

  private setStatus(status: PitchSocketStatus) {
    this.status = status
    this.listeners.forEach((fn) => fn(status))
  }
}

export const pitchSocketService = new PitchSocketServiceImpl()
