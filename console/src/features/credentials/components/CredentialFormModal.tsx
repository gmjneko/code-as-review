import { ModalForm, ProFormSelect, ProFormText } from '@ant-design/pro-components'
import type { ReactElement } from 'react'

import { runAction } from '@/shared/utils'

import { useCreateCredential, useUpdateCredential } from '../queries'
import type { CreateCredentialRequest, ScmCredential, UpdateCredentialRequest } from '../types'

interface CredentialFormModalProps {
  trigger: ReactElement
  record?: ScmCredential
}

interface FormValues {
  name: string
  provider: string
  authType: string
  host: string
  token?: string
  remark?: string
}

function trimmed(value: string | undefined): string | undefined {
  const text = value?.trim()
  return text ? text : undefined
}

export function CredentialFormModal({ trigger, record }: CredentialFormModalProps) {
  const create = useCreateCredential()
  const update = useUpdateCredential()
  const isEdit = record !== undefined

  const handleFinish = (values: FormValues) => {
    const common = {
      name: values.name.trim(),
      host: trimmed(values.host),
      remark: values.remark?.trim() ?? '',
    }
    if (record) {
      const request: UpdateCredentialRequest = { ...common, token: trimmed(values.token) }
      return runAction(() => update.mutateAsync({ id: record.id, body: request }))
    }

    const request: CreateCredentialRequest = { ...common, token: values.token?.trim() ?? '' }
    return runAction(() => create.mutateAsync(request))
  }

  return (
    <ModalForm<FormValues>
      title={isEdit ? '编辑凭据' : '添加凭据'}
      trigger={trigger}
      width={560}
      modalProps={{ destroyOnHidden: true }}
      initialValues={{
        provider: record?.provider ?? 'GITHUB',
        authType: record?.authType ?? 'PAT',
        name: record?.name,
        host: record?.host ?? 'github.com',
        remark: record?.remark ?? undefined,
      }}
      onFinish={handleFinish}
    >
      <ProFormText
        name="name"
        label="凭据名称"
        placeholder="例如：公司 GitHub"
        rules={[
          { required: true, whitespace: true, message: '请输入凭据名称' },
          { max: 64, message: '最多 64 个字符' },
        ]}
      />
      <ProFormSelect
        name="provider"
        label="平台"
        options={[{ value: 'GITHUB', label: 'GitHub' }]}
        disabled
      />
      <ProFormSelect
        name="authType"
        label="认证方式"
        options={[{ value: 'PAT', label: 'Personal Access Token' }]}
        disabled
      />
      <ProFormText
        name="host"
        label="Host"
        placeholder="github.com"
        rules={[
          { required: true, whitespace: true, message: '请输入 Host' },
          { max: 255, message: '最多 255 个字符' },
        ]}
      />
      <ProFormText.Password
        name="token"
        label={isEdit ? 'Personal Access Token（留空则不更换）' : 'Personal Access Token'}
        rules={isEdit ? undefined : [{ required: true, message: '请输入 PAT' }]}
        placeholder={isEdit ? '留空以保留当前 Token' : undefined}
      />
      <ProFormText
        name="remark"
        label="备注"
        placeholder="可选，例如：后端团队仓库"
        fieldProps={{ maxLength: 500, showCount: true }}
      />
    </ModalForm>
  )
}
