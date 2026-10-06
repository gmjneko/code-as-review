import type { InstantString } from '@/shared/api'

/** Mirrors `llm.dto.ModelConfigDtos.View`. */
export interface ModelConfig {
  id: number
  name: string
  baseUrl: string
  modelName: string
  apiKeyMasked: string
  isDefault: boolean
  createdAt: InstantString
}

/** Mirrors `llm.dto.ModelConfigDtos.Create`. */
export interface CreateModelConfigRequest {
  name: string
  baseUrl: string
  modelName: string
  apiKey: string
  isDefault: boolean
}

/** Mirrors `llm.dto.ModelConfigDtos.Update`: omitted fields stay unchanged. */
export type UpdateModelConfigRequest = Partial<CreateModelConfigRequest>
