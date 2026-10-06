import { http, type PageResult } from '@/shared/api'

import type { CreateReviewRequest, ReviewComment, ReviewListParams, ReviewTask } from './types'

const BASE = '/reviews'

export const reviewApi = {
  list: (params: ReviewListParams, signal?: AbortSignal) =>
    http.get<PageResult<ReviewTask>>(BASE, { query: { ...params }, signal }),

  get: (id: number, signal?: AbortSignal) => http.get<ReviewTask>(`${BASE}/${id}`, { signal }),

  comments: (id: number, includeFiltered: boolean, signal?: AbortSignal) =>
    http.get<ReviewComment[]>(`${BASE}/${id}/comments`, { query: { includeFiltered }, signal }),

  create: (body: CreateReviewRequest) => http.post<ReviewTask>(BASE, body),

  cancel: (id: number) => http.post<void>(`${BASE}/${id}/cancel`),
}
