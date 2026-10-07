import type { InstantString } from '@/shared/api'

export type IssueTaskStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED'
export type ExecutionMode = 'LOCAL' | 'SANDBOX'
export type IssueTriggerType = 'API' | 'AUTO_EVENT' | 'WEBHOOK_COMMAND'
export type IssueEffort = 'LOW' | 'MEDIUM' | 'HIGH'

export interface IssueTask {
  id: number
  repositoryId: number
  issueNumber: string
  command: string | null
  triggerType: IssueTriggerType
  executionMode: ExecutionMode
  baseRef: string | null
  baseSha: string | null
  effort: IssueEffort
  modelConfigId: number | null
  modelName: string | null
  status: IssueTaskStatus
  summary: string | null
  errorMessage: string | null
  inputTokens: number | null
  outputTokens: number | null
  executionLog: string | null
  externalCommentId: string | null
  createdAt: InstantString
  startedAt: InstantString | null
  finishedAt: InstantString | null
}

export interface IssueReport {
  reproductionStatus: string
  summary: string
  reproductionSteps: string[]
  observations: string[]
  rootCause: string
  suggestedFix: string
  markdown: string
}

export interface CreateIssueRequest {
  repositoryId: number
  issueNumber: string
  effort?: IssueEffort
  background?: string
  modelConfigId?: number
  modelName?: string
}

export interface IssueListParams {
  page: number
  size: number
  repositoryId?: number
}
