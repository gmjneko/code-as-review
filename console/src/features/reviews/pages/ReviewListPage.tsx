import { PlusOutlined } from '@ant-design/icons'
import { PageContainer, ProTable, type ProColumns } from '@ant-design/pro-components'
import { useQuery } from '@tanstack/react-query'
import { getRouteApi } from '@tanstack/react-router'
import { Button, Popconfirm, Select, Typography } from 'antd'

import { repositoryQueries } from '@/features/repositories'
import { formatDuration, formatInteger, runAction } from '@/shared/utils'

import { CreateReviewModal } from '../components/CreateReviewModal'
import {
  isTerminalStatus,
  REVIEW_EFFORT_LABELS,
  REVIEW_STATUS_ENUM,
  REVIEW_TARGET_LABELS,
  REVIEW_TRIGGER_LABELS,
} from '../constants'
import { reviewQueries, useCancelReview } from '../queries'
import type { ReviewTask } from '../types'

const routeApi = getRouteApi('/_authenticated/reviews/')

function describeTarget(task: ReviewTask): string {
  const label = REVIEW_TARGET_LABELS[task.targetType]
  return task.baseRef && task.headRef ? `${label} ${task.baseRef}...${task.headRef}` : label
}

export function ReviewListPage() {
  const search = routeApi.useSearch()
  const navigate = routeApi.useNavigate()
  const { data, isFetching, refetch } = useQuery({
    ...reviewQueries.list(search),
    // A failed first load goes to the route error boundary; later failures are toasted.
    throwOnError: (_, query) => query.state.data === undefined,
  })
  // Only used to label rows and the filter; the table renders fine before it arrives.
  const repositories = useQuery(repositoryQueries.list())
  const repositoryNames = new Map(repositories.data?.map((repo) => [repo.id, repo.name]))
  const cancel = useCancelReview()

  const columns: ProColumns<ReviewTask>[] = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    {
      title: '仓库',
      dataIndex: 'repositoryId',
      width: 160,
      ellipsis: true,
      render: (_, task) => repositoryNames.get(task.repositoryId) ?? `#${task.repositoryId}`,
    },
    {
      title: '评审对象',
      dataIndex: 'targetType',
      width: 280,
      ellipsis: true,
      render: (_, task) => describeTarget(task),
    },
    {
      title: '触发方式',
      dataIndex: 'triggerType',
      width: 120,
      render: (_, task) => REVIEW_TRIGGER_LABELS[task.triggerType],
    },
    {
      title: '强度',
      dataIndex: 'effort',
      width: 80,
      render: (_, task) => REVIEW_EFFORT_LABELS[task.effort],
    },
    { title: '状态', dataIndex: 'status', width: 110, valueEnum: REVIEW_STATUS_ENUM },
    {
      title: '评论数',
      dataIndex: 'commentCount',
      width: 90,
      align: 'right',
      render: (_, task) => formatInteger(task.commentCount),
    },
    {
      title: 'Token（输入 / 输出）',
      key: 'tokens',
      width: 180,
      align: 'right',
      render: (_, task) =>
        `${formatInteger(task.inputTokens)} / ${formatInteger(task.outputTokens)}`,
    },
    { title: '创建时间', dataIndex: 'createdAt', valueType: 'dateTime', width: 180 },
    {
      title: '耗时',
      key: 'duration',
      width: 100,
      render: (_, task) => formatDuration(task.startedAt, task.finishedAt),
    },
    {
      title: '操作',
      valueType: 'option',
      width: 80,
      render: (_, task) =>
        isTerminalStatus(task.status)
          ? []
          : [
              <Popconfirm
                key="cancel"
                title="取消评审"
                description="确定取消该评审任务吗？"
                onConfirm={() => runAction(() => cancel.mutateAsync(task.id))}
              >
                <Button type="link" size="small">
                  取消
                </Button>
              </Popconfirm>,
            ],
    },
  ]

  return (
    <PageContainer>
      <ProTable<ReviewTask>
        rowKey="id"
        columns={columns}
        scroll={{ x: 'max-content' }}
        dataSource={data?.records}
        loading={isFetching}
        search={false}
        options={{ reload: () => void refetch(), density: false, setting: false }}
        headerTitle={
          <Select<number>
            aria-label="按仓库筛选"
            placeholder="全部仓库"
            allowClear
            showSearch
            optionFilterProp="label"
            style={{ width: 220 }}
            loading={repositories.isPending}
            value={search.repositoryId}
            options={repositories.data?.map((repo) => ({ value: repo.id, label: repo.name }))}
            onChange={(repositoryId) =>
              void navigate({ search: (prev) => ({ ...prev, page: 1, repositoryId }) })
            }
          />
        }
        toolBarRender={() => [
          <CreateReviewModal
            key="create"
            defaultRepositoryId={search.repositoryId}
            trigger={
              <Button type="primary" icon={<PlusOutlined />}>
                新建评审
              </Button>
            }
          />,
        ]}
        expandable={{
          rowExpandable: (task) => Boolean(task.summary || task.errorMessage),
          expandedRowRender: (task) => (
            <Typography.Paragraph
              type={task.errorMessage ? 'danger' : undefined}
              style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}
            >
              {task.errorMessage ?? task.summary}
            </Typography.Paragraph>
          ),
        }}
        pagination={{
          current: search.page,
          pageSize: search.size,
          total: data?.total ?? 0,
          showSizeChanger: true,
          onChange: (page, size) =>
            void navigate({
              // Changing the page size invalidates the current page number.
              search: (prev) => ({ ...prev, page: size === prev.size ? page : 1, size }),
            }),
        }}
      />
    </PageContainer>
  )
}
