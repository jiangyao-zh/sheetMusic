import type {
  PitchProvider,
  PitchResult,
  PitchResultCallback,
  PitchStartOptions,
} from '@/types/pitch'

interface NativePitchPlugin {
  start: (
    options: PitchStartOptions,
    callback: (result: PitchResult) => void,
  ) => void
  stop: () => void
  setTargetNote: (note: string | null) => void
}

function requestMicPermission(): Promise<boolean> {
  return new Promise((resolve) => {
    // #ifdef APP-PLUS
    const plusAny = (globalThis as unknown as { plus?: any }).plus
    if (!plusAny?.android) {
      resolve(true)
      return
    }
    plusAny.android.requestPermissions(
      ['android.permission.RECORD_AUDIO'],
      (e: { granted?: string[]; deniedPresent?: string[]; deniedAlways?: string[] }) => {
        const granted = (e.granted || []).includes('android.permission.RECORD_AUDIO')
        resolve(granted)
      },
      () => resolve(false),
    )
    // #endif
    // #ifndef APP-PLUS
    resolve(false)
    // #endif
  })
}

/**
 * App-Plus 原生插件封装。算法全部在 Kotlin 侧完成。
 */
export class NativePitchProvider implements PitchProvider {
  private plugin: NativePitchPlugin | null = null
  private targetNote: string | null = null

  private getPlugin(): NativePitchPlugin {
    if (this.plugin) return this.plugin
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const raw = (uni as any).requireNativePlugin('PitchDetector') as NativePitchPlugin | null
    if (!raw) {
      throw new Error('PitchDetector 原生插件未加载，请使用离线打包/自定义基座')
    }
    this.plugin = raw
    return raw
  }

  async start(options: PitchStartOptions, onResult: PitchResultCallback): Promise<void> {
    const ok = await requestMicPermission()
    if (!ok) {
      throw new Error('麦克风权限未授予')
    }
    const plugin = this.getPlugin()
    if (this.targetNote) {
      plugin.setTargetNote(this.targetNote)
    }
    plugin.start(
      {
        sampleRate: options.sampleRate ?? 44100,
        bufferSize: options.bufferSize ?? 4096,
        a4: options.a4 ?? 440,
      },
      onResult,
    )
  }

  stop(): void {
    try {
      this.getPlugin().stop()
    } catch {
      // ignore if plugin unavailable
    }
  }

  setTargetNote(note: string | null): void {
    this.targetNote = note
    try {
      this.getPlugin().setTargetNote(note)
    } catch {
      // H5 / 未加载插件时忽略
    }
  }
}
