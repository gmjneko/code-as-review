import { http } from '@/shared/api'

import type { CreateModelConfigRequest, ModelConfig, UpdateModelConfigRequest } from './types'

const BASE = '/model-configs'

export const modelConfigApi = {
  list: (signal?: AbortSignal) => http.get<ModelConfig[]>(BASE, { signal }),

  create: (body: CreateModelConfigRequest) => http.post<ModelConfig>(BASE, body),

  update: (id: number, body: UpdateModelConfigRequest) =>
    http.put<ModelConfig>(`${BASE}/${id}`, body),

  remove: (id: number) => http.delete(`${BASE}/${id}`),
}
