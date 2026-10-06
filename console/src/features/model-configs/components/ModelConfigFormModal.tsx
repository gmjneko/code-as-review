import { ModalForm, ProFormList, ProFormSwitch, ProFormText } from '@ant-design/pro-components'
import { Col, Row, Typography, theme } from 'antd'
import type { ReactElement } from 'react'

import { runAction } from '@/shared/utils'

import { useCreateModelConfig, useUpdateModelConfig } from '../queries'
import type { CreateModelConfigRequest, ModelConfig, ModelCapabilities } from '../types'
import { ModelEntryFields } from './ModelEntryFields'

interface ModelConfigFormModalProps {
  trigger: ReactElement
  /** The config to edit; omit to create a new one. */
  record?: ModelConfig
}

interface ModelEntry {
  name: string
  context: number
  output: number
  inputImage: boolean
  reasoningEffort: string[]
}

interface FormValues {
  name: string
  baseUrl: string
  modelEntries: ModelEntry[]
  apiKey: string
  isDefault: boolean
}

function newModelEntry(): ModelEntry {
  return {
    name: '',
    context: 256000,
    output: 64000,
    inputImage: false,
    reasoningEffort: ['low', 'high', 'max'],
  }
}

function entriesFromRecord(models: Record<string, ModelCapabilities>): ModelEntry[] {
  return Object.entries(models).map(([name, model]) => ({
    name,
    context: model.limit.context,
    output: model.limit.output,
    inputImage: model.modalities.input.includes('image'),
    reasoningEffort: model.modalities.reasoning_effort,
  }))
}

function modelsFromEntries(entries: ModelEntry[]): CreateModelConfigRequest['models'] {
  return Object.fromEntries(
    entries.map((entry) => [
      entry.name.trim(),
      {
        limit: { context: entry.context, output: entry.output },
        modalities: {
          input: entry.inputImage ? ['text', 'image'] : ['text'],
          reasoning_effort: entry.reasoningEffort,
        },
      },
    ]),
  )
}

export function ModelConfigFormModal({ trigger, record }: ModelConfigFormModalProps) {
  const { token } = theme.useToken()
  const create = useCreateModelConfig()
  const update = useUpdateModelConfig()
  const isEdit = record !== undefined

  const handleFinish = ({ apiKey, modelEntries, ...values }: FormValues) => {
    const body = { ...values, models: modelsFromEntries(modelEntries) }
    return runAction(() =>
      record
        ? update.mutateAsync({ id: record.id, body: { ...body, ...(apiKey ? { apiKey } : {}) } })
        : create.mutateAsync({ ...body, apiKey }),
    )
  }

  return (
    <ModalForm<FormValues>
      title={isEdit ? '编辑模型配置' : '新建模型配置'}
      trigger={trigger}
      width={800}
      modalProps={{ destroyOnHidden: true }}
      initialValues={
        record
          ? {
              name: record.name,
              baseUrl: record.baseUrl,
              modelEntries: entriesFromRecord(record.models),
              isDefault: record.isDefault,
            }
          : {
              isDefault: false,
              modelEntries: [newModelEntry()],
            }
      }
      onFinish={handleFinish}
    >
      {/* Constraints mirror llm.dto.ModelConfigDtos. */}
      <ProFormText
        name="name"
        label="名称"
        formItemProps={{ style: { marginBottom: token.margin } }}
        rules={[
          { required: true, message: '请输入名称' },
          { max: 64, message: '最多 64 个字符' },
        ]}
      />
      <ProFormText
        name="baseUrl"
        label="Base URL"
        formItemProps={{ style: { marginBottom: token.margin } }}
        tooltip="OpenAI 兼容接口的地址"
        placeholder="https://api.openai.com/v1"
        rules={[
          { required: true, message: '请输入 Base URL' },
          { pattern: /^https?:\/\/.+/, message: '必须以 http:// 或 https:// 开头' },
          { max: 512, message: '最多 512 个字符' },
        ]}
      />
      <ProFormText.Password
        name="apiKey"
        label="API Key"
        formItemProps={{ style: { marginBottom: token.margin } }}
        placeholder={isEdit ? `当前：${record.apiKeyMasked}，留空表示不修改` : undefined}
        fieldProps={{ autoComplete: 'new-password' }}
        rules={[
          ...(isEdit ? [] : [{ required: true, message: '请输入 API Key' }]),
          { max: 512, message: '最多 512 个字符' },
        ]}
      />
      <Row gutter={token.margin} style={{ marginBottom: token.marginXS }}>
        <Col xs={12} sm={14}>
          <Typography.Text strong>模型列表</Typography.Text>
        </Col>
        <Col xs={12} sm={10}>
          <Typography.Text strong>能力</Typography.Text>
        </Col>
      </Row>
      <ProFormList
        name="modelEntries"
        min={1}
        max={32}
        creatorRecord={newModelEntry}
        creatorButtonProps={{ creatorButtonText: '添加模型' }}
        copyIconProps={false}
        itemRender={(_, { field, operation, fields }) => (
          <ModelEntryFields
            canRemove={fields.length > 1}
            onRemove={() => operation.remove(field.name)}
          />
        )}
        rules={[
          {
            validator: async (_, value: ModelEntry[] | undefined) => {
              if (!value?.length) {
                return Promise.reject(new Error('至少配置一个模型'))
              }
              const names = value.map((entry) => entry.name?.trim()).filter(Boolean)
              if (new Set(names).size !== names.length) {
                return Promise.reject(new Error('模型名称不能重复'))
              }
              return undefined
            },
          },
        ]}
      />
      <ProFormSwitch name="isDefault" label="设为默认模型" />
    </ModalForm>
  )
}
