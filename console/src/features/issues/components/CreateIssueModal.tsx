import { ModalForm, ProFormSelect, ProFormText, ProFormTextArea } from '@ant-design/pro-components'
import { useQuery } from '@tanstack/react-query'
import type { ReactElement } from 'react'

import {
  modelConfigQueries,
  modelSelectionValue,
  parseModelSelection,
} from '@/features/model-configs'
import { repositoryQueries } from '@/features/repositories'
import { runAction } from '@/shared/utils'

import { ISSUE_EFFORT_LABELS } from '../constants'
import { useCreateIssue } from '../queries'
import type { CreateIssueRequest, IssueEffort } from '../types'

type Values = CreateIssueRequest & { modelSelection?: string }

export function CreateIssueModal({
  trigger,
  defaultRepositoryId,
}: {
  trigger: ReactElement
  defaultRepositoryId?: number
}) {
  const create = useCreateIssue()
  const repositories = useQuery(repositoryQueries.list())
  const models = useQuery(modelConfigQueries.list())
  return (
    <ModalForm<Values>
      title="新建 Issue 调查"
      trigger={trigger}
      width={560}
      modalProps={{ destroyOnHidden: true }}
      initialValues={{ repositoryId: defaultRepositoryId }}
      onFinish={(values) => {
        const selected = values.modelSelection
          ? parseModelSelection(values.modelSelection)
          : undefined
        const body: CreateIssueRequest = {
          repositoryId: values.repositoryId,
          issueNumber: values.issueNumber.trim(),
          effort: values.effort,
          background: values.background?.trim() || undefined,
          ...(selected ? { modelConfigId: selected.configId, modelName: selected.modelName } : {}),
        }
        return runAction(() => create.mutateAsync(body))
      }}
    >
      <ProFormSelect<number>
        name="repositoryId"
        label="仓库"
        options={repositories.data?.map((r) => ({ value: r.id, label: r.name }))}
        rules={[{ required: true, message: '请选择仓库' }]}
      />
      <ProFormText
        name="issueNumber"
        label="Issue 编号"
        rules={[{ required: true }, { pattern: /^[1-9][0-9]{0,18}$/, message: '必须是正整数' }]}
      />
      <ProFormSelect<IssueEffort>
        name="effort"
        label="调查强度"
        allowClear
        options={Object.entries(ISSUE_EFFORT_LABELS).map(([value, label]) => ({ value, label }))}
      />
      <ProFormSelect<string>
        name="modelSelection"
        label="模型"
        allowClear
        options={models.data?.map((c) => ({
          label: c.name,
          options: Object.keys(c.models).map((name) => ({
            value: modelSelectionValue(c.id, name),
            label: name,
          })),
        }))}
      />
      <ProFormTextArea
        name="background"
        label="背景说明"
        fieldProps={{ maxLength: 8000, showCount: true }}
      />
    </ModalForm>
  )
}
