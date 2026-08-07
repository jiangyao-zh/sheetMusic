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

/**
 * 从中继 /health 拉取主机网卡 IP（H5 常用）。
 */
export async function fetchRelayLanIp(port = 9091) {
  const bases = [`http://127.0.0.1:${port}`, `http://localhost:${port}`]
  for (const base of bases) {
    try {
      const res = await fetch(`${base}/health`, { method: 'GET' })
      if (!res.ok) continue
      const data = await res.json()
      if (data && data.lanIp && !isLoopback(data.lanIp)) return String(data.lanIp)
      const picked = pickPrivateIp(data && data.lanIps)
      if (picked) return picked
    } catch (e) {
      // try next
    }
  }
  return ''
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
