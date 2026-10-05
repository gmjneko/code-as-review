import type { QueryClient } from '@tanstack/react-query'
import { RouterProvider } from '@tanstack/react-router'

import { AppProviders } from './AppProviders'
import type { AppRouter } from './router'

interface AppProps {
  queryClient: QueryClient
  router: AppRouter
}

export function App({ queryClient, router }: AppProps) {
  return (
    <AppProviders queryClient={queryClient}>
      <RouterProvider router={router} />
    </AppProviders>
  )
}
