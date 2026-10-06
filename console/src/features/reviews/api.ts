import { http, type PageResult } from '@/shared/api'

import type { CreateReviewRequest, ReviewListParams, ReviewTask } from './types'

const BASE = '/reviews'

export const reviewApi = {
  list: (params: ReviewListParams, signal?: AbortSignal) =>
    http.get<PageResult<ReviewTask>>(BASE, { query: { ...params }, signal }),

  create: (body: CreateReviewRequest) => http.post<ReviewTask>(BASE, body),

  cancel: (id: number) => http.post<void>(`${BASE}/${id}/cancel`),
}
