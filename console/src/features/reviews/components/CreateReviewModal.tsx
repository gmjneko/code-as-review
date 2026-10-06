import {
  ModalForm,
  ProFormDependency,
  ProFormRadio,
  ProFormSelect,
  ProFormText,
  ProFormTextArea,
} from '@ant-design/pro-components'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Form, Typography } from 'antd'
import type { ReactElement } from 'react'

import {
  modelConfigQueries,
  modelSelectionValue,
  parseModelSelection,
} from '@/features/model-configs'
import { repositoryQueries } from '@/features/repositories'
import { runAction } from '@/shared/utils'

import {
  REVIEW_EFFORT_LABELS,
  REVIEW_EFFORT_ROUNDS,
  REVIEW_EFFORTS,
  REVIEW_TARGET_LABELS,
  SAFE_GIT_REF,
  TARGETS_BY_SOURCE,
} from '../constants'
import { useCreateReview } from '../queries'
import type { CreateReviewRequest, ReviewEffort, ReviewTargetType } from '../types'

interface CreateReviewModalProps {
  trigger: ReactElement
  /** Preselected repository, e.g. the one the list is filtered by. */
  defaultRepositoryId?: number
}

interface FormValues {
  repositoryId: number
  targetType: ReviewTargetType
  baseRef?: string
  headRef?: string
  externalRef?: string
  effort?: ReviewEffort
  modelSelection?: string
  background?: string
}

const EFFORT_OPTIONS = REVIEW_EFFORTS.map((value) => ({
  value,
  label: `${REVIEW_EFFORT_LABELS[value]}（${REVIEW_EFFORT_ROUNDS[value]} 轮）`,
}))

/** Validates the trimmed ref, which is what gets submitted. */
function validateGitRef(_: unknown, value: string | undefined): Promise<void> {
  const ref = value?.trim()
  if (!ref) return Promise.reject(new Error('请输入分支、标签或提交'))
  if (!SAFE_GIT_REF.test(ref) || ref.includes('..')) {
    return Promise.reject(new Error('不是合法的 Git 引用（不能包含空格、.. 等字符）'))
  }
  return Promise.resolve()
}

const GIT_REF_RULES = [{ required: true, validator: validateGitRef }]

/** Mirrors the `externalRef` pattern of `ReviewDtos.Create`. */
const EXTERNAL_REF = /^[1-9][0-9]{0,18}$/

function needsExternalRef(targetType: ReviewTargetType | undefined): boolean {
  return targetType === 'PULL_REQUEST' || targetType === 'ISSUE'
}

/** Only the reference fields that belong to the chosen target are sent. */
function toRequest({
  baseRef,
  headRef,
  externalRef,
  background,
  modelSelection,
  ...values
}: FormValues): CreateReviewRequest {
  const isRange = values.targetType === 'COMMIT_RANGE'
  const selected = modelSelection ? parseModelSelection(modelSelection) : undefined
  return {
    ...values,
    ...(selected ? { modelConfigId: selected.configId, modelName: selected.modelName } : {}),
    baseRef: isRange ? baseRef?.trim() : undefined,
    headRef: isRange ? headRef?.trim() : undefined,
    externalRef: needsExternalRef(values.targetType) ? externalRef?.trim() : undefined,
    background: background?.trim() || undefined,
  }
}

function TargetFields({ targetType }: { targetType: ReviewTargetType | undefined }) {
  switch (targetType) {
    case 'COMMIT_RANGE':
      return (
        <>
          <ProFormText
            name="baseRef"
            label="基线（base）"
            tooltip="变更所基于的分支或提交，评审 base 与 head 分叉之后 head 上的改动"
            placeholder="main"
            rules={GIT_REF_RULES}
          />
          <ProFormText
            name="headRef"
            label="待评审（head）"
            placeholder="feature/login"
            rules={GIT_REF_RULES}
          />
        </>
      )
    case 'PULL_REQUEST':
    case 'ISSUE':
      return (
        <ProFormText
          name="externalRef"
          label={targetType === 'ISSUE' ? 'Issue 编号' : 'Pull Request 编号'}
          placeholder="42"
          rules={[
            { required: true, whitespace: true, message: '请输入编号' },
            { pattern: EXTERNAL_REF, transform: (v: string) => v.trim(), message: '必须是正整数' },
          ]}
        />
      )
    case 'LOCAL_WORKING_TREE':
      return (
        <Typography.Paragraph type="secondary">
          评审仓库中尚未提交的改动（已暂存、未暂存及未跟踪的文件）。
        </Typography.Paragraph>
      )
    default:
      return null
  }
}

