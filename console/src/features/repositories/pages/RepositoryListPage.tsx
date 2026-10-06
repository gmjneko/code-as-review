import { PlusOutlined } from '@ant-design/icons'
import { PageContainer, ProTable, type ProColumns } from '@ant-design/pro-components'
import { useSuspenseQuery } from '@tanstack/react-query'
import { Link } from '@tanstack/react-router'
import { Button, Popconfirm, Tag, Typography } from 'antd'

import { runAction } from '@/shared/utils'

import { RepositoryFormModal } from '../components/RepositoryFormModal'
import { WebhookConfigModal } from '../components/WebhookConfigModal'
import { SOURCE_TYPE_META } from '../constants'
import { repositoryQueries, useDeleteRepository } from '../queries'
import type { Repository } from '../types'

export function RepositoryListPage() {
  // The route loader has already fetched the list, so this never suspends on first render.
  const { data, isFetching, refetch } = useSuspenseQuery(repositoryQueries.list())
  const remove = useDeleteRepository()

  const columns: ProColumns<Repository>[] = [
    { title: '名称', dataIndex: 'name' },
    {
      title: '来源',
      dataIndex: 'sourceType',
      width: 110,
      render: (_, repo) => (
        <Tag color={SOURCE_TYPE_META[repo.sourceType].color}>
          {SOURCE_TYPE_META[repo.sourceType].label}
        </Tag>
      ),
    },
    {
      title: '位置',
      key: 'location',
      ellipsis: true,
      render: (_, repo) => {
        const location = repo.localPath ?? repo.remoteUrl ?? repo.externalFullName
        return location ? (
          <Typography.Text copyable={{ text: location }} ellipsis={{ tooltip: location }}>
            {location}
          </Typography.Text>
        ) : (
          '-'
        )
      },
    },
    {
      title: '默认分支',
      dataIndex: 'defaultBranch',
      width: 140,
      render: (_, repo) => repo.defaultBranch ?? '-',
    },
    { title: '创建时间', dataIndex: 'createdAt', valueType: 'dateTime', width: 180 },
    {
      title: '操作',
      valueType: 'option',
      width: 200,
      render: (_, repo) => [
        <Link key="reviews" to="/reviews" search={{ repositoryId: repo.id }}>
          评审记录
        </Link>,
        ...(repo.sourceType === 'GITHUB'
          ? [<WebhookConfigModal key="webhook" repository={repo} />]
          : []),
        <RepositoryFormModal
          key="edit"
          record={repo}
          trigger={
            <Button type="link" size="small">
              编辑
            </Button>
          }
        />,
        <Popconfirm
          key="delete"
          title="删除仓库"
          description={`确定删除「${repo.name}」吗？`}
          okButtonProps={{ danger: true }}
          onConfirm={() => runAction(() => remove.mutateAsync(repo.id))}
        >
          <Button type="link" size="small" danger>
            删除
          </Button>
        </Popconfirm>,
      ],
    },
  ]

  return (
    <PageContainer content="登记需要评审的代码仓库，并为 GitHub 仓库配置 Webhook 和自动触发规则。">
      <ProTable<Repository>
        rowKey="id"
        headerTitle="仓库列表"
        columns={columns}
        dataSource={data}
        loading={isFetching}
        search={false}
        pagination={false}
        options={{ reload: () => void refetch(), density: false, setting: false }}
        toolBarRender={() => [
          <RepositoryFormModal
            key="create"
            trigger={
              <Button type="primary" icon={<PlusOutlined />}>
                添加仓库
              </Button>
            }
          />,
        ]}
      />
    </PageContainer>
  )
}
