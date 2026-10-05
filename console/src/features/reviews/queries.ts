import { keepPreviousData, queryOptions, useMutation, useQueryClient } from '@tanstack/react-query'

import { reviewApi } from './api'
import { ACTIVE_TASK_POLL_INTERVAL_MS, isTerminalStatus } from './constants'
import type { ReviewListParams } from './types'

export const reviewKeys = {
  all: ['reviews'] as const,
  lists: () => [...reviewKeys.all, 'list'] as const,
  list: (params: ReviewListParams) => [...reviewKeys.lists(), params] as const,
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
}

export function useCancelReview() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: reviewApi.cancel,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: reviewKeys.all }),
    meta: { successMessage: '已提交取消请求' },
  })
}
