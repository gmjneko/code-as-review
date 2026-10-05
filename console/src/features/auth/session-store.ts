import { create } from 'zustand'
import { createJSONStorage, persist } from 'zustand/middleware'

import { decodeJwtClaims } from './jwt'
import type { CurrentUser, TokenResponse } from './types'

export const SESSION_STORAGE_KEY = 'cr-console.session'

export interface Session {
  accessToken: string
  refreshToken: string
  /** Epoch milliseconds, measured on the client clock to be immune to clock skew. */
  accessTokenExpiresAt: number
  user: CurrentUser
}

interface SessionState {
  session: Session | null
  signIn: (tokens: TokenResponse) => void
  signOut: () => void
}

function toSession(tokens: TokenResponse): Session {
  const claims = decodeJwtClaims(tokens.accessToken)
  return {
    accessToken: tokens.accessToken,
    refreshToken: tokens.refreshToken,
    accessTokenExpiresAt: Date.now() + tokens.expiresIn * 1000,
    user: { id: claims.sub ?? '', username: claims.username ?? '' },
  }
}

/**
 * The signed-in session, persisted to localStorage so it survives reloads and is shared by
 * all tabs. Hydration from localStorage is synchronous, so route guards can read it at once.
 */
export const useSessionStore = create<SessionState>()(
  persist(
    (set) => ({
      session: null,
      signIn: (tokens) => set({ session: toSession(tokens) }),
      signOut: () => set({ session: null }),
    }),
    {
      name: SESSION_STORAGE_KEY,
      version: 1,
      storage: createJSONStorage(() => localStorage),
      partialize: (state) => ({ session: state.session }),
    },
  ),
)

export function getSession(): Session | null {
  return useSessionStore.getState().session
}

export function isAuthenticated(): boolean {
  return getSession() !== null
}

function onStorage(event: StorageEvent): void {
  if (event.key === SESSION_STORAGE_KEY) {
    void useSessionStore.persist.rehydrate()
  }
}

/** Re-reads the session when another tab signs in, refreshes tokens, or signs out. */
export function syncSessionAcrossTabs(): () => void {
  window.addEventListener('storage', onStorage)
  return () => window.removeEventListener('storage', onStorage)
}
