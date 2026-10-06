import { createFileRoute } from '@tanstack/react-router'

import { CredentialListPage, credentialQueries } from '@/features/credentials'

export const Route = createFileRoute('/_authenticated/credentials/')({
  loader: ({ context }) => context.queryClient.ensureQueryData(credentialQueries.list()),
  component: CredentialListPage,
})
