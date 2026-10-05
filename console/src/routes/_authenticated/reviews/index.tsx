import { createFileRoute, stripSearchParams } from '@tanstack/react-router'

import {
  REVIEW_LIST_DEFAULTS,
  ReviewListPage,
  reviewListSearchSchema,
  reviewQueries,
} from '@/features/reviews'

export const Route = createFileRoute('/_authenticated/reviews/')({
  validateSearch: reviewListSearchSchema,
  search: { middlewares: [stripSearchParams(REVIEW_LIST_DEFAULTS)] },
  loaderDeps: ({ search }) => search,
  // Non-blocking loader: start fetching (also on hover-preload) without holding up navigation;
  // the table shows its own loading state and keeps the previous page while paging.
  loader: ({ context, deps }) => {
    void context.queryClient.prefetchQuery(reviewQueries.list(deps))
  },
  component: ReviewListPage,
})
