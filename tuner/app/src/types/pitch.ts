/** 与原生插件回调保持一致的音高检测结果 */
export type PitchStatus = 'idle' | 'detecting' | 'valid' | 'too_low' | 'no_signal'

export interface PitchResult {
  frequency: number
  confidence: number
  note: string
  midi: number
  cent: number
  score: number
  status: PitchStatus
}

export interface PitchStartOptions {
  sampleRate?: number
  bufferSize?: number
  /** A4 参考频率，默认 440；小提琴常用 442/443 */
  a4?: number
}

export type PitchResultCallback = (result: PitchResult) => void

export interface PitchProvider {
  start(options: PitchStartOptions, onResult: PitchResultCallback): Promise<void>
  stop(): void
  setTargetNote(note: string | null): void
}

export const IDLE_PITCH_RESULT: PitchResult = {
  frequency: 0,
  confidence: 0,
  note: '--',
  midi: 0,
  cent: 0,
  score: 0,
  status: 'idle',
}
