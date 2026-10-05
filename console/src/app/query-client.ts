import { MutationCache, QueryCache, QueryClient } from '@tanstack/react-query'

import { ApiError, getErrorMessage } from '@/shared/api'
import { feedback } from '@/shared/feedback'

declare module '@tanstack/react-query' {
  interface Register {
    mutationMeta: {
      /** Skip the global error toast because the caller reports the error itself. */
      silent?: boolean
      /** Toast shown once the mutation succeeds. */
      successMessage?: string
    }
  }
}

/**
 * An expired session already sends the user to the login page; a toast on top is noise. Other
 * 401s (e.g. a wrong password on login) are ordinary errors and must be shown.
 */
function isReportable(error: unknown): boolean {
  return !(error instanceof ApiError && error.isSessionExpired)
}

export function createQueryClient(): QueryClient {
  return new QueryClient({
    queryCache: new QueryCache({
      onError: (error, query) => {
        // A failed first load is rendered by the page (error boundary or inline state). Only
        // failed background refetches need a toast, because stale data is still on screen.
        if (query.state.data !== undefined && isReportable(error)) {
          feedback.message.error(`数据刷新失败：${getErrorMessage(error)}`)
        }
      },
    }),
    mutationCache: new MutationCache({
      onSuccess: (_data, _variables, _context, mutation) => {
        const message = mutation.meta?.successMessage
        if (message) feedback.message.success(message)
      },
      onError: (error, _variables, _context, mutation) => {
        if (!mutation.meta?.silent && isReportable(error)) {
          feedback.message.error(getErrorMessage(error))
        }
      },
    }),
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        // 4xx responses will not change on retry; transient failures get two more attempts.
        retry: (failureCount, error) =>
          !(error instanceof ApiError && error.isClientError) && failureCount < 2,
      },
      mutations: {
        retry: false,
      },
    },
  })
}
