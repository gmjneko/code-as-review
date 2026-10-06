import { ModalForm, ProFormDependency, ProFormRadio, ProFormText } from '@ant-design/pro-components'
import type { ReactElement } from 'react'

import { runAction } from '@/shared/utils'

import { SOURCE_TYPE_META, SOURCE_TYPES, SUPPORTED_SOURCE_TYPES } from '../constants'
import { useCreateRepository, useUpdateRepository } from '../queries'
import type { CreateRepositoryRequest, Repository, SourceType } from '../types'

interface RepositoryFormModalProps {
  trigger: ReactElement
  /** The repository to edit; omit to register a new one. */
  record?: Repository
}

interface FormValues {
  name: string
  sourceType: SourceType
  localPath?: string
  remoteUrl?: string
  defaultBranch?: string
}

const SOURCE_TYPE_OPTIONS = SOURCE_TYPES.map((value) => {
  const supported = SUPPORTED_SOURCE_TYPES.includes(value)
  return {
    value,
    label: supported
      ? SOURCE_TYPE_META[value].label
      : `${SOURCE_TYPE_META[value].label}（即将支持）`,
    disabled: !supported,
  }
})

function trimmed(value: string | undefined): string | undefined {
  const text = value?.trim()
  return text ? text : undefined
}

/** Only the fields that belong to the chosen source type are sent. */
function toCreateRequest(values: FormValues): CreateRepositoryRequest {
  const isLocal = values.sourceType === 'LOCAL'
  return {
    name: values.name.trim(),
    sourceType: values.sourceType,
    localPath: isLocal ? trimmed(values.localPath) : undefined,
    remoteUrl: isLocal ? undefined : trimmed(values.remoteUrl),
    defaultBranch: trimmed(values.defaultBranch),
  }
}

export function RepositoryFormModal({ trigger, record }: RepositoryFormModalProps) {
  const create = useCreateRepository()
  const update = useUpdateRepository()
  const isEdit = record !== undefined

  const handleFinish = (values: FormValues) =>
    runAction(() =>
      record
        ? update.mutateAsync({
            id: record.id,
            // The backend leaves an omitted branch unchanged, so a cleared field is sent as ''.
            body: { name: values.name.trim(), defaultBranch: trimmed(values.defaultBranch) ?? '' },
          })
        : create.mutateAsync(toCreateRequest(values)),
    )

  return (
    <ModalForm<FormValues>
      title={isEdit ? '编辑仓库' : '添加仓库'}
      trigger={trigger}
      width={560}
      modalProps={{ destroyOnHidden: true }}
      initialValues={
        record
          ? {
              name: record.name,
              sourceType: record.sourceType,
              localPath: record.localPath ?? undefined,
              remoteUrl: record.remoteUrl ?? undefined,
              defaultBranch: record.defaultBranch ?? undefined,
            }
          : { sourceType: 'LOCAL' }
      }
      onFinish={handleFinish}
    >
      {/* Constraints mirror repo.dto.RepoDtos. */}
      <ProFormText
        name="name"
        label="名称"
        rules={[
          { required: true, whitespace: true, message: '请输入名称' },
          { max: 128, message: '最多 128 个字符' },
        ]}
      />
      <ProFormRadio.Group
        name="sourceType"
        label="来源"
        radioType="button"
        options={SOURCE_TYPE_OPTIONS}
        disabled={isEdit}
        rules={[{ required: true, message: '请选择来源' }]}
      />
      <ProFormDependency name={['sourceType']}>
        {({ sourceType }: Partial<FormValues>) =>
          sourceType === 'LOCAL' ? (
            <ProFormText
              name="localPath"
              label="本地路径"
              tooltip="服务器上 Git 仓库的绝对路径，必须位于服务端配置的 local-repo-roots 之内"
              placeholder="/srv/repos/my-project"
              disabled={isEdit}
              rules={[
                { required: true, whitespace: true, message: '请输入本地路径' },
                { max: 1024, message: '最多 1024 个字符' },
              ]}
            />
          ) : (
            <ProFormText
              name="remoteUrl"
              label="仓库地址"
              placeholder="https://github.com/owner/repo"
              disabled={isEdit}
              rules={[
                { required: true, whitespace: true, message: '请输入仓库地址' },
                { max: 1024, message: '最多 1024 个字符' },
              ]}
            />
          )
        }
      </ProFormDependency>
      <ProFormText
        name="defaultBranch"
        label="默认分支"
        placeholder="main"
        rules={[{ max: 255, message: '最多 255 个字符' }]}
      />
    </ModalForm>
  )
}
