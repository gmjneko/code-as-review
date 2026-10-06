import { PageContainer } from '@ant-design/pro-components'
import { useQuery, useSuspenseQuery } from '@tanstack/react-query'
import { getRouteApi } from '@tanstack/react-router'
import {
  Alert,
  Badge,
  Button,
  Card,
  Descriptions,
  type DescriptionsProps,
  Flex,
  Popconfirm,
  Result,
  Skeleton,
  Space,
  Switch,
  theme,
  Typography,
} from 'antd'

import { repositoryQueries } from '@/features/repositories'
import { formatDateTime, formatDuration, formatInteger, runAction } from '@/shared/utils'

import { ReviewCommentList } from '../components/ReviewCommentList'
import {
  isTerminalStatus,
  REVIEW_EFFORT_LABELS,
  REVIEW_STATUS_BADGE,
  REVIEW_STATUS_ENUM,
  REVIEW_TARGET_LABELS,
  REVIEW_TRIGGER_LABELS,
} from '../constants'
import { reviewQueries, useCancelReview } from '../queries'
import type { ReviewTask } from '../types'

const routeApi = getRouteApi('/_authenticated/reviews/$reviewId')

function shortSha(sha: string | null): string | null {
  return sha ? sha.slice(0, 10) : null
}

function describeRef(ref: string | null, sha: string | null): string {
  const short = shortSha(sha)
  if (ref && short) return `${ref}（${short}）`
  return ref ?? short ?? '-'
}

function describeTask(
  task: ReviewTask,
  repositoryName: string | undefined,
): DescriptionsProps['items'] {
  return [
    { key: 'repository', label: '仓库', children: repositoryName ?? `#${task.repositoryId}` },
    { key: 'target', label: '评审对象', children: REVIEW_TARGET_LABELS[task.targetType] },
    { key: 'trigger', label: '触发方式', children: REVIEW_TRIGGER_LABELS[task.triggerType] },
    { key: 'base', label: '基线（base）', children: describeRef(task.baseRef, task.baseSha) },
    { key: 'head', label: '待评审（head）', children: describeRef(task.headRef, task.headSha) },
    { key: 'effort', label: '评审强度', children: REVIEW_EFFORT_LABELS[task.effort] },
    {
      key: 'files',
      label: '变更文件 / 已评审',
      children: `${formatInteger(task.filesChanged)} / ${formatInteger(task.filesReviewed)}`,
    },
    { key: 'rounds', label: '完成轮次', children: formatInteger(task.roundsCompleted) },
    { key: 'comments', label: '意见数', children: formatInteger(task.commentCount) },
    {
      key: 'tokens',
      label: 'Token（输入 / 输出）',
      children: `${formatInteger(task.inputTokens)} / ${formatInteger(task.outputTokens)}`,
    },
    { key: 'createdAt', label: '创建时间', children: formatDateTime(task.createdAt) },
    {
      key: 'duration',
      label: '耗时',
      children: formatDuration(task.startedAt, task.finishedAt),
    },
  ]
}

function CommentsSection({ task }: { task: ReviewTask }) {
  const search = routeApi.useSearch()
  const navigate = routeApi.useNavigate()
  const finished = isTerminalStatus(task.status)
  const comments = useQuery({
    ...reviewQueries.comments(task.id, search.includeFiltered),
    enabled: finished,
  })

  return (
    <Card
      title="评审意见"
      extra={
        <Space>
          <Typography.Text type="secondary">显示被过滤的意见</Typography.Text>
          <Switch
            size="small"
            aria-label="显示被过滤的意见"
            checked={search.includeFiltered}
            onChange={(includeFiltered) =>
              void navigate({ search: (prev) => ({ ...prev, includeFiltered }), replace: true })
            }
          />
        </Space>
      }
    >
      {!finished ? (
        <Result
          status="info"
          title="评审进行中"
          subTitle="评审完成后将在这里展示意见，页面会自动刷新。"
        />
      ) : comments.isError ? (
        <Result
          status="error"
          title="评审意见加载失败"
          extra={
            <Button onClick={() => void comments.refetch()} loading={comments.isFetching}>
              重试
            </Button>
          }
        />
      ) : comments.data ? (
        <ReviewCommentList comments={comments.data} />
      ) : (
        <Skeleton active />
      )}
    </Card>
  )
}

export function ReviewDetailPage() {
  const { reviewId } = routeApi.useParams()
  const navigate = routeApi.useNavigate()
  const { token } = theme.useToken()
  // The route loader has already fetched the task; the query keeps polling while it runs.
  const { data: task } = useSuspenseQuery(reviewQueries.detail(reviewId))
  const repositories = useQuery(repositoryQueries.list())
  const repositoryName = repositories.data?.find((repo) => repo.id === task.repositoryId)?.name
  const cancel = useCancelReview()
  const status = REVIEW_STATUS_ENUM[task.status]

  return (
    <PageContainer
      title={`评审任务 #${task.id}`}
      onBack={() => void navigate({ to: '/reviews' })}
      tags={<Badge status={REVIEW_STATUS_BADGE[status.status]} text={status.text} />}
      extra={
        isTerminalStatus(task.status)
          ? undefined
          : [
              <Popconfirm
                key="cancel"
                title="取消评审"
                description="确定取消该评审任务吗？"
                okButtonProps={{ danger: true }}
                onConfirm={() => runAction(() => cancel.mutateAsync(task.id))}
              >
                <Button danger>取消评审</Button>
              </Popconfirm>,
            ]
      }
    >
      <Flex vertical gap={token.marginMD}>
        {task.errorMessage && (
          <Alert
            type={task.status === 'SUCCEEDED' ? 'warning' : 'error'}
            showIcon
            title={task.status === 'SUCCEEDED' ? '评审已完成，但有警告' : '评审失败'}
            description={<span style={{ whiteSpace: 'pre-wrap' }}>{task.errorMessage}</span>}
          />
        )}
        <Card>
          <Descriptions
            column={{ xs: 1, sm: 2, lg: 3 }}
            items={describeTask(task, repositoryName)}
          />
        </Card>
        {task.summary && (
          <Card title="评审总结">
            <Typography.Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>
              {task.summary}
            </Typography.Paragraph>
          </Card>
        )}
        <CommentsSection task={task} />
      </Flex>
    </PageContainer>
  )
}
