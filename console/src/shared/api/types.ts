/** Mirrors `common.api.ApiResponse` on the backend. */
export interface ApiEnvelope<T> {
  code: string
  message: string
  data: T
}

/** Mirrors `common.api.PageResult` on the backend. `page` is 1-based. */
export interface PageResult<T> {
  page: number
  size: number
  total: number
  records: T[]
}

export interface PageQuery {
  page: number
  size: number
}

/** Backend `LocalDateTime` serialized without an offset, e.g. `2026-10-05T23:13:00`. */
export type LocalDateTimeString = string
