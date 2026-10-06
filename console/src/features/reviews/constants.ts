import type {
  CommentCategory,
  CommentSeverity,
  ReviewEffort,
  ReviewTargetType,
  ReviewTaskStatus,
  ReviewTriggerType,
} from './types'

type BadgeStatus = 'Default' | 'Processing' | 'Success' | 'Error' | 'Warning'

/** Shaped as a ProTable `valueEnum`. */
export const REVIEW_STATUS_ENUM: Record<ReviewTaskStatus, { text: string; status: BadgeStatus }> = {
  PENDING: { text: '排队中', status: 'Default' },
  RUNNING: { text: '评审中', status: 'Processing' },
  SUCCEEDED: { text: '已完成', status: 'Success' },
  FAILED: { text: '失败', status: 'Error' },
  CANCELLED: { text: '已取消', status: 'Warning' },
}

/** The same status as an antd `Badge` status, for places outside ProTable. */
export const REVIEW_STATUS_BADGE: Record<
  BadgeStatus,
  'default' | 'processing' | 'success' | 'error' | 'warning'
> = {
  Default: 'default',
  Processing: 'processing',
  Success: 'success',
  Error: 'error',
  Warning: 'warning',
}

export const REVIEW_TARGET_LABELS: Record<ReviewTargetType, string> = {
  LOCAL_WORKING_TREE: '本地工作区',
  COMMIT_RANGE: '提交区间',
  PULL_REQUEST: 'Pull Request',
  ISSUE: 'Issue',
}

export const REVIEW_TRIGGER_LABELS: Record<ReviewTriggerType, string> = {
  API: '手动',
  WEBHOOK_COMMAND: 'Webhook 指令',
}

export const REVIEW_EFFORT_LABELS: Record<ReviewEffort, string> = {
  LOW: '低',
  MEDIUM: '中',
  HIGH: '高',
}

/** Mirrors `ReviewEnums.Effort#rounds`. */
export const REVIEW_EFFORT_ROUNDS: Record<ReviewEffort, number> = {
  LOW: 1,
  MEDIUM: 2,
  HIGH: 3,
}

export const REVIEW_EFFORTS: readonly ReviewEffort[] = ['LOW', 'MEDIUM', 'HIGH']

/**
 * Targets that can be requested from the console. PR / issue reviews need an external reference
 * that `ReviewDtos.Create` does not accept yet; they will come with webhook support.
 */
export const CREATABLE_TARGET_TYPES: readonly ReviewTargetType[] = [
  'LOCAL_WORKING_TREE',
  'COMMIT_RANGE',
]

/** Mirrors `scm.git.GitCli#requireSafeRef`. */
export const SAFE_GIT_REF = /^[A-Za-z0-9][A-Za-z0-9._/@{}^~-]{0,254}$/

export const COMMENT_SEVERITY_META: Record<CommentSeverity, { label: string; color: string }> = {
  critical: { label: '严重', color: 'magenta' },
  high: { label: '高', color: 'red' },
  medium: { label: '中', color: 'orange' },
  low: { label: '低', color: 'blue' },
}

export const COMMENT_CATEGORY_LABELS: Record<CommentCategory, string> = {
  bug: '缺陷',
  security: '安全',
  performance: '性能',
  maintainability: '可维护性',
  test: '测试',
  style: '代码风格',
  documentation: '文档',
  other: '其他',
}

/** Mirrors `ReviewEnums.TaskStatus#terminal`. */
export function isTerminalStatus(status: ReviewTaskStatus): boolean {
  return status === 'SUCCEEDED' || status === 'FAILED' || status === 'CANCELLED'
}

/** How often to poll while any visible task is still pending or running. */
export const ACTIVE_TASK_POLL_INTERVAL_MS = 5_000
