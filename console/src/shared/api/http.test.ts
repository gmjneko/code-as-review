import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from 'vitest'

import { apiResponse, ok } from '@/test/http'

import { configureHttpAuth, http, type AuthAdapter } from './http'

let fetchMock: Mock<typeof fetch>

function authHeaderOfCall(index: number): string | null {
  const init = fetchMock.mock.calls[index]?.[1]
  return new Headers(init?.headers).get('Authorization')
}

function fakeAdapter(overrides: Partial<AuthAdapter> = {}) {
  return {
    getAccessToken: vi.fn<AuthAdapter['getAccessToken']>(() => Promise.resolve('token-1')),
    refreshAccessToken: vi.fn<AuthAdapter['refreshAccessToken']>(() => Promise.resolve('token-2')),
    onUnauthenticated: vi.fn<AuthAdapter['onUnauthenticated']>(),
    ...overrides,
  }
}

beforeEach(() => {
  fetchMock = vi.fn<typeof fetch>()
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  configureHttpAuth(null)
})

describe('http', () => {
  it('unwraps the envelope and omits empty query params', async () => {
    fetchMock.mockResolvedValue(ok({ id: 1 }))

    await expect(
      http.get('/things', { query: { page: 2, keyword: undefined, archived: false } }),
    ).resolves.toEqual({ id: 1 })
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/things?page=2&archived=false',
      expect.objectContaining({ method: 'GET' }),
    )
  })

  it('rejects with the backend code and message', async () => {
    fetchMock.mockResolvedValue(apiResponse(409, { code: 'CONFLICT', message: 'username taken' }))

    await expect(http.post('/auth/register', {})).rejects.toMatchObject({
      name: 'ApiError',
      status: 409,
      code: 'CONFLICT',
      message: 'username taken',
    })
  })

  it('rejects when a 200 response carries a non-OK code', async () => {
    fetchMock.mockResolvedValue(apiResponse(200, { code: 'BAD_REQUEST', message: 'nope' }))

    await expect(http.get('/things')).rejects.toMatchObject({ status: 200, code: 'BAD_REQUEST' })
  })

  it('describes error responses without a JSON body by status', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 403 }))

    await expect(http.get('/things')).rejects.toMatchObject({ status: 403, code: 'HTTP_403' })
  })

  it('turns network failures into NETWORK_ERROR but lets aborts through', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'))
    await expect(http.get('/things')).rejects.toMatchObject({ status: 0, code: 'NETWORK_ERROR' })

    fetchMock.mockRejectedValueOnce(new DOMException('aborted', 'AbortError'))
    await expect(http.get('/things')).rejects.toHaveProperty('name', 'AbortError')
  })

  it('refreshes once after a 401 and retries with the new token', async () => {
    const adapter = fakeAdapter()
    configureHttpAuth(adapter)
    fetchMock
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValueOnce(ok('done'))

    await expect(http.get('/things')).resolves.toBe('done')
    expect(adapter.refreshAccessToken).toHaveBeenCalledWith('token-1')
    expect(authHeaderOfCall(0)).toBe('Bearer token-1')
    expect(authHeaderOfCall(1)).toBe('Bearer token-2')
    expect(adapter.onUnauthenticated).not.toHaveBeenCalled()
  })

  it('gives up when the session cannot be refreshed', async () => {
    const adapter = fakeAdapter({
      refreshAccessToken: vi.fn<AuthAdapter['refreshAccessToken']>(() => Promise.resolve(null)),
    })
    configureHttpAuth(adapter)
    fetchMock.mockResolvedValue(new Response(null, { status: 401 }))

    await expect(http.get('/things')).rejects.toMatchObject({
      status: 401,
      code: 'SESSION_EXPIRED',
    })
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(adapter.onUnauthenticated).toHaveBeenCalledOnce()
  })

  it('sends no credentials when auth is disabled', async () => {
    const adapter = fakeAdapter()
    configureHttpAuth(adapter)
    fetchMock.mockResolvedValue(apiResponse(401, { code: 'UNAUTHORIZED', message: 'bad password' }))

    await expect(http.post('/auth/login', {}, { auth: false })).rejects.toMatchObject({
      code: 'UNAUTHORIZED',
      message: 'bad password',
    })
    expect(authHeaderOfCall(0)).toBeNull()
    expect(adapter.getAccessToken).not.toHaveBeenCalled()
    expect(adapter.onUnauthenticated).not.toHaveBeenCalled()
  })
})
