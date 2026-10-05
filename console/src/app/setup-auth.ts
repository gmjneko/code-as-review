import type { QueryClient } from '@tanstack/react-query'

import { httpAuthAdapter, syncSessionAcrossTabs, useSessionStore } from '@/features/auth'
import { configureHttpAuth } from '@/shared/api'

import type { AppRouter } from './router'

/**
 * Connects the session to the rest of the app. When the signed-in user goes away (logout,
 * expired refresh token, or a sign-out/user switch in another tab), cached data of that user is
 * dropped and route guards re-run, which sends the browser to the login page.
 */
export function setupAuth(queryClient: QueryClient, router: AppRouter): () => void {
  configureHttpAuth(httpAuthAdapter)
  const stopTabSync = syncSessionAcrossTabs()

  const unsubscribe = useSessionStore.subscribe((state, previous) => {
    const previousUserId = previous.session?.user.id
    if (previousUserId !== undefined && previousUserId !== state.session?.user.id) {
      queryClient.clear()
      void router.invalidate()
    }
  })

  return () => {
    unsubscribe()
    stopTabSync()
    configureHttpAuth(null)
  }
}
