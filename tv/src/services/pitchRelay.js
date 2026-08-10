/**
 * TV 内嵌音准中继启动封装。
 * App：调用原生 PitchRelay；H5：依赖电脑 control-server（不启动原生）。
 */

import { isLoopback, probeExternalDevRelay, resolvePhoneLanIp, verifyEmbeddedRelay } from '../utils/lanIp'

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
    this.wsHost = ''
    this.error = ''
    this.nativeAvailable = false
    this.mode = 'unknown'
  }

  getStatus() {
    return {
      running: this.running,
      port: this.port,
      lanIp: this.lanIp,
      wsHost: this.wsHost,
      error: this.error,
      nativeAvailable: this.nativeAvailable,
      mode: this.mode,
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
      const st = await new Promise((resolve) => {
        native.start({ port }, (result) => resolve(result || {}))
      })
      const listenPort = st.port || port
      const embeddedOk = !!st.running && (await verifyEmbeddedRelay(listenPort))
      if (embeddedOk) {
        this.nativeAvailable = true
        this.mode = 'embedded'
        this.wsHost = '127.0.0.1'
        this.running = true
        this.port = listenPort
        this.lanIp = st.lanIp || ''
        this.error = st.error || ''
        if (this.lanIp) uni.setStorageSync(STORAGE_LAN, this.lanIp)
        if (!this.lanIp || isLoopback(this.lanIp)) {
          const fixed = await resolvePhoneLanIp({ preferred: this.lanIp, port: this.port })
          if (fixed) {
            this.lanIp = fixed
            uni.setStorageSync(STORAGE_LAN, this.lanIp)
          }
        }
        return this.getStatus()
      }
    }

    this.nativeAvailable = false
    if (isAppPlus()) {
      const external = await probeExternalDevRelay(port)
      if (external) {
        this.mode = 'external-dev'
        this.running = true
        this.error = ''
        this.wsHost = external.wsHost
        this.port = external.port
        this.lanIp = external.lanIp
        uni.setStorageSync(STORAGE_LAN, this.lanIp)
        return this.getStatus()
      }
      this.mode = 'missing-plugin'
      this.running = false
      this.wsHost = ''
      this.error =
        '未找到 control-server；请先运行 cd tv && npm run control:server。若曾执行 adb forward，请先 adb forward --remove tcp:9091'
      return this.getStatus()
    }

    // H5：假定外部 control-server 已启动，并解析真实局域网 IP
    this.mode = 'external'
    this.running = true
    this.error = ''
    this.wsHost = ''
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
    if (this.mode === 'external-dev') {
      const external = await probeExternalDevRelay(this.port)
      if (external) {
        this.lanIp = external.lanIp
        this.wsHost = external.wsHost
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
