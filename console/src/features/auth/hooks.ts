import { useMutation } from '@tanstack/react-query'

import { authApi } from './api'
import { getSession, useSessionStore } from './session-store'
import { getAccessToken } from './token-refresh'
import type { CurrentUser } from './types'

export function useCurrentUser(): CurrentUser | null {
  return useSessionStore((state) => state.session?.user ?? null)
}

export function useLogin() {
  return useMutation({
    mutationFn: authApi.login,
    onSuccess: (tokens) => useSessionStore.getState().signIn(tokens),
  })
}

export function useRegister() {
  return useMutation({
    mutationFn: authApi.register,
    onSuccess: (tokens) => useSessionStore.getState().signIn(tokens),
  })
}

export function useLogout() {
  return useMutation({
    mutationFn: async () => {
      // Ensure a fresh access token first: refreshing rotates the refresh token, so the one sent
      // to /logout must be read afterwards or the newly issued token would stay valid.
      await getAccessToken()
      const session = getSession()
      if (session) await authApi.logout(session.refreshToken)
    },
    // Signing out locally must succeed even when the server call fails.
    meta: { silent: true },
    onSettled: () => useSessionStore.getState().signOut(),
  })
}
