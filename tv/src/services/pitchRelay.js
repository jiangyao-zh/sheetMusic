/**
 * TV 内嵌音准中继启动封装。
 * App：调用原生 PitchRelay；H5：依赖电脑 control-server（不启动原生）。
 */

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
   * 启动中继。App 成功后返回局域网 IP；H5 返回空 IP（用页面 host）。
   */
  start(opts = {}) {
    const port = Number(opts.port) || this.port || 9091
    this.port = port
    uni.setStorageSync(STORAGE_PORT, String(port))

    const native = getNative()
    if (native && typeof native.start === 'function') {
      this.nativeAvailable = true
      return new Promise((resolve) => {
        native.start({ port }, (st) => {
          this.running = !!(st && st.running)
          this.port = (st && st.port) || port
          this.lanIp = (st && st.lanIp) || ''
          this.error = (st && st.error) || ''
          if (this.lanIp) uni.setStorageSync(STORAGE_LAN, this.lanIp)
          resolve(this.getStatus())
        })
      })
    }

    this.nativeAvailable = false
    if (isAppPlus()) {
      this.running = false
      this.error = '未集成 PitchRelay 原生插件；请使用 tv/android RelayHost 或集成 nativeplugins/PitchRelay'
      return Promise.resolve(this.getStatus())
    }

    // H5：假定外部 control-server 已启动
    this.running = true
    this.error = ''
    try {
      if (typeof location !== 'undefined' && location.hostname) {
        this.lanIp = location.hostname
        uni.setStorageSync(STORAGE_LAN, this.lanIp)
      }
    } catch (e) {
      // ignore
    }
    return Promise.resolve(this.getStatus())
  }

  stop() {
    const native = getNative()
    if (native && typeof native.stop === 'function') {
      native.stop(() => {})
    }
    this.running = false
    return this.getStatus()
  }

  refreshLanIp() {
    const native = getNative()
    if (native && typeof native.getLanIp === 'function') {
      return new Promise((resolve) => {
        native.getLanIp((r) => {
          this.lanIp = (r && r.lanIp) || this.lanIp
          if (this.lanIp) uni.setStorageSync(STORAGE_LAN, this.lanIp)
          resolve(this.lanIp)
        })
      })
    }
    return Promise.resolve(this.lanIp)
  }
}

export const pitchRelay = new PitchRelayService()
