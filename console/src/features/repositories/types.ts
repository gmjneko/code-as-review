import type { InstantString } from '@/shared/api'

/** Mirrors `repo.domain.SourceType`. */
export type SourceType = 'LOCAL' | 'GITHUB' | 'GITLAB'

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
}

/**
 * Mirrors `repo.dto.RepoDtos.Update`. A blank or omitted `name` is ignored; an omitted
 * `defaultBranch` is left unchanged, while an empty string clears it.
 */
export interface UpdateRepositoryRequest {
  name?: string
  defaultBranch?: string
}
