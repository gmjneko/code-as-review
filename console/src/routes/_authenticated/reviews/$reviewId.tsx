import { createFileRoute, notFound, stripSearchParams } from '@tanstack/react-router'

import {
  REVIEW_DETAIL_DEFAULTS,
  ReviewDetailPage,
  reviewDetailSearchSchema,
  reviewQueries,
} from '@/features/reviews'
import { ApiError } from '@/shared/api'

export const Route = createFileRoute('/_authenticated/reviews/$reviewId')({
  params: {
    parse: ({ reviewId }) => {
      const id = Number(reviewId)
      // A malformed id can never exist, so treat it like a missing task.
      if (!Number.isSafeInteger(id) || id <= 0) throw notFound()
      return { reviewId: id }
    },
    stringify: ({ reviewId }) => ({ reviewId: String(reviewId) }),
  },
  validateSearch: reviewDetailSearchSchema,
  search: { middlewares: [stripSearchParams(REVIEW_DETAIL_DEFAULTS)] },
  // Blocking loader for the task itself; comments load on the page once the task has finished.
  loader: async ({ context, params }) => {
    try {
      return await context.queryClient.ensureQueryData(reviewQueries.detail(params.reviewId))
    } catch (error) {
      // The backend answers 404 for tasks that do not exist or belong to someone else.
      if (error instanceof ApiError && error.status === 404) throw notFound()
      throw error
    }
  },
  component: ReviewDetailPage,
})
