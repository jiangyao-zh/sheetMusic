/**
 * 音准 UI 颜色（与 tuner/app/src/utils/format.ts、PitchUiColors.kt 保持一致）
 */

export const PITCH_COLOR_GRAY = '#8b93a7'
export const PITCH_COLOR_GREEN = '#3dd68c'
export const PITCH_COLOR_LIGHT_GREEN = '#7ee787'
export const PITCH_COLOR_YELLOW = '#e3b341'
export const PITCH_COLOR_RED = '#ff7b72'

/**
 * @param {{ status?: string, cent?: number } | null | undefined} result
 * @returns {string}
 */
export function pitchAccentColor(result) {
  if (!result) return PITCH_COLOR_GRAY
  if (result.status === 'stabilizing') return PITCH_COLOR_GRAY
  if (result.status !== 'valid') return PITCH_COLOR_GRAY
  const abs = Math.abs(Number(result.cent) || 0)
  if (abs <= 5) return PITCH_COLOR_GREEN
  if (abs <= 15) return PITCH_COLOR_LIGHT_GREEN
  if (abs <= 30) return PITCH_COLOR_YELLOW
  return PITCH_COLOR_RED
}
