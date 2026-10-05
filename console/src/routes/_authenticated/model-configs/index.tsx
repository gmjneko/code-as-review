import { createFileRoute } from '@tanstack/react-router'

import { ModelConfigListPage, modelConfigQueries } from '@/features/model-configs'

export const Route = createFileRoute('/_authenticated/model-configs/')({
  // Blocking loader: the page renders with data and reads it via useSuspenseQuery.
  loader: ({ context }) => context.queryClient.ensureQueryData(modelConfigQueries.list()),
  component: ModelConfigListPage,
})
