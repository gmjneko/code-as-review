import type { AuthAdapter } from '@/shared/api'

import { useSessionStore } from './session-store'
import { getAccessToken, refreshAccessToken } from './token-refresh'

export const httpAuthAdapter: AuthAdapter = {
  getAccessToken,
  refreshAccessToken,
  onUnauthenticated: () => useSessionStore.getState().signOut(),
}
