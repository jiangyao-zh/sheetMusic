/**
 * TV 音准会话：持久化 4 位数字，供手机填写。
 * 旧格式（如 tv-msk...）首次读取时自动迁移。
 */

const STORAGE_SESSION_ID = 'tv_session_id'
const STORAGE_PITCH_SESSION = 'tv_pitch_session'

const FOUR_DIGIT = /^\d{4}$/

export function isFourDigitSession(value) {
  return FOUR_DIGIT.test(String(value || '').trim())
}

export function generateFourDigitSession() {
  return String(1000 + Math.floor(Math.random() * 9000))
}

/**
 * 读取或生成持久化 4 位会话，并同步写入两个 storage key。
 * @param {{ preferred?: string }} [opts]
 * @returns {string}
 */
export function ensurePitchSession(opts = {}) {
  const preferred = String(opts.preferred || '').trim()
  if (isFourDigitSession(preferred)) {
    persistSession(preferred)
    return preferred
  }

  const storedId = String(uni.getStorageSync(STORAGE_SESSION_ID) || '').trim()
  if (isFourDigitSession(storedId)) {
    persistSession(storedId)
    return storedId
  }

  const storedPitch = String(uni.getStorageSync(STORAGE_PITCH_SESSION) || '').trim()
  if (isFourDigitSession(storedPitch)) {
    persistSession(storedPitch)
    return storedPitch
  }

  const next = generateFourDigitSession()
  persistSession(next)
  return next
}

function persistSession(session) {
  uni.setStorageSync(STORAGE_SESSION_ID, session)
  uni.setStorageSync(STORAGE_PITCH_SESSION, session)
}
