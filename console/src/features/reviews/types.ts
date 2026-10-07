import type { InstantString } from '@/shared/api'

// Unions mirror review.domain.ReviewEnums. Display metadata is keyed by these unions in
// constants.ts, so a new backend value fails type checking until it is handled.

export type ReviewTaskStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED'

export type ReviewTargetType = 'LOCAL_WORKING_TREE' | 'COMMIT_RANGE' | 'PULL_REQUEST' | 'ISSUE'

export type ReviewTriggerType = 'API' | 'AUTO_EVENT' | 'WEBHOOK_COMMAND'

export type ReviewEffort = 'LOW' | 'MEDIUM' | 'HIGH'

export type ReviewCommentStatus = 'CONFIRMED' | 'FILTERED'

// The backend stores these as plain strings, normalised by `review.agent.ReviewTools`.
export type CommentSeverity = 'critical' | 'high' | 'medium' | 'low'

export type CommentCategory =
  | 'bug'
  | 'security'
  | 'performance'
  | 'maintainability'
  | 'test'
  | 'style'
  | 'documentation'
  | 'other'

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
  externalRef: string | null
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
  createdAt: InstantString
  startedAt: InstantString | null
  finishedAt: InstantString | null
}

export interface ReviewListParams {
  page: number
  size: number
  repositoryId?: number
}

/**
 * Mirrors `review.task.dto.ReviewDtos.Create`. `baseRef` / `headRef` are required for
 * `COMMIT_RANGE`, `externalRef` (the PR / issue number) for `PULL_REQUEST` / `ISSUE`; omitted
 * `effort` / `modelConfigId` fall back to the server default and the user's default model.
 */
export interface CreateReviewRequest {
  repositoryId: number
  targetType: ReviewTargetType
  baseRef?: string
  headRef?: string
  externalRef?: string
  effort?: ReviewEffort
  background?: string
  modelConfigId?: number
  modelName?: string
}

/** Mirrors `review.task.dto.ReviewDtos.CommentView`. */
export interface ReviewComment {
  id: number
  filePath: string
  startLine: number | null
  endLine: number | null
  category: string
  severity: string
  content: string
  existingCode: string | null
  suggestionCode: string | null
  round: number | null
  status: ReviewCommentStatus
  filterReason: string | null
}
