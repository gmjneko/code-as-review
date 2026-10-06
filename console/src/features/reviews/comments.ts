import { COMMENT_CATEGORY_LABELS, COMMENT_SEVERITY_META } from './constants'
import type { CommentCategory, CommentSeverity, ReviewComment } from './types'

export function isCommentSeverity(value: string): value is CommentSeverity {
  return Object.hasOwn(COMMENT_SEVERITY_META, value)
}

export function isCommentCategory(value: string): value is CommentCategory {
  return Object.hasOwn(COMMENT_CATEGORY_LABELS, value)
}

export function describeLines(comment: Pick<ReviewComment, 'startLine' | 'endLine'>): string {
  const { startLine, endLine } = comment
  if (startLine == null) return '整个文件'
  return endLine == null || endLine === startLine
    ? `第 ${startLine} 行`
    : `第 ${startLine}–${endLine} 行`
}

/** Groups comments by file, keeping the backend's order (file path, then start line). */
export function groupByFile(comments: ReviewComment[]): [string, ReviewComment[]][] {
  const groups = new Map<string, ReviewComment[]>()
  for (const comment of comments) {
    const group = groups.get(comment.filePath)
    if (group) group.push(comment)
    else groups.set(comment.filePath, [comment])
  }
  return [...groups]
}
