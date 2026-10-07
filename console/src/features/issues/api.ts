import { http, type PageResult } from '@/shared/api'

import type { CreateIssueRequest, IssueListParams, IssueReport, IssueTask } from './types'

const BASE = '/issues'

export const issueApi = {
  list: (params: IssueListParams, signal?: AbortSignal) =>
    http.get<PageResult<IssueTask>>(BASE, { query: { ...params }, signal }),
  get: (id: number, signal?: AbortSignal) => http.get<IssueTask>(`${BASE}/${id}`, { signal }),
  report: (id: number, signal?: AbortSignal) =>
    http.get<IssueReport | null>(`${BASE}/${id}/report`, { signal }),
  create: (body: CreateIssueRequest) => http.post<IssueTask>(BASE, body),
  cancel: (id: number) => http.post<void>(`${BASE}/${id}/cancel`),
}
