import { describe, expect, it } from 'vitest'

import { describeLines, groupByFile } from './comments'
import type { ReviewComment } from './types'

function comment(id: number, filePath: string): ReviewComment {
  return {
    id,
    filePath,
    startLine: 1,
    endLine: 1,
    category: 'bug',
    severity: 'high',
    content: '',
    existingCode: null,
    suggestionCode: null,
    round: 1,
    status: 'CONFIRMED',
    filterReason: null,
  }
}

describe('describeLines', () => {
  it('covers whole-file, single-line and range comments', () => {
    expect(describeLines({ startLine: null, endLine: null })).toBe('整个文件')
    expect(describeLines({ startLine: 4, endLine: 4 })).toBe('第 4 行')
    expect(describeLines({ startLine: 4, endLine: null })).toBe('第 4 行')
    expect(describeLines({ startLine: 4, endLine: 9 })).toBe('第 4–9 行')
  })
})

describe('groupByFile', () => {
  it('groups by path in first-seen order and keeps the order within a file', () => {
    const groups = groupByFile([comment(1, 'b.ts'), comment(2, 'a.ts'), comment(3, 'b.ts')])

    expect(groups.map(([path, items]) => [path, items.map((c) => c.id)])).toEqual([
      ['b.ts', [1, 3]],
      ['a.ts', [2]],
    ])
  })
})
