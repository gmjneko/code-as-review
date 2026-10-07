import { PageContainer } from '@ant-design/pro-components'
import { useQuery, useSuspenseQuery } from '@tanstack/react-query'
import { getRouteApi } from '@tanstack/react-router'
import { Alert, Badge, Button, Card, Descriptions, Popconfirm, Typography } from 'antd'

import { repositoryQueries } from '@/features/repositories'
import { runAction } from '@/shared/utils'

import { ISSUE_EFFORT_LABELS, ISSUE_STATUS_META, isTerminalStatus } from '../constants'
import { issueQueries, useCancelIssue } from '../queries'

const routeApi = getRouteApi('/_authenticated/issues/$issueId')

export function IssueDetailPage() {
  const { issueId } = routeApi.useParams()
  const task = useSuspenseQuery(issueQueries.detail(issueId)).data
  const report = useQuery(issueQueries.report(issueId, task.status))
  const repositories = useQuery(repositoryQueries.list())
  const cancel = useCancelIssue()
  const meta = ISSUE_STATUS_META[task.status]
  const repository = repositories.data?.find((r) => r.id === task.repositoryId)
  const commentUrl =
    repository?.externalFullName && task.externalCommentId
      ? `https://github.com/${repository.externalFullName}/issues/${task.issueNumber}#issuecomment-${task.externalCommentId}`
      : undefined
  return (
    <PageContainer
      title={`Issue 调查 #${task.id}`}
      onBack={() => history.back()}
      tags={<Badge status={meta.status} text={meta.text} />}
      extra={
        !isTerminalStatus(task.status) ? (
          <Popconfirm
            title="取消调查？"
            onConfirm={() => runAction(() => cancel.mutateAsync(task.id))}
          >
            <Button danger>取消调查</Button>
          </Popconfirm>
        ) : undefined
      }
    >
      {task.errorMessage && (
        <Alert type="error" showIcon message="调查失败" description={task.errorMessage} />
      )}
      <Card style={{ marginTop: 16 }}>
        <Descriptions
          column={{ xs: 1, sm: 2 }}
          items={[
            {
              key: 'repo',
              label: '仓库',
              children: repository?.name ?? `#${task.repositoryId}`,
            },
            { key: 'issue', label: 'Issue', children: `#${task.issueNumber}` },
            { key: 'mode', label: '执行模式', children: task.executionMode },
            {
              key: 'base',
              label: '基线',
              children: `${task.baseRef ?? '-'} ${task.baseSha ? `(${task.baseSha.slice(0, 10)})` : ''}`,
            },
            { key: 'effort', label: '强度', children: ISSUE_EFFORT_LABELS[task.effort] },
            {
              key: 'tokens',
              label: 'Token（输入 / 输出）',
              children: `${task.inputTokens ?? 0} / ${task.outputTokens ?? 0}`,
            },
            { key: 'created', label: '创建时间', children: task.createdAt },
            ...(commentUrl
              ? [
                  {
                    key: 'comment',
                    label: 'GitHub 评论',
                    children: (
                      <a href={commentUrl} target="_blank" rel="noreferrer">
                        打开评论
                      </a>
                    ),
                  },
                ]
              : []),
          ]}
        />
      </Card>
      {report.data && (
        <Card title="调查报告" style={{ marginTop: 16 }}>
          <Typography.Title level={4}>{report.data.summary}</Typography.Title>
          <Typography.Paragraph>
            <b>复现状态：</b>
            {report.data.reproductionStatus}
          </Typography.Paragraph>
          <Typography.Paragraph>
            <b>根因：</b>
            {report.data.rootCause}
          </Typography.Paragraph>
          <Typography.Paragraph>
            <b>修复建议：</b>
            {report.data.suggestedFix}
          </Typography.Paragraph>
          <Typography.Paragraph>
            <b>复现步骤：</b>
            {report.data.reproductionSteps.length ? (
              <ol>
                {report.data.reproductionSteps.map((step) => (
                  <li key={step}>{step}</li>
                ))}
              </ol>
            ) : (
              '无'
            )}
          </Typography.Paragraph>
          <Typography.Paragraph>
            <b>观察结果：</b>
            {report.data.observations.length ? (
              <ul>
                {report.data.observations.map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            ) : (
              '无'
            )}
          </Typography.Paragraph>
          <Typography.Text code>{report.data.markdown}</Typography.Text>
        </Card>
      )}
    </PageContainer>
  )
}
