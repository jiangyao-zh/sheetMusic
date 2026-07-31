import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { getPitchProvider } from '@/services/PitchProvider'
import {
  IDLE_PITCH_RESULT,
  type PitchResult,
  type PitchStartOptions,
} from '@/types/pitch'
import { statusColor, statusLabel } from '@/utils/format'

export const usePitchStore = defineStore('pitch', () => {
  const result = ref<PitchResult>({ ...IDLE_PITCH_RESULT })
  const running = ref(false)
  const error = ref('')
  const a4 = ref(440)

  const label = computed(() => statusLabel(result.value))
  const color = computed(() => statusColor(result.value))

  async function start(options: PitchStartOptions = {}) {
    if (running.value) return
    error.value = ''
    const provider = getPitchProvider()
    const opts: PitchStartOptions = {
      sampleRate: 44100,
      bufferSize: 4096,
      a4: options.a4 ?? a4.value,
    }
    running.value = true
    result.value = {
      ...IDLE_PITCH_RESULT,
      status: 'detecting',
    }
    try {
      await provider.start(opts, (data) => {
        result.value = data
      })
    } catch (e) {
      running.value = false
      result.value = { ...IDLE_PITCH_RESULT }
      error.value = e instanceof Error ? e.message : String(e)
    }
  }

  function stop() {
    const provider = getPitchProvider()
    provider.stop()
    running.value = false
    result.value = { ...IDLE_PITCH_RESULT }
  }

  function setTargetNote(note: string | null) {
    getPitchProvider().setTargetNote(note)
  }

  function setA4(value: number) {
    a4.value = value
  }

  return {
    result,
    running,
    error,
    a4,
    label,
    color,
    start,
    stop,
    setTargetNote,
    setA4,
  }
})
