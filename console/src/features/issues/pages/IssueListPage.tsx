import { PlusOutlined } from '@ant-design/icons'
import { PageContainer, ProTable, type ProColumns } from '@ant-design/pro-components'
import { useQuery } from '@tanstack/react-query'
import { getRouteApi, Link } from '@tanstack/react-router'
import { Button, Popconfirm, Select, Space, Tag, Typography } from 'antd'

import { repositoryQueries } from '@/features/repositories'
import { runAction } from '@/shared/utils'

import { CreateIssueModal } from '../components/CreateIssueModal'
import { ISSUE_EFFORT_LABELS, ISSUE_STATUS_META, isTerminalStatus } from '../constants'
import { issueQueries, useCancelIssue } from '../queries'
import type { IssueTask } from '../types'

const routeApi = getRouteApi('/_authenticated/issues/')

export function IssueListPage() {
  const search = routeApi.useSearch()
  const navigate = routeApi.useNavigate()
  const { data, isFetching, refetch } = useQuery(issueQueries.list(search))
  const repositories = useQuery(repositoryQueries.list())
  const cancel = useCancelIssue()
  const names = new Map(repositories.data?.map((repo) => [repo.id, repo.name]))
  const columns: ProColumns<IssueTask>[] = [
    {
      title: 'ID',
      dataIndex: 'id',
      width: 80,
      render: (_, task) => (
        <Link to="/issues/$issueId" params={{ issueId: task.id }}>
          #{task.id}
        </Link>
      ),
    },
    {
      title: '仓库',
      dataIndex: 'repositoryId',
      width: 150,
      render: (_, task) => names.get(task.repositoryId) ?? `#${task.repositoryId}`,
    },
    {
      title: 'Issue',
      dataIndex: 'issueNumber',
      width: 90,
      render: (_, task) => `#${task.issueNumber}`,
    },
    { title: '触发方式', dataIndex: 'triggerType', width: 120 },
    {
      title: '执行模式',
      dataIndex: 'executionMode',
      width: 110,
      render: (_, task) => <Tag>{task.executionMode === 'SANDBOX' ? 'Docker 沙箱' : '本地'}</Tag>,
    },
    {
      title: '强度',
      dataIndex: 'effort',
      width: 80,
      render: (_, task) => ISSUE_EFFORT_LABELS[task.effort],
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (_, task) => (
        <Tag color={ISSUE_STATUS_META[task.status].status}>
          {ISSUE_STATUS_META[task.status].text}
        </Tag>
      ),
    },
    {
      title: '摘要',
      dataIndex: 'summary',
      ellipsis: true,
      render: (_, task) =>
        task.errorMessage ? (
          <Typography.Text type="danger">{task.errorMessage}</Typography.Text>
        ) : (
          (task.summary ?? '-')
        ),
    },
    { title: '创建时间', dataIndex: 'createdAt', valueType: 'dateTime', width: 180 },
    {
      title: '操作',
      valueType: 'option',
      width: 130,
      render: (_, task) => (
        <Space>
          <Link to="/issues/$issueId" params={{ issueId: task.id }}>
            详情
          </Link>
          {!isTerminalStatus(task.status) && (
            <Popconfirm
              title="取消调查？"
              onConfirm={() => runAction(() => cancel.mutateAsync(task.id))}
            >
              <Button type="link" size="small">
                取消
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
    {
      title: 'Token（输入 / 输出）',
      key: 'tokens',
      width: 160,
      render: (_, task) => `${task.inputTokens ?? 0} / ${task.outputTokens ?? 0}`,
    },
  ]
  return (
    <PageContainer>
      <ProTable<IssueTask>
        rowKey="id"
        columns={columns}
        dataSource={data?.records}
        loading={isFetching}
        search={false}
        options={{ reload: () => void refetch(), density: false, setting: false }}
        headerTitle={
          <Select<number>
            placeholder="全部仓库"
            allowClear
            value={search.repositoryId}
            options={repositories.data?.map((r) => ({ value: r.id, label: r.name }))}
            onChange={(repositoryId) =>
              void navigate({ search: (prev) => ({ ...prev, page: 1, repositoryId }) })
            }
          />
        }
        toolBarRender={() => [
          <CreateIssueModal
            key="create"
            defaultRepositoryId={search.repositoryId}
            trigger={
              <Button type="primary" icon={<PlusOutlined />}>
                新建调查
              </Button>
            }
          />,
        ]}
        pagination={{
          current: search.page,
          pageSize: search.size,
          total: data?.total ?? 0,
          onChange: (page, size) => void navigate({ search: (prev) => ({ ...prev, page, size }) }),
        }}
      />
    </PageContainer>
  )
}