/** Rendered only while the modal is open, so the option lists are fetched on demand. */
function ReviewFormFields() {
  const repositories = useQuery(repositoryQueries.list())
  const modelConfigs = useQuery(modelConfigQueries.list())

  return (
    <>
      {/* Constraints mirror review.task.dto.ReviewDtos.Create. */}
      <ProFormSelect<number>
        name="repositoryId"
        label="仓库"
        showSearch
        fieldProps={{
          loading: repositories.isPending,
          optionFilterProp: 'label',
          notFoundContent: repositories.isPending
            ? undefined
            : '暂无仓库，请先在「代码仓库」中添加',
        }}
        options={repositories.data?.map((repo) => ({ value: repo.id, label: repo.name }))}
        rules={[{ required: true, message: '请选择仓库' }]}
      />
      <ProFormDependency name={['repositoryId', 'targetType']}>
        {({ repositoryId, targetType }: Partial<FormValues>) => {
          const repo = repositories.data?.find((r) => r.id === repositoryId)
          const allowed = TARGETS_BY_SOURCE[repo?.sourceType ?? 'LOCAL']
          return (
            <>
              <ProFormRadio.Group
                name="targetType"
                label="评审对象"
                radioType="button"
                options={allowed.map((value) => ({ value, label: REVIEW_TARGET_LABELS[value] }))}
                rules={[{ required: true, message: '请选择评审对象' }]}
              />
              <TargetFields targetType={targetType} />
            </>
          )
        }}
      </ProFormDependency>
      <ProFormSelect<ReviewEffort>
        name="effort"
        label="评审强度"
        placeholder="使用服务端默认值"
        allowClear
        options={EFFORT_OPTIONS}
      />
      <ProFormSelect<string>
        name="modelSelection"
        label="模型"
        placeholder="使用默认模型"
        allowClear
        showSearch
        fieldProps={{ loading: modelConfigs.isPending, optionFilterProp: 'label' }}
        options={modelConfigs.data?.map((config) => ({
          label: config.name,
          options: Object.keys(config.models).map((modelName) => ({
            value: modelSelectionValue(config.id, modelName),
            label: modelName,
          })),
        }))}
      />
      <ProFormTextArea
        name="background"
        label="背景说明"
        tooltip="告诉评审 Agent 这次改动的目的、需求或需要重点关注的地方"
        fieldProps={{ autoSize: { minRows: 3, maxRows: 8 }, showCount: true, maxLength: 8000 }}
        rules={[{ max: 8000, message: '最多 8000 个字符' }]}
      />
    </>
  )
}

export function CreateReviewModal({ trigger, defaultRepositoryId }: CreateReviewModalProps) {
  const create = useCreateReview()
  const queryClient = useQueryClient()
  const [form] = Form.useForm<FormValues>()

  const targetsOf = (repositoryId: number | undefined) => {
    const repo = queryClient
      .getQueryData(repositoryQueries.list().queryKey)
      ?.find((r) => r.id === repositoryId)
    return TARGETS_BY_SOURCE[repo?.sourceType ?? 'LOCAL']
  }

  // A repository of another kind may not support the chosen target; fall back to its first one.
  const handleValuesChange = (changed: Partial<FormValues>) => {
    if (changed.repositoryId === undefined) return
    const allowed = targetsOf(changed.repositoryId)
    if (!allowed.includes(form.getFieldValue('targetType'))) {
      form.setFieldValue('targetType', allowed[0])
    }
  }

  return (
    <ModalForm<FormValues>
      title="新建评审"
      trigger={trigger}
      width={600}
      form={form}
      modalProps={{ destroyOnHidden: true }}
      onValuesChange={handleValuesChange}
      initialValues={{
        repositoryId: defaultRepositoryId,
        targetType: targetsOf(defaultRepositoryId)[0],
      }}
      onFinish={(values) => runAction(() => create.mutateAsync(toRequest(values)))}
    >
      <ReviewFormFields />
    </ModalForm>
  )
}
