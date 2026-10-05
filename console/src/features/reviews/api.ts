import { http, type PageResult } from '@/shared/api'

import type { ReviewListParams, ReviewTask } from './types'

const BASE = '/reviews'

export const reviewApi = {
  list: (params: ReviewListParams, signal?: AbortSignal) =>
    http.get<PageResult<ReviewTask>>(BASE, { query: { ...params }, signal }),

  cancel: (id: number) => http.post<void>(`${BASE}/${id}/cancel`),
}
