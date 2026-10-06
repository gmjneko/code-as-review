import { PlusOutlined } from '@ant-design/icons'
import { PageContainer, ProTable, type ProColumns } from '@ant-design/pro-components'
import { useSuspenseQuery } from '@tanstack/react-query'
import { Button, Popconfirm, Tag, Typography } from 'antd'

import { runAction } from '@/shared/utils'

import { CredentialFormModal } from '../components/CredentialFormModal'
import { credentialQueries, useDeleteCredential } from '../queries'
import type { ScmCredential } from '../types'

export function CredentialListPage() {
  const { data, isFetching, refetch } = useSuspenseQuery(credentialQueries.list())
  const remove = useDeleteCredential()

  const columns: ProColumns<ScmCredential>[] = [
    { title: '名称', dataIndex: 'name', width: 180 },
    {
      title: '平台',
      dataIndex: 'provider',
      width: 110,
      render: (_, record) => (
        <Tag color="geekblue">{record.provider === 'GITHUB' ? 'GitHub' : record.provider}</Tag>
      ),
    },
    {
      title: '认证方式',
      dataIndex: 'authType',
      width: 180,
      render: (_, record) =>
        record.authType === 'PAT' ? 'Personal Access Token' : record.authType,
    },
    { title: 'Host', dataIndex: 'host', width: 160 },
    {
      title: 'Token',
      dataIndex: 'maskedToken',
      width: 150,
      render: (_, record) => <Typography.Text code>{record.maskedToken}</Typography.Text>,
    },
    {
      title: '备注',
      dataIndex: 'remark',
      ellipsis: true,
      render: (_, record) => record.remark || '-',
    },
    { title: '创建时间', dataIndex: 'createdAt', valueType: 'dateTime', width: 180 },
    {
      title: '操作',
      valueType: 'option',
      width: 140,
      render: (_, record) => [
        <CredentialFormModal
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
          title="删除凭据"
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
    <PageContainer content="集中管理 GitHub 访问凭据，Token 只会加密保存在服务端。">
      <ProTable<ScmCredential>
        rowKey="id"
        headerTitle="凭据列表"
        columns={columns}
        dataSource={data}
        loading={isFetching}
        search={false}
        pagination={false}
        options={{ reload: () => void refetch(), density: false, setting: false }}
        toolBarRender={() => [
          <CredentialFormModal
            key="create"
            trigger={
              <Button type="primary" icon={<PlusOutlined />}>
                添加凭据
              </Button>
            }
          />,
        ]}
      />
    </PageContainer>
  )
}
