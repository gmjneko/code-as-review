import { QueryClient } from '@tanstack/react-query'
import { render, type RenderOptions } from '@testing-library/react'
import type { ReactElement } from 'react'

import { AppProviders } from '@/app/AppProviders'

export function createTestQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
}

interface RenderWithProvidersOptions extends Omit<RenderOptions, 'wrapper'> {
  /** Pass a client to seed the cache before rendering; a fresh one is created otherwise. */
  queryClient?: QueryClient
}

/** Renders `ui` inside the real app providers with a retry-free QueryClient. */
export function renderWithProviders(
  ui: ReactElement,
  { queryClient = createTestQueryClient(), ...options }: RenderWithProvidersOptions = {},
) {
  const result = render(<AppProviders queryClient={queryClient}>{ui}</AppProviders>, options)
  return { queryClient, ...result }
}
