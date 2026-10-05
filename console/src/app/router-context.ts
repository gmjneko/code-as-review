import type { QueryClient } from '@tanstack/react-query'

/** Dependencies available to every route's `beforeLoad` / `loader` via `context`. */
export interface RouterContext {
  queryClient: QueryClient
}
