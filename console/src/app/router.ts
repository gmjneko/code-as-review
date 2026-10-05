import type { QueryClient } from '@tanstack/react-query'
import { createRouter } from '@tanstack/react-router'

import { routeTree } from '@/routeTree.gen'
import { NotFound, PageLoading, RouteError } from '@/shared/components'

export function createAppRouter(queryClient: QueryClient) {
  return createRouter({
    routeTree,
    context: { queryClient },
    defaultPreload: 'intent',
    // TanStack Query owns data freshness; the router should always ask it.
    defaultPreloadStaleTime: 0,
    scrollRestoration: true,
    defaultPendingComponent: PageLoading,
    defaultErrorComponent: RouteError,
    defaultNotFoundComponent: NotFound,
  })
}

export type AppRouter = ReturnType<typeof createAppRouter>

declare module '@tanstack/react-router' {
  interface Register {
    router: AppRouter
  }
}
