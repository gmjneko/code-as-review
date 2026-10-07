import type { IssueEffort, IssueTaskStatus } from './types'

export const ISSUE_STATUS_META: Record<
  IssueTaskStatus,
  { text: string; status: 'default' | 'processing' | 'success' | 'error' | 'warning' }
> = {
  PENDING: { text: '排队中', status: 'default' },
  RUNNING: { text: '调查中', status: 'processing' },
  SUCCEEDED: { text: '已完成', status: 'success' },
  FAILED: { text: '失败', status: 'error' },
  CANCELLED: { text: '已取消', status: 'warning' },
}

export const ISSUE_EFFORT_LABELS: Record<IssueEffort, string> = {
  LOW: '低',
  MEDIUM: '中',
  HIGH: '高',
}

export function isTerminalStatus(status: IssueTaskStatus): boolean {
  return status === 'SUCCEEDED' || status === 'FAILED' || status === 'CANCELLED'
}
