import { Card, Flex, Tag, theme, Typography } from 'antd'

import { describeLines, isCommentCategory, isCommentSeverity } from '../comments'
import { COMMENT_CATEGORY_LABELS, COMMENT_SEVERITY_META } from '../constants'
import type { ReviewComment } from '../types'

interface ReviewCommentCardProps {
  comment: ReviewComment
}

function CodeBlock({ label, code, tone }: { label: string; code: string; tone: 'old' | 'new' }) {
  const { token } = theme.useToken()
  return (
    <div>
      <Typography.Text type="secondary" style={{ fontSize: token.fontSizeSM }}>
        {label}
      </Typography.Text>
      <pre
        style={{
          margin: 0,
          marginBlockStart: token.marginXXS,
          padding: token.paddingSM,
          overflowX: 'auto',
          fontFamily: token.fontFamilyCode,
          fontSize: token.fontSizeSM,
          lineHeight: token.lineHeight,
          borderRadius: token.borderRadius,
          background: tone === 'old' ? token.colorErrorBg : token.colorSuccessBg,
          border: `1px solid ${tone === 'old' ? token.colorErrorBorder : token.colorSuccessBorder}`,
        }}
      >
        {code}
      </pre>
    </div>
  )
}

export function ReviewCommentCard({ comment }: ReviewCommentCardProps) {
  const { token } = theme.useToken()
  const severity = isCommentSeverity(comment.severity)
    ? COMMENT_SEVERITY_META[comment.severity]
    : null
  const category = isCommentCategory(comment.category)
    ? COMMENT_CATEGORY_LABELS[comment.category]
    : comment.category
  const filtered = comment.status === 'FILTERED'

  return (
    <Card size="small" style={filtered ? { opacity: 0.65 } : undefined}>
      <Flex vertical gap={token.marginSM}>
        <Flex wrap gap={token.marginXS} align="center">
          <Tag color={severity?.color}>{severity?.label ?? comment.severity}</Tag>
          <Tag>{category}</Tag>
          <Typography.Text type="secondary">{describeLines(comment)}</Typography.Text>
          {comment.round != null && (
            <Typography.Text type="secondary">· 第 {comment.round} 轮</Typography.Text>
          )}
          {filtered && <Tag color="default">已过滤</Tag>}
        </Flex>
        <Typography.Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>
          {comment.content}
        </Typography.Paragraph>
        {comment.existingCode && (
          <CodeBlock label="原代码" code={comment.existingCode} tone="old" />
        )}
        {comment.suggestionCode && (
          <CodeBlock label="建议修改" code={comment.suggestionCode} tone="new" />
        )}
        {filtered && comment.filterReason && (
          <Typography.Text type="secondary">过滤原因：{comment.filterReason}</Typography.Text>
        )}
      </Flex>
    </Card>
  )
}
