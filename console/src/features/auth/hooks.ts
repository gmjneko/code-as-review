import { useMutation } from '@tanstack/react-query'

import { authApi } from './api'
import { getSession, useSessionStore } from './session-store'
import { getAccessToken } from './token-refresh'
import type { CurrentUser, LoginRequest, RegisterRequest, TokenResponse } from './types'

export function useCurrentUser(): CurrentUser | null {
  return useSessionStore((state) => state.session?.user ?? null)
}

async function signIn(tokens: TokenResponse): Promise<void> {
  const user = await authApi.me(tokens.accessToken)
  useSessionStore.getState().signIn(tokens, user)
}

export function useLogin() {
  return useMutation({
    mutationFn: async (body: LoginRequest) => signIn(await authApi.login(body)),
  })
}

export function useRegister() {
  return useMutation({
    mutationFn: async (body: RegisterRequest) => signIn(await authApi.register(body)),
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
