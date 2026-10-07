import { createFileRoute, stripSearchParams } from '@tanstack/react-router'

import { IssueListPage, issueQueries } from '@/features/issues'

const DEFAULTS = { page: 1, size: 20, repositoryId: undefined as number | undefined }

export const Route = createFileRoute('/_authenticated/issues/')({
  validateSearch: (search: Record<string, unknown>) => ({
    page: Number(search.page ?? 1),
    size: Number(search.size ?? 20),
    repositoryId: search.repositoryId === undefined ? undefined : Number(search.repositoryId),
  }),
  search: { middlewares: [stripSearchParams(DEFAULTS)] },
  loaderDeps: ({ search }) => search,
  loader: ({ context, deps }) => {
    void context.queryClient.prefetchQuery(issueQueries.list(deps))
  },
  component: IssueListPage,
})
