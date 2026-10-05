import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '@/shared/api'

import { authApi } from './api'
import { SESSION_STORAGE_KEY, useSessionStore, type Session } from './session-store'
import { getAccessToken, refreshAccessToken } from './token-refresh'

vi.mock('./api', () => ({ authApi: { refresh: vi.fn<(typeof authApi)['refresh']>() } }))

const refresh = vi.mocked(authApi.refresh)

function session(overrides: Partial<Session> = {}): Session {
  return {
    accessToken: 'access-1',
    refreshToken: 'refresh-1',
    accessTokenExpiresAt: Date.now() + 10 * 60_000,
    user: { id: '1', username: 'alice' },
    ...overrides,
  }
}

const rotated = { accessToken: 'access-2', refreshToken: 'refresh-2', expiresIn: 1800 }

beforeEach(() => {
  refresh.mockReset()
  useSessionStore.setState({ session: session() })
})

describe('refreshAccessToken', () => {
  it('shares one refresh between concurrent callers', async () => {
    refresh.mockResolvedValue(rotated)

    const results = await Promise.all([
      refreshAccessToken('access-1'),
      refreshAccessToken('access-1'),
      refreshAccessToken('access-1'),
    ])

    expect(results).toEqual(['access-2', 'access-2', 'access-2'])
    expect(refresh).toHaveBeenCalledExactlyOnceWith('refresh-1')
    expect(useSessionStore.getState().session?.refreshToken).toBe('refresh-2')
  })

  it('reuses tokens that another tab rotated instead of spending the old refresh token', async () => {
    const otherTab = session({
      accessToken: 'access-from-tab-b',
      refreshToken: 'refresh-from-tab-b',
    })
    localStorage.setItem(
      SESSION_STORAGE_KEY,
      JSON.stringify({ state: { session: otherTab }, version: 1 }),
    )

    await expect(refreshAccessToken('access-1')).resolves.toBe('access-from-tab-b')
    expect(refresh).not.toHaveBeenCalled()
  })

  it('signs out when the refresh token is rejected', async () => {
    refresh.mockRejectedValue(new ApiError(401, 'UNAUTHORIZED', 'refresh token invalid or expired'))

    await expect(refreshAccessToken('access-1')).resolves.toBeNull()
    expect(useSessionStore.getState().session).toBeNull()
  })

  it('keeps the session when the refresh fails for another reason', async () => {
    refresh.mockRejectedValue(new ApiError(0, 'NETWORK_ERROR', 'offline'))

    await expect(refreshAccessToken('access-1')).rejects.toMatchObject({ code: 'NETWORK_ERROR' })
    expect(useSessionStore.getState().session?.refreshToken).toBe('refresh-1')
  })
})

describe('getAccessToken', () => {
  it('returns the current token while it is fresh', async () => {
    await expect(getAccessToken()).resolves.toBe('access-1')
    expect(refresh).not.toHaveBeenCalled()
  })

  it('refreshes proactively shortly before expiry', async () => {
    useSessionStore.setState({ session: session({ accessTokenExpiresAt: Date.now() + 5_000 }) })
    refresh.mockResolvedValue(rotated)

    await expect(getAccessToken()).resolves.toBe('access-2')
  })

  it('returns null when signed out', async () => {
    useSessionStore.setState({ session: null })

    await expect(getAccessToken()).resolves.toBeNull()
  })
})
