import type { InstantString } from '@/shared/api'

/** Mirrors `repo.domain.SourceType`. */
export type SourceType = 'LOCAL' | 'GITHUB' | 'GITLAB'
export type ExecutionMode = 'LOCAL' | 'SANDBOX'

/** Mirrors `repo.dto.RepoDtos.View`. */
export interface Repository {
  id: number
  name: string
  sourceType: SourceType
  localPath: string | null
  remoteUrl: string | null
  externalFullName: string | null
  defaultBranch: string | null
  credentialId: number | null
  executionMode?: ExecutionMode
  lastSyncedAt: InstantString | null
  createdAt: InstantString
}

/** Mirrors `repo.dto.RepoDtos.Create`. */
export interface CreateRepositoryRequest {
  name: string
  sourceType: SourceType
  localPath?: string
  remoteUrl?: string
  defaultBranch?: string
  credentialId?: number
  executionMode?: ExecutionMode
}

export interface WebhookRule {
  id: number | null
  eventKind: 'PULL_REQUEST' | 'ISSUE' | 'PR_COMMENT' | 'ISSUE_COMMENT'
  action: string
  mode: 'AUTO' | 'COMMAND'
  command: string | null
  enabled: boolean
  effort: 'LOW' | 'MEDIUM' | 'HIGH'
  modelConfigId: number | null
  modelName: string | null
}

export interface WebhookConfig {
  configured: boolean
  endpoint: string
  secret: string | null
  rules: WebhookRule[]
}

/**
 * Mirrors `repo.dto.RepoDtos.Update`. A blank or omitted `name` is ignored; an omitted
 * `defaultBranch` is left unchanged, while a blank one clears it (stored as `null`).
 */
export interface UpdateRepositoryRequest {
  name?: string
  defaultBranch?: string
  executionMode?: ExecutionMode
}
