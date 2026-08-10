import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { getPitchProvider } from '@/services/PitchProvider'
import { NativePitchProvider } from '@/services/NativePitchProvider'
import { pitchSocketService, type PitchSocketStatus } from '@/services/PitchSocketService'
import {
  IDLE_PITCH_RESULT,
  type PitchResult,
  type PitchStartOptions,
} from '@/types/pitch'
import { statusColor, statusLabel } from '@/utils/format'

const STORAGE_HOST = 'pitch_tv_host'
const STORAGE_SESSION = 'pitch_tv_session'
const STORAGE_PORT = 'pitch_tv_port'

export const usePitchStore = defineStore('pitch', () => {
  const result = ref<PitchResult>({ ...IDLE_PITCH_RESULT })
  const running = ref(false)
  const error = ref('')
  const a4 = ref(440)

  const tvHost = ref(uni.getStorageSync(STORAGE_HOST) || '')
  const tvSession = ref(uni.getStorageSync(STORAGE_SESSION) || 'default')
  const tvPort = ref(Number(uni.getStorageSync(STORAGE_PORT)) || 9091)
  const castEnabled = ref(false)
  const socketStatus = ref<PitchSocketStatus>('idle')
  /** 本轮投屏是否曾成功连上过（用于重连时保持单行稳定文案） */
  const wasSocketConnected = ref(false)

  const label = computed(() => statusLabel(result.value))
  const color = computed(() => statusColor(result.value))
  /** 单行投屏状态，不展示 URL / 技术细节，避免连接成功后跳动 */
  const socketLabel = computed(() => {
    if (socketStatus.value === 'connected') return '已连接 TV'
    if (
      castEnabled.value &&
      wasSocketConnected.value &&
      (socketStatus.value === 'connecting' || socketStatus.value === 'error')
    ) {
      return '重连中…'
    }
    switch (socketStatus.value) {
      case 'connecting':
        return '连接中…'
      case 'error':
        return '连接失败'
      default:
        return '未连接'
    }
  })

  const socketStatusClass = computed(() => {
    if (socketStatus.value === 'connected') return 'connected'
    if (
      castEnabled.value &&
      wasSocketConnected.value &&
      (socketStatus.value === 'connecting' || socketStatus.value === 'error')
    ) {
      return 'connecting'
    }
    if (socketStatus.value === 'connecting') return 'connecting'
    if (socketStatus.value === 'error') return 'error'
    return 'idle'
  })

  pitchSocketService.onStatus((status) => {
    socketStatus.value = status
    if (status === 'connected') wasSocketConnected.value = true
    if (status === 'idle') wasSocketConnected.value = false
  })

  pitchSocketService.onBeat((beat) => {
    const provider = getPitchProvider()
    if (provider instanceof NativePitchProvider) {
      provider.notifyMetronomeBeat(beat)
    }
  })

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
        if (castEnabled.value) {
          pitchSocketService.publish(data)
        }
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
    pitchSocketService.setA4(value)
  }

  function saveCastConfig() {
    uni.setStorageSync(STORAGE_HOST, tvHost.value.trim())
    uni.setStorageSync(STORAGE_SESSION, tvSession.value.trim() || 'default')
    uni.setStorageSync(STORAGE_PORT, String(tvPort.value || 9091))
  }

  function connectTv() {
    saveCastConfig()
    if (!tvHost.value.trim()) {
      error.value = '请填写 TV / 中继服务器 IP'
      return
    }
    castEnabled.value = true
    pitchSocketService.setA4(a4.value)
    pitchSocketService.connect({
      host: tvHost.value.trim(),
      port: tvPort.value || 9091,
      session: tvSession.value.trim() || 'default',
      a4: a4.value,
    })
  }

  function disconnectTv() {
    castEnabled.value = false
    wasSocketConnected.value = false
    pitchSocketService.disconnect()
  }

  return {
    result,
    running,
    error,
    a4,
    label,
    color,
    tvHost,
    tvSession,
    tvPort,
    castEnabled,
    socketStatus,
    socketLabel,
    socketStatusClass,
    start,
    stop,
    setTargetNote,
    setA4,
    connectTv,
    disconnectTv,
    saveCastConfig,
  }
})
