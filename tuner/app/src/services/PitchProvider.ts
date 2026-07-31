import type { PitchProvider } from '@/types/pitch'
import { MockPitchProvider } from './MockPitchProvider'
import { NativePitchProvider } from './NativePitchProvider'

let cached: PitchProvider | null = null

function isAppPlusRuntime(): boolean {
  try {
    return typeof (globalThis as { plus?: unknown }).plus !== 'undefined'
  } catch {
    return false
  }
}

/**
 * App-Plus 使用原生插件；其它环境（H5）使用 Mock。
 * 页面只依赖 PitchProvider 接口，算法不进入页面层。
 */
export function getPitchProvider(): PitchProvider {
  if (cached) return cached

  // #ifdef APP-PLUS
  cached = new NativePitchProvider()
  // #endif

  // #ifndef APP-PLUS
  if (!cached) {
    cached = isAppPlusRuntime() ? new NativePitchProvider() : new MockPitchProvider()
  }
  // #endif

  if (!cached) {
    cached = new MockPitchProvider()
  }
  return cached
}

export type { PitchProvider }
