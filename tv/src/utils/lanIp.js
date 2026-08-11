/**
 * 解析给手机填写的局域网 IP（避免 localhost / 127.0.0.1）。
 */

function isLoopback(host) {
  if (!host) return true
  const h = String(host).trim().toLowerCase()
  return h === 'localhost' || h === '127.0.0.1' || h === '::1' || h === '0.0.0.0'
}

function pickPrivateIp(list) {
  if (!Array.isArray(list) || !list.length) return ''
  return (
    list.find((ip) => String(ip).startsWith('192.168.')) ||
    list.find((ip) => String(ip).startsWith('10.')) ||
    list.find((ip) => /^172\.(1[6-9]|2\d|3[0-1])\./.test(String(ip))) ||
    list[0] ||
    ''
  )
}

function isControlServerHealth(data) {
  return !!(data && data.ok === true && data.port)
}

function requestHealth(host, port) {
  const url = `http://${host}:${port}/health`
  return new Promise((resolve) => {
    if (typeof uni !== 'undefined' && typeof uni.request === 'function') {
      uni.request({
        url,
        method: 'GET',
        timeout: 2000,
        success: (res) => {
          if (res.statusCode === 200 && res.data) resolve(res.data)
          else resolve(null)
        },
        fail: () => resolve(null),
      })
      return
    }
    fetch(url, { method: 'GET' })
      .then((res) => (res.ok ? res.json() : null))
      .then((data) => resolve(data))
      .catch(() => resolve(null))
  })
}

function lanIpFromHealth(data) {
  if (!data || data.ok === false) return ''
  if (data.lanIp && !isLoopback(data.lanIp)) return String(data.lanIp)
  return pickPrivateIp(data.lanIps)
}

function getNativePitchRelay() {
  try {
    if (typeof uni !== 'undefined' && typeof uni.requireNativePlugin === 'function') {
      return uni.requireNativePlugin('PitchRelay')
    }
  } catch (e) {
    // ignore
  }
  return null
}

/**
 * App 内嵌 PitchRelay：直接读 Android 网卡 IP（不依赖 /health）。
 */
export async function resolveNativeLanIp() {
  const native = getNativePitchRelay()
  if (!native || typeof native.getLanIp !== 'function') return ''
  const r = await new Promise((resolve) => {
    native.getLanIp((result) => resolve(result || {}))
  })
  const ip = r.lanIp ? String(r.lanIp).trim() : ''
  return ip && !isLoopback(ip) ? ip : ''
}

/**
 * 从中继 /health 拉取主机网卡 IP（H5 常用）。
 */
export async function fetchRelayLanIp(port = 9091) {
  const hosts = ['127.0.0.1', 'localhost']
  for (const host of hosts) {
    const data = await requestHealth(host, port)
    const lanIp = lanIpFromHealth(data)
    if (lanIp) return lanIp
  }
  return ''
}

/**
 * App 标准基座无 PitchRelay 时：探测宿主机 control-server。
 * WebSocket 必须用 lanIp（与手机相同）；10.0.2.2 在 adb forward 存在时会误转发到模拟器空端口。
 * @returns {Promise<{ wsHost: string, lanIp: string, port: number } | null>}
 */
export async function probeExternalDevRelay(port = 9091) {
  let stored = ''
  try {
    stored = String(uni.getStorageSync('tv_pitch_lan_ip') || '').trim()
  } catch (e) {
    // ignore
  }
  if (stored && !isLoopback(stored)) {
    const data = await requestHealth(stored, port)
    if (isControlServerHealth(data)) {
      const lanIp = lanIpFromHealth(data) || stored
      return { wsHost: lanIp, lanIp, port: Number(data.port) || port }
    }
  }

  for (const host of ['10.0.2.2', '127.0.0.1']) {
    const data = await requestHealth(host, port)
    if (!isControlServerHealth(data)) continue
    const lanIp = lanIpFromHealth(data)
    if (lanIp) {
      return { wsHost: lanIp, lanIp, port: Number(data.port) || port }
    }
  }
  return null
}

/**
 * 探测本机 9091 是否为 control-server（带 /health）。
 * 内嵌 PitchRelay 仅有 WebSocket，此处会返回 false，不能用来否定 native.start()。
 */
export async function verifyEmbeddedRelay(port = 9091) {
  const data = await requestHealth('127.0.0.1', port)
  return isControlServerHealth(data)
}

/**
 * 统一解析展示给手机的 IP。
 * @param {{ preferred?: string, port?: number }} opts
 */
export async function resolvePhoneLanIp(opts = {}) {
  const port = Number(opts.port) || 9091
  const preferred = (opts.preferred || '').trim()
  if (preferred && !isLoopback(preferred)) return preferred

  // App：优先已有 storage / 原生结果
  try {
    const stored = uni.getStorageSync('tv_pitch_lan_ip')
    if (stored && !isLoopback(stored)) return String(stored)
  } catch (e) {
    // ignore
  }

  const fromNative = await resolveNativeLanIp()
  if (fromNative) return fromNative

  // H5：location 非回环时直接用
  try {
    if (typeof location !== 'undefined' && location.hostname && !isLoopback(location.hostname)) {
      return location.hostname
    }
  } catch (e) {
    // ignore
  }

  // 向本机中继查询真实网卡 IP
  const fromRelay = await fetchRelayLanIp(port)
  if (fromRelay) return fromRelay

  return preferred || ''
}

export { isLoopback }
