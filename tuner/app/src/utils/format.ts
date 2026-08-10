import type { PitchResult, PitchStatus } from '@/types/pitch'

export function formatFrequency(hz: number): string {
  if (!hz || hz <= 0) return '--'
  return `${hz.toFixed(1)} Hz`
}

export function formatCent(cent: number, status: PitchStatus): string {
  if (status === 'idle' || status === 'no_signal' || status === 'metronome_suppressed' || status === 'voice_rejected') {
    return '--'
  }
  if (status === 'stabilizing') {
    const sign = cent > 0 ? '+' : ''
    return `${sign}${cent.toFixed(0)} cent`
  }
  const sign = cent > 0 ? '+' : ''
  return `${sign}${cent.toFixed(0)} cent`
}

export function formatScore(score: number, status: PitchStatus): string {
  if (status === 'idle' || status === 'no_signal' || status === 'metronome_suppressed' || status === 'voice_rejected') {
    return '--'
  }
  return `${Math.round(score)} 分`
}

export function statusLabel(result: PitchResult): string {
  switch (result.status) {
    case 'idle':
      return '待机'
    case 'detecting':
      return '检测中…'
    case 'no_signal':
      return '未检测到声音'
    case 'too_low':
      return '信号偏弱'
    case 'metronome_suppressed':
      return '节拍干扰过滤'
    case 'voice_rejected':
      return '环境声过滤'
    case 'stabilizing':
      return '稳定中…'
    case 'valid': {
      const abs = Math.abs(result.cent)
      if (abs <= 5) return '非常准确'
      if (abs <= 15) return '较准确'
      if (abs <= 30) return '略有偏差'
      if (abs <= 50) return '偏差较大'
      return '音不准'
    }
    default:
      return '--'
  }
}

export function statusColor(result: PitchResult): string {
  if (result.status === 'stabilizing') return '#8b93a7'
  if (result.status !== 'valid') return '#8b93a7'
  const abs = Math.abs(result.cent)
  if (abs <= 5) return '#3dd68c'
  if (abs <= 15) return '#7ee787'
  if (abs <= 30) return '#e3b341'
  return '#ff7b72'
}
