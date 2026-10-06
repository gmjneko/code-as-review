import { http } from '@/shared/api'

import type {
  CreateRepositoryRequest,
  Repository,
  UpdateRepositoryRequest,
  WebhookConfig,
  WebhookRule,
} from './types'

const BASE = '/repositories'

export const repositoryApi = {
  list: (signal?: AbortSignal) => http.get<Repository[]>(BASE, { signal }),

  create: (body: CreateRepositoryRequest) => http.post<Repository>(BASE, body),

  update: (id: number, body: UpdateRepositoryRequest) =>
    http.put<Repository>(`${BASE}/${id}`, body),

  remove: (id: number) => http.delete(`${BASE}/${id}`),

  webhook: (id: number) => http.get<WebhookConfig>(`${BASE}/${id}/github-webhook`),
  rotateWebhook: (id: number) => http.post<WebhookConfig>(`${BASE}/${id}/github-webhook`),
  replaceTriggerRules: (id: number, rules: WebhookRule[]) =>
    http.put<WebhookRule[]>(`${BASE}/${id}/github-trigger-rules`, { rules }),
}
