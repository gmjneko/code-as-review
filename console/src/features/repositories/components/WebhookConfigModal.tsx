import { useState } from 'react'

import { InfoCircleOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Button, Checkbox, Divider, Input, List, Modal, Select, Tooltip, Typography } from 'antd'

import {
  modelConfigQueries,
  modelSelectionValue,
  parseModelSelection,
} from '@/features/model-configs'
import { runAction } from '@/shared/utils'

import { repositoryQueries, useReplaceTriggerRules, useRotateWebhook } from '../queries'
import type { Repository, WebhookRule } from '../types'

const EVENT_KIND_LABELS: Record<WebhookRule['eventKind'], string> = {
  PULL_REQUEST: 'Pull Request',
  ISSUE: 'Issue',
  PR_COMMENT: 'Pull Request 评论',
  ISSUE_COMMENT: 'Issue 评论',
}

const MODE_LABELS: Record<WebhookRule['mode'], string> = {
  AUTO: '自动处理',
  COMMAND: '命令触发',
}

const EFFORT_OPTIONS = [
  { value: 'LOW', label: '低' },
  { value: 'MEDIUM', label: '中' },
  { value: 'HIGH', label: '高' },
]

function humanRuleName(rule: WebhookRule): string {
  const event = EVENT_KIND_LABELS[rule.eventKind]
  if (rule.eventKind === 'PULL_REQUEST' && rule.action === 'opened') {
    return 'Pull Request 创建时自动评审'
  }
  if (rule.eventKind === 'PULL_REQUEST' && rule.action === 'synchronize') {
    return 'Pull Request 更新代码时自动评审'
  }
  if (rule.eventKind === 'ISSUE' && rule.action === 'opened') {
    return 'Issue 创建时记录待处理任务'
  }
  if (rule.mode === 'COMMAND') {
    return `${event}中使用 ${rule.command ?? '/review'} 命令`
  }
  return `${event} ${MODE_LABELS[rule.mode]}`
}

function technicalRuleName(rule: WebhookRule): string {
  return `${rule.eventKind} / ${rule.action} / ${rule.mode}${rule.command ? ` ${rule.command}` : ''}`
}

