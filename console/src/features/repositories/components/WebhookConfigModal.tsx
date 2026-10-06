import { useEffect, useState } from 'react'

import { InfoCircleOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import {
  Button,
  Checkbox,
  Divider,
  Input,
  InputNumber,
  List,
  Modal,
  Select,
  Tooltip,
  Typography,
} from 'antd'

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
  const [rules, setRules] = useState<WebhookRule[]>([])
  const query = useQuery({ ...repositoryQueries.webhook(repository.id), enabled: open })
  const rotate = useRotateWebhook()
  const replace = useReplaceTriggerRules()

  useEffect(() => {
    if (query.data) setRules(query.data.rules)
  }, [query.data])

  const toggle = (index: number, enabled: boolean) =>
    setRules((current) => current.map((rule, i) => (i === index ? { ...rule, enabled } : rule)))

  const updateRule = (index: number, patch: Partial<WebhookRule>) =>
    setRules((current) => current.map((rule, i) => (i === index ? { ...rule, ...patch } : rule)))

  return (
    <>
      <Button type="link" size="small" onClick={() => setOpen(true)}>
        Webhook
      </Button>
      <Modal
        title={`GitHub Webhook：${repository.name}`}
        width="min(960px, calc(100vw - 32px))"
        open={open}
        onCancel={() => setOpen(false)}
        footer={[
          <Button
            key="save"
            type="primary"
            onClick={() => runAction(() => replace.mutateAsync({ id: repository.id, rules }))}
          >
            保存规则
          </Button>,
          <Button key="close" onClick={() => setOpen(false)}>
            关闭
          </Button>,
        ]}
      >
        {(secret ?? query.data?.secret) ? (
          <>
            <Typography.Text strong>新 Secret（只显示本次）</Typography.Text>
            <Input.Password readOnly value={secret ?? query.data?.secret ?? ''} />
          </>
        ) : null}
        <Typography.Text strong>Webhook URL</Typography.Text>
        <Input readOnly value={query.data?.endpoint ?? ''} />
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
        <Divider />
        <Typography.Text strong>触发规则</Typography.Text>
        <List<WebhookRule>
          size="small"
          dataSource={rules}
          renderItem={(rule, index) => (
            <List.Item style={{ paddingBlock: 14 }}>
              <div
                style={{
                  alignItems: 'center',
                  display: 'flex',
                  gap: 12,
                  minWidth: 0,
                  width: '100%',
                }}
              >
                <Checkbox
                  checked={rule.enabled}
                  onChange={(event) => toggle(index, event.target.checked)}
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
                  size="small"
                  style={{ flex: '0 0 124px', width: 124 }}
                  value={rule.effort}
                  options={EFFORT_OPTIONS}
                  onChange={(effort) => updateRule(index, { effort })}
                />
                <InputNumber
                  size="small"
                  min={1}
                  style={{ flex: '0 0 150px', width: 150 }}
                  placeholder="模型 ID"
                  value={rule.modelConfigId ?? undefined}
                  onChange={(modelConfigId) => updateRule(index, { modelConfigId })}
                />
              </div>
            </List.Item>
          )}
        />
      </Modal>
    </>
  )
}
