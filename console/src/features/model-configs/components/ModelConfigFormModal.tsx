import { ModalForm, ProFormSwitch, ProFormText } from '@ant-design/pro-components'
import type { ReactElement } from 'react'

import { runAction } from '@/shared/utils'

import { useCreateModelConfig, useUpdateModelConfig } from '../queries'
import type { CreateModelConfigRequest, ModelConfig } from '../types'

interface ModelConfigFormModalProps {
  trigger: ReactElement
  /** The config to edit; omit to create a new one. */
  record?: ModelConfig
}

export function ModelConfigFormModal({ trigger, record }: ModelConfigFormModalProps) {
  const create = useCreateModelConfig()
  const update = useUpdateModelConfig()
  const isEdit = record !== undefined

  const handleFinish = ({ apiKey, ...values }: CreateModelConfigRequest) =>
    runAction(() =>
      record
        ? // A blank API key on edit means "keep the stored one".
          update.mutateAsync({ id: record.id, body: { ...values, ...(apiKey ? { apiKey } : {}) } })
        : create.mutateAsync({ ...values, apiKey }),
    )

  return (
    <ModalForm<CreateModelConfigRequest>
      title={isEdit ? '编辑模型配置' : '新建模型配置'}
      trigger={trigger}
      width={520}
      modalProps={{ destroyOnHidden: true }}
      initialValues={
        record
          ? {
              name: record.name,
              baseUrl: record.baseUrl,
              modelName: record.modelName,
              isDefault: record.isDefault,
            }
          : { isDefault: false }
      }
      onFinish={handleFinish}
    >
      {/* Constraints mirror llm.dto.ModelConfigDtos. */}
      <ProFormText
        name="name"
        label="名称"
        rules={[
          { required: true, message: '请输入名称' },
          { max: 64, message: '最多 64 个字符' },
        ]}
      />
      <ProFormText
        name="baseUrl"
        label="Base URL"
        tooltip="OpenAI 兼容接口的地址"
        placeholder="https://api.openai.com/v1"
        rules={[
          { required: true, message: '请输入 Base URL' },
          { pattern: /^https?:\/\/.+/, message: '必须以 http:// 或 https:// 开头' },
          { max: 512, message: '最多 512 个字符' },
        ]}
      />
      <ProFormText
        name="modelName"
        label="模型名称"
        placeholder="gpt-4o"
        rules={[
          { required: true, message: '请输入模型名称' },
          { max: 128, message: '最多 128 个字符' },
        ]}
      />
      <ProFormText.Password
        name="apiKey"
        label="API Key"
        placeholder={isEdit ? `当前：${record.apiKeyMasked}，留空表示不修改` : undefined}
        fieldProps={{ autoComplete: 'new-password' }}
        rules={[
          ...(isEdit ? [] : [{ required: true, message: '请输入 API Key' }]),
          { max: 512, message: '最多 512 个字符' },
        ]}
      />
      <ProFormSwitch name="isDefault" label="设为默认模型" />
    </ModalForm>
  )
}
