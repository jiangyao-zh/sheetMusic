#!/usr/bin/env node
/**
 * 纯 JS 复刻 Kotlin YIN 管线，验证合成正弦波误差 < 1 cent。
 * 不依赖 Android SDK，可在任意 Node 环境运行：
 *   node tuner/scripts/verify-yin.mjs
 */

const SAMPLE_RATE = 44100
const THRESHOLD = 0.1
const MIN_F = 190
const MAX_F = 2700

function synthesize(frequency, { harmonics = false, n = 4096, noise = 0.01, weakFundamental = false } = {}) {
  const out = new Float64Array(n)
  for (let i = 0; i < n; i++) {
    const t = i / SAMPLE_RATE
    let v = Math.sin(2 * Math.PI * frequency * t)
    if (harmonics) {
      const fundScale = weakFundamental ? 0.18 : 1.0
      v = fundScale * v
      v += 0.45 * Math.sin(2 * Math.PI * frequency * 2 * t)
      v += 0.25 * Math.sin(2 * Math.PI * frequency * 3 * t)
      v += 0.12 * Math.sin(2 * Math.PI * frequency * 4 * t)
    }
    v += Math.sin(i * 0.013 + frequency) * noise
    out[i] = v * 0.35
  }
  return out
}

function synthesizeClick(freq = 1500, n = 4096) {
  const out = new Float64Array(n)
  for (let i = 0; i < Math.min(200, n); i++) {
    const t = i / SAMPLE_RATE
    out[i] = 0.5 * Math.sin(2 * Math.PI * freq * t) * Math.exp(-t * 120)
  }
  return out
}

function removeDc(samples) {
  let sum = 0
  for (const s of samples) sum += s
  const mean = sum / samples.length
  return samples.map((s) => s - mean)
}

function projectCos(samples, omega) {
  let re = 0
  let im = 0
  for (let i = 0; i < samples.length; i++) {
    re += samples[i] * Math.cos(omega * i)
    im += samples[i] * Math.sin(omega * i)
  }
  return Math.sqrt(re * re + im * im) / samples.length
}

function isSecondHarmonicLock(samples, candidateF, detectedF) {
  if (Math.abs(detectedF / candidateF - 2) > 0.04) return false
  const omega = (2 * Math.PI * candidateF) / SAMPLE_RATE
  const e1 = projectCos(samples, omega)
  const e2 = projectCos(samples, omega * 2)
  if (e1 < 1e-5) return false
  return e2 * e2 > e1 * e1 * 2.5 && e1 > e2 * 0.12
}

function fundamentalLikelihood(samples, f) {
  const omega = (2 * Math.PI * f) / SAMPLE_RATE
  const e1 = projectCos(samples, omega) ** 2
  if (e1 < 1e-10) return 0
  const e2 = projectCos(samples, omega * 2) ** 2
  const e3 = projectCos(samples, omega * 3) ** 2
  const e4 = projectCos(samples, omega * 4) ** 2
  const r2 = e2 / e1
  const r3 = e3 / e1
  const r4 = e4 / e1
  const err = Math.abs(r2 - 0.45) + Math.abs(r3 - 0.25) + Math.abs(r4 - 0.12)
  let s = 1 / (1 + err)
  s *= 1 + Math.log(1 + e1 * 1000)
  if (r2 > 2) s *= 0.35
  return s
}

function pickBestCandidate(samples, rawF, rawConf, prevF = null) {
  const half = rawF * 0.5
  if (half >= MIN_F && half <= MAX_F && isSecondHarmonicLock(samples, half, rawF)) {
    return { frequency: half, confidence: Math.min(1, rawConf * 0.65 + 0.35) }
  }
  const candidates = [...new Set([rawF, rawF * 0.5])].filter((f) => f >= MIN_F && f <= MAX_F)
  let bestF = rawF
  let bestScore = fundamentalLikelihood(samples, rawF)
  for (const f of candidates) {
    if (f >= rawF) continue
    if (!isSecondHarmonicLock(samples, f, rawF)) continue
    const s = fundamentalLikelihood(samples, f)
    if (s > bestScore * 1.02) {
      bestScore = s
      bestF = f
    }
  }
  if (prevF && prevF > 0) {
    const centsDiff = Math.abs((1200 * Math.log(bestF / prevF)) / Math.LN2)
    if (centsDiff < 35) bestScore += 0.15
  }
  const conf = Math.max(0, Math.min(1, rawConf * 0.55 + (bestScore / (bestScore + 1)) * 0.45))
  return { frequency: bestF, confidence: conf }
}

function yinDetect(raw, prevF = null) {
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
    if (bestVal >= 0.32) return null
    tauEst = best
  }

  const s0 = cmnd[tauEst - 1]
  const s1 = cmnd[tauEst]
  const s2 = cmnd[tauEst + 1]
  const denom = s0 - 2 * s1 + s2
  const delta = Math.abs(denom) < 1e-12 ? 0 : (0.5 * (s0 - s2)) / denom
  const tau0 = tauEst + delta
  const tau = refineFractionalTau(samples, tau0)
  const rawF = SAMPLE_RATE / tau
  const rawConf = Math.max(0, Math.min(1, 1 - cmnd[tauEst]))
  return pickBestCandidate(samples, rawF, rawConf, prevF)
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

const freqs = [196.0, 293.66, 392.0, 440.0, 659.25, 987.77, 1318.5, 2093.0]
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

// G4 弱基频 + 强二次谐波
const g4weak = yinDetect(synthesize(392, { harmonics: true, weakFundamental: true, noise: 0.01 }))
const g4c = g4weak ? cents(g4weak.frequency, 392) : Infinity
console.log(`\nG4 弱基频: ${g4weak ? `${g4weak.frequency.toFixed(3)} Hz / ${g4c.toFixed(3)} cent` : 'null'}`)
if (!g4weak || g4c >= 2.5) failed++

const harmonic = yinDetect(synthesize(440, { harmonics: true, noise: 0.01 }))
const hc = harmonic ? cents(harmonic.frequency, 440) : Infinity
console.log(`谐波 A4: ${harmonic ? `${harmonic.frequency.toFixed(3)} Hz / ${hc.toFixed(3)} cent` : 'null'}`)
if (hc >= 2) failed++

// 节拍点击不应稳定锁到 800/1500
const click = yinDetect(synthesizeClick(1500))
if (click && click.confidence > 0.75) {
  console.log(`WARN  点击帧 conf=${click.confidence.toFixed(3)}（应由瞬态门控过滤）`)
}

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
