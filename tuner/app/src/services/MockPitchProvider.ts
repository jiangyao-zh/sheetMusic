import type {
  PitchProvider,
  PitchResult,
  PitchResultCallback,
  PitchStartOptions,
} from '@/types/pitch'

const NOTE_CYCLE = [
  { note: 'G3', hz: 196.0 },
  { note: 'D4', hz: 293.66 },
  { note: 'A4', hz: 440.0 },
  { note: 'E5', hz: 659.25 },
]

function midiFromHz(hz: number, a4: number): number {
  return 69 + 12 * Math.log2(hz / a4)
}

function noteNameFromMidi(midi: number): string {
  const names = ['C', 'C#', 'D', 'D#', 'E', 'F', 'F#', 'G', 'G#', 'A', 'A#', 'B']
  const rounded = Math.round(midi)
  const name = names[((rounded % 12) + 12) % 12]
  const octave = Math.floor(rounded / 12) - 1
  return `${name}${octave}`
}

function scoreFromCent(cent: number): number {
  const abs = Math.abs(cent)
  if (abs <= 5) return 100
  if (abs <= 15) return 95
  if (abs <= 30) return 85
  if (abs <= 50) return 70
  return Math.max(0, 60 - (abs - 50) / 2)
}

/**
 * H5 开发用模拟音高源：在空弦附近缓慢扫频并叠加轻微抖动。
 */
export class MockPitchProvider implements PitchProvider {
  private timer: ReturnType<typeof setInterval> | null = null
  private targetNote: string | null = null
  private a4 = 440
  private tick = 0

  async start(options: PitchStartOptions, onResult: PitchResultCallback): Promise<void> {
    this.stop()
    this.a4 = options.a4 ?? 440
    this.tick = 0

    onResult({
      frequency: 0,
      confidence: 0,
      note: '--',
      midi: 0,
      cent: 0,
      score: 0,
      status: 'detecting',
    })

    this.timer = setInterval(() => {
      this.tick += 1
      const base = NOTE_CYCLE[Math.floor(this.tick / 40) % NOTE_CYCLE.length]
      const wobble = Math.sin(this.tick / 6) * 1.8 + (Math.random() - 0.5) * 0.6
      const frequency = base.hz + wobble
      const midi = midiFromHz(frequency, this.a4)
      const note = noteNameFromMidi(midi)
      const targetHz = this.resolveTargetHz(note, frequency)
      const cent = 1200 * Math.log2(frequency / targetHz)
      const score = scoreFromCent(cent)

      const result: PitchResult = {
        frequency: Number(frequency.toFixed(2)),
        confidence: 0.92 + Math.random() * 0.06,
        note,
        midi: Number(midi.toFixed(2)),
        cent: Number(cent.toFixed(1)),
        score: Number(score.toFixed(0)),
        status: 'valid',
      }
      onResult(result)
    }, 66)
  }

  stop(): void {
    if (this.timer) {
      clearInterval(this.timer)
      this.timer = null
    }
  }

  setTargetNote(note: string | null): void {
    this.targetNote = note
  }

  private resolveTargetHz(detectedNote: string, frequency: number): number {
    if (this.targetNote) {
      const found = NOTE_CYCLE.find((n) => n.note === this.targetNote)
      if (found) return found.hz
    }
    const midi = Math.round(midiFromHz(frequency, this.a4))
    return this.a4 * Math.pow(2, (midi - 69) / 12)
  }
}
