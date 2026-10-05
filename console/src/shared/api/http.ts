import { env } from '@/shared/config'

import { ApiError, ClientErrorCode, defaultMessageForStatus } from './errors'
import type { ApiEnvelope } from './types'

export type QueryParams = Record<string, string | number | boolean | null | undefined>

export interface RequestOptions {
  query?: QueryParams
  body?: unknown
  signal?: AbortSignal
  /** Attach the bearer token and recover from a 401 by refreshing once. Defaults to `true`. */
  auth?: boolean
}

/**
 * How the HTTP client obtains credentials. Implemented by the auth feature and installed at
 * startup, so this module stays free of business dependencies.
 */
export interface AuthAdapter {
  /** A usable access token (refreshed first if it is about to expire), or `null` if signed out. */
  getAccessToken: () => Promise<string | null>
  /** Called after the server rejected `rejectedToken`; resolves to a new token or `null`. */
  refreshAccessToken: (rejectedToken: string) => Promise<string | null>
  /** The session cannot be recovered; the user has to sign in again. */
  onUnauthenticated: () => void
}

let authAdapter: AuthAdapter | null = null

export function configureHttpAuth(adapter: AuthAdapter | null): void {
  authAdapter = adapter
}

type Method = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

function buildUrl(path: string, query?: QueryParams): string {
  const url = `${env.apiBaseUrl}${path}`
  if (!query) return url
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== null) params.append(key, String(value))
  }
  const search = params.toString()
  return search ? `${url}?${search}` : url
}

async function send(
  method: Method,
  path: string,
  options: RequestOptions,
  token: string | null,
): Promise<Response> {
  const headers = new Headers({ Accept: 'application/json' })
  if (options.body !== undefined) headers.set('Content-Type', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)

  try {
    return await fetch(buildUrl(path, options.query), {
      method,
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: options.signal,
    })
  } catch (error) {
    // Let cancellations propagate untouched so callers (and TanStack Query) can tell them apart.
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw new ApiError(0, ClientErrorCode.Network, '网络连接失败，请检查网络后重试')
  }
}

async function parse<T>(response: Response): Promise<T> {
  const isJson = response.headers.get('Content-Type')?.includes('application/json') ?? false
  if (!isJson) {
    // Spring Security answers 401/403 with an empty body; proxies answer 5xx with HTML.
    if (!response.ok) {
      throw new ApiError(
        response.status,
        `HTTP_${response.status}`,
        defaultMessageForStatus(response.status),
      )
    }
    throw new ApiError(
      response.status,
      ClientErrorCode.InvalidResponse,
      '服务器返回了无法识别的响应',
    )
  }

  // oxlint-disable-next-line typescript/no-unsafe-type-assertion -- trust boundary: DTO types mirror the backend contract
  const envelope = (await response.json()) as ApiEnvelope<T>
  if (!response.ok || envelope.code !== 'OK') {
    throw new ApiError(
      response.status,
      envelope.code,
      envelope.message || defaultMessageForStatus(response.status),
    )
  }
  return envelope.data
}

async function request<T>(method: Method, path: string, options: RequestOptions = {}): Promise<T> {
  const adapter = options.auth === false ? null : authAdapter
  const token = adapter ? await adapter.getAccessToken() : null

  let response = await send(method, path, options, token)

  if (response.status === 401 && adapter) {
    const renewed = token ? await adapter.refreshAccessToken(token) : null
    if (renewed) {
      response = await send(method, path, options, renewed)
    }
    if (response.status === 401) {
      adapter.onUnauthenticated()
      throw new ApiError(401, ClientErrorCode.SessionExpired, defaultMessageForStatus(401))
    }
  }

  return parse<T>(response)
}

/**
 * The single entry point for calling the backend. Resolves to `ApiResponse.data` and rejects
 * with {@link ApiError}. Paths are relative to `VITE_API_BASE_URL`, e.g. `/repositories`.
 */
export const http = {
  get: <T>(path: string, options?: Omit<RequestOptions, 'body'>) =>
    request<T>('GET', path, options),
  post: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, 'body'>) =>
    request<T>('POST', path, { ...options, body }),
  put: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, 'body'>) =>
    request<T>('PUT', path, { ...options, body }),
  patch: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, 'body'>) =>
    request<T>('PATCH', path, { ...options, body }),
  delete: <T = void>(path: string, options?: Omit<RequestOptions, 'body'>) =>
    request<T>('DELETE', path, options),
}
