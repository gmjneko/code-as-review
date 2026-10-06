import type { SourceType } from './types'

export const SOURCE_TYPE_META: Record<SourceType, { label: string; color: string }> = {
  LOCAL: { label: '本地仓库', color: 'default' },
  GITHUB: { label: 'GitHub', color: 'geekblue' },
  GITLAB: { label: 'GitLab', color: 'orange' },
}

/** Source types backed by a registered SCM provider. */
export const SUPPORTED_SOURCE_TYPES: readonly SourceType[] = ['LOCAL', 'GITHUB']

/** Display order of the source type options. */
export const SOURCE_TYPES: readonly SourceType[] = ['LOCAL', 'GITHUB', 'GITLAB']
