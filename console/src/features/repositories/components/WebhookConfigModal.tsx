import { useEffect, useState } from 'react'

import {
  Modal,
  Button,
  Checkbox,
  Divider,
  Input,
  InputNumber,
  List,
  Select,
  Space,
  Typography,
} from 'antd'
import { useQuery } from '@tanstack/react-query'

import { runAction } from '@/shared/utils'

import { repositoryQueries, useReplaceTriggerRules, useRotateWebhook } from '../queries'
import type { Repository, WebhookRule } from '../types'

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
            <List.Item>
              <Space>
                <Checkbox
                  checked={rule.enabled}
                  onChange={(event) => toggle(index, event.target.checked)}
                />
                <Typography.Text>
                  {rule.eventKind} / {rule.action} / {rule.mode}
                  {rule.command ? ` ${rule.command}` : ''}
                </Typography.Text>
                <Select
                  size="small"
                  value={rule.effort}
                  options={['LOW', 'MEDIUM', 'HIGH'].map((effort) => ({
                    value: effort,
                    label: effort,
                  }))}
                  onChange={(effort) => updateRule(index, { effort })}
                />
                <InputNumber
                  size="small"
                  min={1}
                  placeholder="模型 ID"
                  value={rule.modelConfigId ?? undefined}
                  onChange={(modelConfigId) => updateRule(index, { modelConfigId })}
                />
              </Space>
            </List.Item>
          )}
        />
      </Modal>
    </>
  )
}
