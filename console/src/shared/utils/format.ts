import dayjs from 'dayjs'

import type { InstantString } from '@/shared/api'

const integerFormat = new Intl.NumberFormat('zh-CN')

export function formatInteger(value: number | null | undefined): string {
  return value == null ? '-' : integerFormat.format(value)
}

/** Elapsed time between two backend timestamps, e.g. `1分23秒`; `-` until both are known. */
export function formatDuration(
  start: InstantString | null | undefined,
  end: InstantString | null | undefined,
): string {
  if (!start || !end) return '-'
  const seconds = Math.max(0, dayjs(end).diff(dayjs(start), 'second'))
  const minutes = Math.floor(seconds / 60)
  return minutes > 0 ? `${minutes}分${seconds % 60}秒` : `${seconds}秒`
}
