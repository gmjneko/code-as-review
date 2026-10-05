import type { LocalDateTimeString } from '@/shared/api'

// Unions mirror review.domain.ReviewEnums. Display metadata is keyed by these unions in
// constants.ts, so a new backend value fails type checking until it is handled.

export type ReviewTaskStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED'

export type ReviewTargetType = 'LOCAL_WORKING_TREE' | 'COMMIT_RANGE' | 'PULL_REQUEST' | 'ISSUE'

export type ReviewTriggerType = 'API' | 'WEBHOOK_COMMAND'

export type ReviewEffort = 'LOW' | 'MEDIUM' | 'HIGH'

/** Mirrors `review.task.dto.ReviewDtos.TaskView`. */
export interface ReviewTask {
  id: number
  repositoryId: number
  targetType: ReviewTargetType
  triggerType: ReviewTriggerType
  baseRef: string | null
  headRef: string | null
  baseSha: string | null
  headSha: string | null
  effort: ReviewEffort
  status: ReviewTaskStatus
  filesChanged: number | null
  filesReviewed: number | null
  commentCount: number | null
  roundsCompleted: number | null
  inputTokens: number | null
  outputTokens: number | null
  summary: string | null
  errorMessage: string | null
  createdAt: LocalDateTimeString
  startedAt: LocalDateTimeString | null
  finishedAt: LocalDateTimeString | null
}

export interface ReviewListParams {
  page: number
  size: number
}
