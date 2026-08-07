/**
 * TV 内嵌音准中继启动封装。
 * App：调用原生 PitchRelay；H5：依赖电脑 control-server（不启动原生）。
 */

import { isLoopback, resolvePhoneLanIp } from '../utils/lanIp'

const STORAGE_LAN = 'tv_pitch_lan_ip'
const STORAGE_PORT = 'tv_pitch_ws_port'

function getNative() {
  try {
    if (typeof uni !== 'undefined' && typeof uni.requireNativePlugin === 'function') {
      return uni.requireNativePlugin('PitchRelay')
    }
  } catch (e) {
    // ignore
  }
  return null
}

function isAppPlus() {
  try {
    if (typeof plus !== 'undefined') return true
    const info = uni.getSystemInfoSync && uni.getSystemInfoSync()
    return !!(info && (info.uniPlatform === 'app' || info.uniPlatform === 'app-plus'))
  } catch (e) {
    return false
  }
}

class PitchRelayService {
  constructor() {
    this.running = false
    this.port = Number(uni.getStorageSync(STORAGE_PORT)) || 9091
    this.lanIp = uni.getStorageSync(STORAGE_LAN) || ''
    this.error = ''
    this.nativeAvailable = false
  }

  getStatus() {
    return {
      running: this.running,
      port: this.port,
      lanIp: this.lanIp,
      error: this.error,
      nativeAvailable: this.nativeAvailable,
      mode: this.nativeAvailable ? 'embedded' : isAppPlus() ? 'missing-plugin' : 'external',
    }
  }

  /**
   * 启动中继。App 成功后返回局域网 IP；H5 会尽量解析真实内网 IP（非 localhost）。
   */
  async start(opts = {}) {
    const port = Number(opts.port) || this.port || 9091
    this.port = port
    uni.setStorageSync(STORAGE_PORT, String(port))

    const native = getNative()
    if (native && typeof native.start === 'function') {
      this.nativeAvailable = true
      const st = await new Promise((resolve) => {
        native.start({ port }, (result) => resolve(result || {}))
      })
      this.running = !!st.running
      this.port = st.port || port
      this.lanIp = st.lanIp || ''
      this.error = st.error || ''
      // 模拟器可能是 10.0.2.15；仍写入。H5/电脑侧另有 resolve
      if (this.lanIp) uni.setStorageSync(STORAGE_LAN, this.lanIp)
      // App 若拿到回环/空，再尝试补一次
      if (!this.lanIp || isLoopback(this.lanIp)) {
        const fixed = await resolvePhoneLanIp({ preferred: this.lanIp, port: this.port })
        if (fixed) {
          this.lanIp = fixed
          uni.setStorageSync(STORAGE_LAN, this.lanIp)
        }
      }
      return this.getStatus()
    }

    this.nativeAvailable = false
    if (isAppPlus()) {
      this.running = false
      this.error =
        '未集成 PitchRelay 原生插件；请使用 tv/android RelayHost 或集成 nativeplugins/PitchRelay'
      const fixed = await resolvePhoneLanIp({ port: this.port })
      if (fixed) {
        this.lanIp = fixed
        uni.setStorageSync(STORAGE_LAN, this.lanIp)
      }
      return this.getStatus()
    }

    // H5：假定外部 control-server 已启动，并解析真实局域网 IP
    this.running = true
    this.error = ''
    let preferred = ''
    try {
      if (typeof location !== 'undefined' && location.hostname) {
        preferred = location.hostname
      }
    } catch (e) {
      // ignore
    }
    this.lanIp = await resolvePhoneLanIp({ preferred, port: this.port })
    if (this.lanIp) uni.setStorageSync(STORAGE_LAN, this.lanIp)
    return this.getStatus()
  }

  stop() {
    const native = getNative()
    if (native && typeof native.stop === 'function') {
      native.stop(() => {})
    }
    this.running = false
    return this.getStatus()
  }

  async refreshLanIp() {
    const native = getNative()
    if (native && typeof native.getLanIp === 'function') {
      const r = await new Promise((resolve) => {
        native.getLanIp((result) => resolve(result || {}))
      })
      if (r.lanIp && !isLoopback(r.lanIp)) {
        this.lanIp = r.lanIp
        uni.setStorageSync(STORAGE_LAN, this.lanIp)
        return this.lanIp
      }
    }
    const fixed = await resolvePhoneLanIp({ preferred: this.lanIp, port: this.port })
    if (fixed) {
      this.lanIp = fixed
      uni.setStorageSync(STORAGE_LAN, this.lanIp)
    }
    return this.lanIp
  }
}

export const pitchRelay = new PitchRelayService()
