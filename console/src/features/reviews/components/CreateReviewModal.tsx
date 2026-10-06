import {
  ModalForm,
  ProFormDependency,
  ProFormRadio,
  ProFormSelect,
  ProFormText,
  ProFormTextArea,
} from '@ant-design/pro-components'
import { useQuery } from '@tanstack/react-query'
import { Typography } from 'antd'
import type { ReactElement } from 'react'

import { modelConfigQueries } from '@/features/model-configs'
import { repositoryQueries } from '@/features/repositories'
import { runAction } from '@/shared/utils'

import {
  CREATABLE_TARGET_TYPES,
  REVIEW_EFFORT_LABELS,
  REVIEW_EFFORT_ROUNDS,
  REVIEW_EFFORTS,
  REVIEW_TARGET_LABELS,
  SAFE_GIT_REF,
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
  effort?: ReviewEffort
  modelConfigId?: number
  background?: string
}

const TARGET_OPTIONS = CREATABLE_TARGET_TYPES.map((value) => ({
  value,
  label: REVIEW_TARGET_LABELS[value],
}))

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

function toRequest({ baseRef, headRef, background, ...values }: FormValues): CreateReviewRequest {
  const isRange = values.targetType === 'COMMIT_RANGE'
  return {
    ...values,
    baseRef: isRange ? baseRef?.trim() : undefined,
    headRef: isRange ? headRef?.trim() : undefined,
    background: background?.trim() || undefined,
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
      <ProFormRadio.Group
        name="targetType"
        label="评审对象"
        radioType="button"
        options={TARGET_OPTIONS}
        rules={[{ required: true, message: '请选择评审对象' }]}
      />
      <ProFormDependency name={['targetType']}>
        {({ targetType }: Partial<FormValues>) =>
          targetType === 'COMMIT_RANGE' ? (
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
          ) : (
            <Typography.Paragraph type="secondary">
              评审仓库中尚未提交的改动（已暂存、未暂存及未跟踪的文件）。
            </Typography.Paragraph>
          )
        }
      </ProFormDependency>
      <ProFormSelect<ReviewEffort>
        name="effort"
        label="评审强度"
        placeholder="使用服务端默认值"
        allowClear
        options={EFFORT_OPTIONS}
      />
      <ProFormSelect<number>
        name="modelConfigId"
        label="模型"
        placeholder="使用默认模型"
        allowClear
        fieldProps={{ loading: modelConfigs.isPending }}
        options={modelConfigs.data?.map((config) => ({
          value: config.id,
          label: `${config.name}（${config.modelName}）${config.isDefault ? ' · 默认' : ''}`,
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

  return (
    <ModalForm<FormValues>
      title="新建评审"
      trigger={trigger}
      width={600}
      modalProps={{ destroyOnHidden: true }}
      initialValues={{ repositoryId: defaultRepositoryId, targetType: 'LOCAL_WORKING_TREE' }}
      onFinish={(values) => runAction(() => create.mutateAsync(toRequest(values)))}
    >
      <ReviewFormFields />
    </ModalForm>
  )
}
