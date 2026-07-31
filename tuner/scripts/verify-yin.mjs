#!/usr/bin/env node
/**
 * 纯 JS 复刻 Kotlin YIN 管线，验证合成正弦波误差 < 1 cent。
 * 不依赖 Android SDK，可在任意 Node 环境运行：
 *   node tuner/scripts/verify-yin.mjs
 */

const SAMPLE_RATE = 44100
const THRESHOLD = 0.1
const MIN_F = 196
const MAX_F = 2637

function synthesize(frequency, { harmonics = false, n = 4096, noise = 0.01 } = {}) {
  const out = new Float64Array(n)
  for (let i = 0; i < n; i++) {
    const t = i / SAMPLE_RATE
    let v = Math.sin(2 * Math.PI * frequency * t)
    if (harmonics) {
      v += 0.45 * Math.sin(2 * Math.PI * frequency * 2 * t)
      v += 0.25 * Math.sin(2 * Math.PI * frequency * 3 * t)
      v += 0.12 * Math.sin(2 * Math.PI * frequency * 4 * t)
    }
    v += (Math.random() * 2 - 1) * noise
    out[i] = v * 0.35
  }
  return out
}

function removeDc(samples) {
  let sum = 0
  for (const s of samples) sum += s
  const mean = sum / samples.length
  return samples.map((s) => s - mean)
}

function normalize(samples) {
  let peak = 0
  for (const s of samples) peak = Math.max(peak, Math.abs(s))
  if (peak < 1e-8) return samples.slice()
  return samples.map((s) => s / peak)
}

function hanning(samples) {
  const n = samples.length
  const denom = n - 1
  return samples.map((s, i) => s * (0.5 * (1 - Math.cos((2 * Math.PI * i) / denom))))
}

function yinDetect(raw) {
  // 与 Kotlin PitchAnalyzer 一致：YIN 前仅去直流
  const samples = removeDc(Array.from(raw))
  const n = samples.length
  const tauMin = Math.max(2, Math.floor(SAMPLE_RATE / MAX_F))
  const tauMax = Math.min(Math.floor(n / 2), Math.floor(SAMPLE_RATE / MIN_F))
  const diff = new Float64Array(tauMax + 1)
  for (let tau = 1; tau <= tauMax; tau++) {
    let sum = 0
    const limit = n - tau
    for (let j = 0; j < limit; j++) {
      const d = samples[j] - samples[j + tau]
      sum += d * d
    }
    diff[tau] = sum
  }
  const cmnd = new Float64Array(tauMax + 1)
  cmnd[0] = 1
  let running = 0
  for (let tau = 1; tau <= tauMax; tau++) {
    running += diff[tau]
    cmnd[tau] = running > 0 ? (diff[tau] * tau) / running : 1
  }

  let tauEst = -1
  for (let tau = tauMin; tau < tauMax; tau++) {
    if (cmnd[tau] < THRESHOLD) {
      while (tau + 1 < tauMax && cmnd[tau + 1] < cmnd[tau]) tau++
      tauEst = tau
      break
    }
  }
  if (tauEst < 0) {
    let best = tauMin
    let bestVal = cmnd[tauMin]
    for (let t = tauMin + 1; t <= tauMax; t++) {
      if (cmnd[t] < bestVal) {
        bestVal = cmnd[t]
        best = t
      }
    }
    if (bestVal >= 0.3) return null
    tauEst = best
  }

  const s0 = cmnd[tauEst - 1]
  const s1 = cmnd[tauEst]
  const s2 = cmnd[tauEst + 1]
  const denom = s0 - 2 * s1 + s2
  const delta = Math.abs(denom) < 1e-12 ? 0 : (0.5 * (s0 - s2)) / denom
  const tau0 = tauEst + delta
  const tau = refineFractionalTau(samples, tau0)
  const frequency = SAMPLE_RATE / tau
  const confidence = Math.max(0, Math.min(1, 1 - cmnd[tauEst]))
  return { frequency, confidence }
}

function fractionalDifference(samples, tau) {
  const tauFloor = Math.floor(tau)
  const frac = tau - tauFloor
  if (tauFloor < 1 || tauFloor + 1 >= samples.length) return Number.POSITIVE_INFINITY
  let sum = 0
  const limit = samples.length - tauFloor - 1
  for (let j = 0; j < limit; j++) {
    const delayed = samples[j + tauFloor] * (1 - frac) + samples[j + tauFloor + 1] * frac
    const d = samples[j] - delayed
    sum += d * d
  }
  return sum
}

function refineFractionalTau(samples, tau) {
  if (tau < 2) return tau
  let bestTau = tau
  let bestVal = fractionalDifference(samples, tau)
  for (let t = tau - 0.75; t <= tau + 0.75 + 1e-12; t += 0.01) {
    const v = fractionalDifference(samples, t)
    if (v < bestVal) {
      bestVal = v
      bestTau = t
    }
  }
  return bestTau
}

function cents(a, b) {
  return Math.abs((1200 * Math.log(a / b)) / Math.LN2)
}

function midiFromHz(f, a4 = 440) {
  return 69 + 12 * Math.log2(f / a4)
}

function scoreFromCent(cent) {
  const abs = Math.abs(cent)
  if (abs <= 5) return 100
  if (abs <= 15) return 95
  if (abs <= 30) return 85
  if (abs <= 50) return 70
  return Math.max(0, 60 - (abs - 50) / 2)
}

const freqs = [196.0, 293.66, 440.0, 659.25, 987.77, 1318.5, 2093.0]
let failed = 0

console.log('YIN 精度验证（合成正弦波，目标 < 1 cent）\n')
for (const f of freqs) {
  const r = yinDetect(synthesize(f, { harmonics: false, noise: 0.005 }))
  if (!r) {
    console.log(`FAIL  ${f} Hz  ->  null`)
    failed++
    continue
  }
  const c = cents(r.frequency, f)
  const ok = c < 1.0
  if (!ok) failed++
  console.log(
    `${ok ? 'OK  ' : 'FAIL'} ${f.toFixed(2).padStart(8)} Hz -> ${r.frequency.toFixed(3).padStart(10)} Hz | ${c.toFixed(3)} cent | conf=${r.confidence.toFixed(3)}`,
  )
}

const harmonic = yinDetect(synthesize(440, { harmonics: true, noise: 0.01 }))
const hc = harmonic ? cents(harmonic.frequency, 440) : Infinity
console.log(`\n谐波 A4: ${harmonic ? `${harmonic.frequency.toFixed(3)} Hz / ${hc.toFixed(3)} cent` : 'null'}`)
if (hc >= 2) failed++

const eval442 = scoreFromCent((1200 * Math.log(442 / 440)) / Math.LN2)
console.log(`评分 442vs440: ${eval442} (期望 95)`)
if (eval442 !== 95) failed++

console.log(`MIDI 440: ${midiFromHz(440).toFixed(4)} (期望 69)`)
if (Math.abs(midiFromHz(440) - 69) > 1e-6) failed++

if (failed > 0) {
  console.error(`\n验证失败: ${failed} 项`)
  process.exit(1)
}
console.log('\n全部通过')
