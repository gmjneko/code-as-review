import { createFileRoute } from '@tanstack/react-router'

import { RepositoryListPage, repositoryQueries } from '@/features/repositories'

export const Route = createFileRoute('/_authenticated/repositories/')({
  // Blocking loader: the page renders with data and reads it via useSuspenseQuery.
  loader: ({ context }) => context.queryClient.ensureQueryData(repositoryQueries.list()),
  component: RepositoryListPage,
})
