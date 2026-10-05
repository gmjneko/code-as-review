import { QueryClient } from '@tanstack/react-query'
import { render, type RenderOptions } from '@testing-library/react'
import type { ReactElement } from 'react'

import { AppProviders } from '@/app/AppProviders'

/** Renders `ui` inside the real app providers with a fresh, retry-free QueryClient. */
export function renderWithProviders(ui: ReactElement, options?: Omit<RenderOptions, 'wrapper'>) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  const result = render(<AppProviders queryClient={queryClient}>{ui}</AppProviders>, options)
  return { queryClient, ...result }
}
