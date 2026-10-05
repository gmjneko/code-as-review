/** Codes produced by the client itself rather than returned by the backend. */
export const ClientErrorCode = {
  Network: 'NETWORK_ERROR',
  InvalidResponse: 'INVALID_RESPONSE',
  /** An authenticated request failed and the session could not be refreshed. */
  SessionExpired: 'SESSION_EXPIRED',
} as const

/**
 * Every failed request rejects with an ApiError. `code` is the backend's `ApiResponse.code`
 * (e.g. `BAD_REQUEST`, `CONFLICT`) or a {@link ClientErrorCode}; `status` is 0 when no
 * response was received.
 */
export class ApiError extends Error {
  override readonly name = 'ApiError'
  readonly status: number
  readonly code: string

  constructor(status: number, code: string, message: string) {
    super(message)
    this.status = status
    this.code = code
  }

  /** Any 401, including a wrong password on the login endpoint. */
  get isUnauthorized(): boolean {
    return this.status === 401
  }

  /** The session is gone and the app is already redirecting to the login page. */
  get isSessionExpired(): boolean {
    return this.code === ClientErrorCode.SessionExpired
  }

  /** 4xx responses describe a problem with the request, so retrying will not help. */
  get isClientError(): boolean {
    return this.status >= 400 && this.status < 500
  }
}

const STATUS_MESSAGES: Record<number, string> = {
  401: '登录已失效，请重新登录',
  403: '没有权限执行该操作',
  404: '请求的资源不存在',
  502: '服务暂时不可用',
  503: '服务暂时不可用',
  504: '服务响应超时',
}

export function defaultMessageForStatus(status: number): string {
  return STATUS_MESSAGES[status] ?? `请求失败（HTTP ${status}）`
}

/** Turns anything thrown into text that is safe to show to the user. */
export function getErrorMessage(error: unknown): string {
  if (error instanceof Error && error.message) {
    return error.message
  }
  return '发生未知错误'
}
