import { createFileRoute, notFound, stripSearchParams } from '@tanstack/react-router'

import {
  REVIEW_DETAIL_DEFAULTS,
  ReviewDetailPage,
  reviewDetailSearchSchema,
  reviewQueries,
} from '@/features/reviews'

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
  loader: ({ context, params }) =>
    context.queryClient.ensureQueryData(reviewQueries.detail(params.reviewId)),
  component: ReviewDetailPage,
})
