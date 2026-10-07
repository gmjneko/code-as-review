import { keepPreviousData, queryOptions, useMutation, useQueryClient } from '@tanstack/react-query'

import { issueApi } from './api'
import { isTerminalStatus } from './constants'
import type { CreateIssueRequest, IssueListParams, IssueTaskStatus } from './types'

export const issueKeys = {
  all: ['issues'] as const,
  list: (params: IssueListParams) => [...issueKeys.all, 'list', params] as const,
  detail: (id: number) => [...issueKeys.all, 'detail', id] as const,
  report: (id: number) => [...issueKeys.detail(id), 'report'] as const,
}

export const issueQueries = {
  list: (params: IssueListParams) =>
    queryOptions({
      queryKey: issueKeys.list(params),
      queryFn: ({ signal }) => issueApi.list(params, signal),
      placeholderData: keepPreviousData,
      refetchInterval: (query) =>
        query.state.data?.records.some((task) => !isTerminalStatus(task.status)) ? 5000 : false,
    }),
  detail: (id: number) =>
    queryOptions({
      queryKey: issueKeys.detail(id),
      queryFn: ({ signal }) => issueApi.get(id, signal),
      refetchInterval: (query) =>
        query.state.data && !isTerminalStatus(query.state.data.status) ? 5000 : false,
    }),
  report: (id: number, status: IssueTaskStatus) =>
    queryOptions({
      queryKey: issueKeys.report(id),
      queryFn: ({ signal }) => issueApi.report(id, signal),
      enabled: status === 'SUCCEEDED',
    }),
}

export function useCreateIssue() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateIssueRequest) => issueApi.create(body),
    onSuccess: () => client.invalidateQueries({ queryKey: issueKeys.all }),
    meta: { successMessage: 'Issue 调查任务已创建' },
  })
}

export function useCancelIssue() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => issueApi.cancel(id),
    onSuccess: () => client.invalidateQueries({ queryKey: issueKeys.all }),
    meta: { successMessage: '已提交取消请求' },
  })
}
