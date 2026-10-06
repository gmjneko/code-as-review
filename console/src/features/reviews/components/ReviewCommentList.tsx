import { Collapse, Empty, Flex, theme, Typography } from 'antd'

import { groupByFile } from '../comments'
import type { ReviewComment } from '../types'
import { ReviewCommentCard } from './ReviewCommentCard'

interface ReviewCommentListProps {
  comments: ReviewComment[]
  /** The review is still running, so an empty list is not the final answer yet. */
  running?: boolean
}

export function ReviewCommentList({ comments, running = false }: ReviewCommentListProps) {
  const { token } = theme.useToken()

  if (comments.length === 0) {
    return (
      <Empty
        image={Empty.PRESENTED_IMAGE_SIMPLE}
        description={running ? '暂无意见' : '没有发现需要关注的问题'}
      />
    )
  }

  const groups = groupByFile(comments)
  return (
    <Collapse
      defaultActiveKey={groups.map(([path]) => path)}
      items={groups.map(([path, items]) => ({
        key: path,
        label: (
          <Typography.Text code style={{ wordBreak: 'break-all' }}>
            {path}
          </Typography.Text>
        ),
        extra: <Typography.Text type="secondary">{items.length} 条</Typography.Text>,
        children: (
          <Flex vertical gap={token.marginSM}>
            {items.map((comment) => (
              <ReviewCommentCard key={comment.id} comment={comment} />
            ))}
          </Flex>
        ),
      }))}
    />
  )
}