export function WebhookConfigModal({ repository }: { repository: Repository }) {
  const [open, setOpen] = useState(false)
  const [secret, setSecret] = useState<string | null>(null)
  const [draftRules, setDraftRules] = useState<WebhookRule[] | null>(null)
  const query = useQuery({ ...repositoryQueries.webhook(repository.id), enabled: open })
  const rules = draftRules ?? query.data?.rules ?? []
  const modelConfigs = useQuery({ ...modelConfigQueries.list(), enabled: open })
  const defaultModel = modelConfigs.data?.find((model) => model.isDefault)
  const modelOptions = modelConfigs.data?.map((config) => ({
    label: config.name,
    options: Object.keys(config.models).map((modelName) => ({
      value: modelSelectionValue(config.id, modelName),
      label: modelName,
    })),
  }))
  const modelValueForRule = (rule: WebhookRule) => {
    if (rule.modelConfigId != null) {
      return rule.modelName ? modelSelectionValue(rule.modelConfigId, rule.modelName) : undefined
    }
    const defaultModelName = defaultModel ? Object.keys(defaultModel.models)[0] : undefined
    return defaultModel && defaultModelName
      ? modelSelectionValue(defaultModel.id, defaultModelName)
      : undefined
  }
  const rotate = useRotateWebhook()
  const replace = useReplaceTriggerRules()

  const updateRule = (index: number, patch: Partial<WebhookRule>) =>
    setDraftRules(
      rules.map((rule, ruleIndex) => (ruleIndex === index ? { ...rule, ...patch } : rule)),
    )

  return (
    <>
      <Button
        type="link"
        size="small"
        onClick={() => {
          setDraftRules(null)
          setOpen(true)
        }}
      >
        Webhook
      </Button>
      <Modal
        title={`GitHub Webhook：${repository.name}`}
        width={760}
        open={open}
        onCancel={() => setOpen(false)}
        footer={[
          <Button
            key="save"
            type="primary"
            loading={replace.isPending}
            disabled={!query.data}
            onClick={() =>
              runAction(async () => {
                await replace.mutateAsync({ id: repository.id, rules })
                setDraftRules(null)
              })
            }
          >
            保存规则
          </Button>,
          <Button key="close" onClick={() => setOpen(false)}>
            关闭
          </Button>,
        ]}
      >
        <Typography.Text strong style={{ display: 'block' }}>
          Webhook URL
        </Typography.Text>
        <Input readOnly value={query.data?.endpoint ?? ''} style={{ marginTop: 8 }} />
        {(secret ?? query.data?.secret) ? (
          <div style={{ marginTop: 12 }}>
            <Typography.Text strong style={{ display: 'block', marginBottom: 8 }}>
              新 Secret（只显示本次）
            </Typography.Text>
            <Input.Password readOnly value={secret ?? query.data?.secret ?? ''} />
          </div>
        ) : null}
        <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 8 }}>
          <Button
            loading={rotate.isPending}
            onClick={() =>
              runAction(async () => {
                const result = await rotate.mutateAsync(repository.id)
                setSecret(result.secret)
              })
            }
          >
            生成/轮换 Secret
          </Button>
        </div>
        <Divider />
        <Typography.Text strong>触发规则</Typography.Text>
        <div
          style={{
            color: '#8c8c8c',
            display: 'grid',
            gap: 12,
            gridTemplateColumns: '28px minmax(0, 1fr) 124px 220px',
            marginTop: 12,
            paddingBottom: 8,
          }}
        >
          <span />
          <Typography.Text type="secondary">触发条件</Typography.Text>
          <Typography.Text type="secondary">评审强度</Typography.Text>
          <Typography.Text type="secondary">模型配置</Typography.Text>
        </div>
        <List<WebhookRule>
          size="small"
          dataSource={rules}
          renderItem={(rule, index) => (
            <List.Item style={{ paddingBlock: 14 }}>
              <div
                style={{
                  alignItems: 'center',
                  display: 'grid',
                  gap: 12,
                  gridTemplateColumns: '28px minmax(0, 1fr) 124px 220px',
                  minWidth: 0,
                  width: '100%',
                }}
              >
                <Checkbox
                  checked={rule.enabled}
                  onChange={(event) => updateRule(index, { enabled: event.target.checked })}
                />
                <div
                  style={{
                    alignItems: 'center',
                    display: 'flex',
                    flex: 1,
                    gap: 8,
                    minWidth: 0,
                  }}
                >
                  <Typography.Text ellipsis style={{ display: 'block', minWidth: 0 }}>
                    {humanRuleName(rule)}
                  </Typography.Text>
                  <Tooltip title={`内部规则：${technicalRuleName(rule)}`}>
                    <InfoCircleOutlined
                      aria-label="查看内部规则名称"
                      style={{ color: '#8c8c8c' }}
                    />
                  </Tooltip>
                </div>
                <Select
                  size="middle"
                  aria-label="评审强度"
                  style={{ width: 124 }}
                  value={rule.effort}
                  options={EFFORT_OPTIONS}
                  onChange={(effort) => updateRule(index, { effort })}
                />
                <Select
                  size="middle"
                  aria-label="模型配置"
                  allowClear
                  loading={modelConfigs.isPending}
                  optionFilterProp="label"
                  options={modelOptions}
                  showSearch
                  style={{ width: 220 }}
                  value={modelValueForRule(rule)}
                  onChange={(value) => {
                    const selected = typeof value === 'string' ? parseModelSelection(value) : null
                    updateRule(index, {
                      modelConfigId: selected?.configId ?? null,
                      modelName: selected?.modelName ?? null,
                    })
                  }}
                />
              </div>
            </List.Item>
          )}
        />
      </Modal>
    </>
  )
}
