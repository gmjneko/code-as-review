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

/** Backend `Instant` serialized as ISO-8601 UTC, e.g. `2026-10-05T15:13:00Z`. */
export type InstantString = string
