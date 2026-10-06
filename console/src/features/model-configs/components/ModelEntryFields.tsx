import { DeleteOutlined, DownOutlined, RightOutlined } from '@ant-design/icons'
import {
  ProFormCheckbox,
  ProFormDigit,
  ProFormSelect,
  ProFormText,
} from '@ant-design/pro-components'
import { Button, Checkbox, Col, Flex, Popconfirm, Row, theme, type InputNumberProps } from 'antd'
import { useId, useState } from 'react'

interface ModelEntryFieldsProps {
  canRemove: boolean
  onRemove: () => void
}

const REASONING_EFFORT_OPTIONS = [
  { value: 'low', label: 'Low' },
  { value: 'high', label: 'High' },
  { value: 'max', label: 'Max' },
]

const TOKEN_FIELD_PROPS = {
  precision: 0,
  style: { width: '100%' },
  formatter: (value, { userTyping, input }) => {
    if (userTyping) return input
    if (value === undefined) return ''
    const tokens = value
    return tokens % 1000 === 0 ? `${tokens / 1000}K` : String(tokens)
  },
  parser: (value) => {
    const input = value?.trim() ?? ''
    return /k$/i.test(input) ? Number(input.slice(0, -1)) * 1000 : Number(input)
  },
} satisfies InputNumberProps<number>

export function ModelEntryFields({ canRemove, onRemove }: ModelEntryFieldsProps) {
  const { token } = theme.useToken()
  const [expanded, setExpanded] = useState(false)
  const detailsId = useId()

  return (
    <div
      style={{
        padding: token.paddingSM,
        marginBottom: token.marginSM,
        border: `1px solid ${token.colorBorderSecondary}`,
        borderRadius: token.borderRadiusLG,
        background: token.colorFillAlter,
      }}
    >
      <Row gutter={[token.margin, token.marginXS]} align="middle">
        <Col xs={24} sm={14}>
          <ProFormText
            name="name"
            placeholder="例如：deepseek-v4.1-flash"
            fieldProps={{ 'aria-label': '模型名称' }}
            formItemProps={{ style: { marginBottom: 0 } }}
            rules={[
              { required: true, whitespace: true, message: '请输入模型名称' },
              { max: 128, message: '最多 128 个字符' },
            ]}
          />
        </Col>
        <Col xs={24} sm={10}>
          <Flex align="center" justify="space-between" gap={token.marginXS}>
            <Flex align="center" gap={token.marginSM}>
              <Checkbox checked disabled>
                文本
              </Checkbox>
              <ProFormCheckbox name="inputImage" formItemProps={{ style: { marginBottom: 0 } }}>
                图片
              </ProFormCheckbox>
            </Flex>
            <Flex align="center" gap={token.marginXS}>
              <Button
                type="text"
                icon={expanded ? <DownOutlined /> : <RightOutlined />}
                aria-label={expanded ? '收起模型设置' : '展开模型设置'}
                aria-expanded={expanded}
                aria-controls={detailsId}
                onClick={() => setExpanded(!expanded)}
              />
              <Popconfirm
                title="删除模型"
                description="确定删除这个模型吗？"
                okButtonProps={{ danger: true }}
                onConfirm={onRemove}
                disabled={!canRemove}
              >
                <Button
                  type="text"
                  danger
                  icon={<DeleteOutlined />}
                  aria-label="删除模型"
                  disabled={!canRemove}
                />
              </Popconfirm>
            </Flex>
          </Flex>
        </Col>
      </Row>
      <div id={detailsId} hidden={!expanded} style={{ marginTop: token.margin }}>
        <ProFormSelect
          name="reasoningEffort"
          label="思考强度设置"
          mode="multiple"
          options={REASONING_EFFORT_OPTIONS}
          rules={[{ required: true, message: '请选择支持的思考强度' }]}
        />
        <Row gutter={token.margin}>
          <Col xs={24} sm={12}>
            <ProFormDigit
              name="context"
              label="上下文窗口"
              min={1}
              fieldProps={TOKEN_FIELD_PROPS}
              formItemProps={{ style: { marginBottom: 0 } }}
              rules={[{ required: true, message: '请输入上下文窗口' }]}
            />
          </Col>
          <Col xs={24} sm={12}>
            <ProFormDigit
              name="output"
              label="最大输出 token"
              min={1}
              fieldProps={TOKEN_FIELD_PROPS}
              formItemProps={{ style: { marginBottom: 0 } }}
              rules={[{ required: true, message: '请输入最大输出 token' }]}
            />
          </Col>
        </Row>
      </div>
    </div>
  )
}
