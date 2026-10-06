import type { InstantString } from '@/shared/api'

/** Mirrors `llm.dto.ModelConfigDtos.View`. */
export interface ModelConfig {
  id: number
  name: string
  baseUrl: string
  models: Record<string, ModelCapabilities>
  apiKeyMasked: string
  isDefault: boolean
  createdAt: InstantString
}

export interface ModelCapabilities {
  limit: {
    context: number
    output: number
  }
  modalities: {
    input: string[]
    reasoning_effort: string[]
  }
}

/** Mirrors `llm.dto.ModelConfigDtos.Create`. */
export interface CreateModelConfigRequest {
  name: string
  baseUrl: string
  models: Record<string, ModelCapabilities>
  apiKey: string
  isDefault: boolean
}

/** Mirrors `llm.dto.ModelConfigDtos.Update`: omitted fields stay unchanged. */
export type UpdateModelConfigRequest = Partial<CreateModelConfigRequest>
