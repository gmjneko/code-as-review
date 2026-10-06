import { keepPreviousData, queryOptions, useMutation, useQueryClient } from '@tanstack/react-query'

import { reviewApi } from './api'
import { ACTIVE_TASK_POLL_INTERVAL_MS, isTerminalStatus } from './constants'
import type { CreateReviewRequest, ReviewListParams } from './types'

export const reviewKeys = {
  all: ['reviews'] as const,
  lists: () => [...reviewKeys.all, 'list'] as const,
  list: (params: ReviewListParams) => [...reviewKeys.lists(), params] as const,
  detail: (id: number) => [...reviewKeys.all, 'detail', id] as const,
  comments: (id: number, includeFiltered: boolean) =>
    [...reviewKeys.detail(id), 'comments', { includeFiltered }] as const,
}

export const reviewQueries = {
  list: (params: ReviewListParams) =>
    queryOptions({
      queryKey: reviewKeys.list(params),
      queryFn: ({ signal }) => reviewApi.list(params, signal),
      // Keep the current page on screen while the next one loads.
      placeholderData: keepPreviousData,
      refetchInterval: (query) =>
        query.state.data?.records.some((task) => !isTerminalStatus(task.status))
          ? ACTIVE_TASK_POLL_INTERVAL_MS
          : false,
    }),
  detail: (id: number) =>
    queryOptions({
      queryKey: reviewKeys.detail(id),
      queryFn: ({ signal }) => reviewApi.get(id, signal),
      refetchInterval: (query) =>
        query.state.data && !isTerminalStatus(query.state.data.status)
          ? ACTIVE_TASK_POLL_INTERVAL_MS
          : false,
    }),
  /**
   * Comments are written in one go when the review finishes, so callers should only enable this
   * for a task in a terminal status; the first fetch then already returns the complete set.
   */
  comments: (id: number, includeFiltered: boolean) =>
    queryOptions({
      queryKey: reviewKeys.comments(id, includeFiltered),
      queryFn: ({ signal }) => reviewApi.comments(id, includeFiltered, signal),
    }),
}

export function useCreateReview() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateReviewRequest) => reviewApi.create(body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: reviewKeys.all }),
    meta: { successMessage: '评审任务已创建' },
  })
}

export function useCancelReview() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => reviewApi.cancel(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: reviewKeys.all }),
    meta: { successMessage: '已提交取消请求' },
  })
}
