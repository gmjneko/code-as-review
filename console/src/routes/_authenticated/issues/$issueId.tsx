import { createFileRoute, notFound } from '@tanstack/react-router'

import { IssueDetailPage, issueQueries } from '@/features/issues'

export const Route = createFileRoute('/_authenticated/issues/$issueId')({
  params: {
    parse: ({ issueId }) => {
      const id = Number(issueId)
      if (!Number.isSafeInteger(id) || id <= 0) throw notFound()
      return { issueId: id }
    },
    stringify: ({ issueId }) => ({ issueId: String(issueId) }),
  },
  loader: ({ context, params }) =>
    context.queryClient.ensureQueryData(issueQueries.detail(params.issueId)),
  component: IssueDetailPage,
})
