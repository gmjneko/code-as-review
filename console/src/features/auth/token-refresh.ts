import { ApiError } from '@/shared/api'

import { authApi } from './api'
import { getSession, useSessionStore, type Session } from './session-store'

/** Refresh this long before the access token actually expires. */
const EXPIRY_MARGIN_MS = 30_000
const REFRESH_LOCK_NAME = 'cr-console.token-refresh'

let inflight: Promise<string | null> | null = null

export function isExpiring(session: Session, now = Date.now()): boolean {
  return session.accessTokenExpiresAt - EXPIRY_MARGIN_MS <= now
}

/**
 * Exchanges the refresh token for a new token pair, unless someone else already did.
 *
 * The backend rotates refresh tokens on every use, so two concurrent refreshes would make one of
 * them fail and sign the user out. Concurrent calls in this tab share one promise, and the Web
 * Locks API serializes refreshes across tabs; inside the lock the session is re-read in case
 * another tab rotated it meanwhile.
 *
 * Resolves to the new access token, or `null` once the session is gone. Network failures reject
 * and leave the session intact.
 */
export function refreshAccessToken(staleAccessToken: string): Promise<string | null> {
  inflight ??= withRefreshLock(() => performRefresh(staleAccessToken)).finally(() => {
    inflight = null
  })
  return inflight
}

async function performRefresh(staleAccessToken: string): Promise<string | null> {
  await useSessionStore.persist.rehydrate()
  const session = getSession()
  if (!session) return null
  if (session.accessToken !== staleAccessToken && !isExpiring(session)) {
    return session.accessToken
  }

  try {
    const tokens = await authApi.refresh(session.refreshToken)
    useSessionStore.getState().rotateTokens(tokens)
    return tokens.accessToken
  } catch (error) {
    if (error instanceof ApiError && error.isUnauthorized) {
      useSessionStore.getState().signOut()
      return null
    }
    throw error
  }
}

function withRefreshLock<T>(task: () => Promise<T>): Promise<T> {
  if (typeof navigator !== 'undefined' && 'locks' in navigator) {
    return navigator.locks.request(REFRESH_LOCK_NAME, task)
  }
  return task()
}

/** A valid access token, refreshed first if it is about to expire; `null` when signed out. */
export async function getAccessToken(): Promise<string | null> {
  const session = getSession()
  if (!session) return null
  if (isExpiring(session)) return refreshAccessToken(session.accessToken)
  return session.accessToken
}
