import { PlusOutlined } from '@ant-design/icons'
import { PageContainer, ProTable, type ProColumns } from '@ant-design/pro-components'
import { useSuspenseQuery } from '@tanstack/react-query'
import { Button, Popconfirm, Space, Tag } from 'antd'

import { runAction } from '@/shared/utils'

import { ModelConfigFormModal } from '../components/ModelConfigFormModal'
import { modelConfigQueries, useDeleteModelConfig } from '../queries'
import type { ModelConfig } from '../types'

export function ModelConfigListPage() {
  // The route loader has already fetched the list, so this never suspends on first render.
  const { data, isFetching, refetch } = useSuspenseQuery(modelConfigQueries.list())
  const remove = useDeleteModelConfig()

  const columns: ProColumns<ModelConfig>[] = [
    {
      title: '名称',
      dataIndex: 'name',
      render: (_, record) => (
        <>
          {record.name}
          {record.isDefault && (
            <Tag color="blue" style={{ marginInlineStart: 8 }}>
              默认
            </Tag>
          )}
        </>
      ),
    },
    {
      title: '模型',
      key: 'models',
      render: (_, record) => (
        <Space wrap>
          {Object.keys(record.models).map((modelName) => (
            <Tag key={modelName}>{modelName}</Tag>
          ))}
        </Space>
      ),
    },
    { title: 'Base URL', dataIndex: 'baseUrl', ellipsis: true, copyable: true },
    { title: 'API Key', dataIndex: 'apiKeyMasked' },
    { title: '创建时间', dataIndex: 'createdAt', valueType: 'dateTime', width: 180 },
    {
      title: '操作',
      valueType: 'option',
      width: 140,
      render: (_, record) => [
        <ModelConfigFormModal
          key="edit"
          record={record}
          trigger={
            <Button type="link" size="small">
              编辑
            </Button>
          }
        />,
        <Popconfirm
          key="delete"
          title="删除模型配置"
          description={`确定删除「${record.name}」吗？`}
          okButtonProps={{ danger: true }}
          onConfirm={() => runAction(() => remove.mutateAsync(record.id))}
        >
          <Button type="link" size="small" danger>
            删除
          </Button>
        </Popconfirm>,
      ],
    },
  ]

  return (
    <PageContainer content="为评审 Agent 配置 OpenAI 兼容的模型，可将其中一个设为默认。">
      <ProTable<ModelConfig>
        rowKey="id"
        headerTitle="模型列表"
        columns={columns}
        dataSource={data}
        loading={isFetching}
        search={false}
        pagination={false}
        options={{ reload: () => void refetch(), density: false, setting: false }}
        toolBarRender={() => [
          <ModelConfigFormModal
            key="create"
            trigger={
              <Button type="primary" icon={<PlusOutlined />}>
                新建配置
              </Button>
            }
          />,
        ]}
      />
    </PageContainer>
  )
}
