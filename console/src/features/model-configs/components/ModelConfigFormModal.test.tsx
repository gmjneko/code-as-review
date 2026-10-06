import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { renderWithProviders } from '@/test/render'

import { modelConfigApi } from '../api'
import type { ModelConfig } from '../types'
import { ModelConfigFormModal } from './ModelConfigFormModal'

type ModelConfigApi = typeof modelConfigApi

vi.mock('../api', () => ({
  modelConfigApi: {
    list: vi.fn<ModelConfigApi['list']>(),
    create: vi.fn<ModelConfigApi['create']>(),
    update: vi.fn<ModelConfigApi['update']>(),
    remove: vi.fn<ModelConfigApi['remove']>(),
  },
}))

const record: ModelConfig = {
  id: 7,
  name: 'Primary',
  baseUrl: 'https://api.example.com/v1',
  models: {
    'gpt-4o': {
      limit: { context: 128000, output: 8192 },
      modalities: { input: ['text'], reasoning_effort: ['low', 'high', 'max'] },
    },
  },
  apiKeyMasked: 'sk-****abcd',
  isDefault: true,
  createdAt: '2026-10-01T02:00:00Z',
}

beforeEach(() => {
  vi.mocked(modelConfigApi.update).mockResolvedValue(record)
})

describe('ModelConfigFormModal', () => {
  it('confirms removing a model and keeps at least one model', async () => {
    const user = userEvent.setup()
    renderWithProviders(<ModelConfigFormModal record={record} trigger={<button>edit</button>} />)

    await user.click(screen.getByRole('button', { name: 'edit' }))
    expect(await screen.findByRole('button', { name: '删除模型' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: /添加模型/ }))
    const remove = screen.getAllByRole('button', { name: '删除模型' })[1]
    if (!remove) throw new Error('新增模型删除按钮未显示')
    await user.click(remove)
    expect(screen.getAllByRole('textbox', { name: '模型名称' })).toHaveLength(2)
    const confirmation = await screen.findByRole('tooltip')
    await user.click(within(confirmation).getByRole('button', { name: /确\s*认|确\s*定/ }))
    expect(screen.getAllByRole('textbox', { name: '模型名称' })).toHaveLength(1)
    expect(screen.getByRole('button', { name: '删除模型' })).toBeDisabled()
  })

  it('expands advanced settings and saves token limits after collapsing them', async () => {
    const user = userEvent.setup()
    renderWithProviders(<ModelConfigFormModal record={record} trigger={<button>edit</button>} />)

    await user.click(screen.getByRole('button', { name: 'edit' }))
    expect(await screen.findByRole('checkbox', { name: '文本' })).toBeChecked()
    expect(screen.getByRole('checkbox', { name: '文本' })).toBeDisabled()
    expect(screen.queryByRole('combobox', { name: '思考强度设置' })).not.toBeInTheDocument()
    expect(screen.queryByRole('spinbutton', { name: '上下文窗口' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '展开模型设置' }))
    expect(screen.getByRole('button', { name: '收起模型设置' })).toHaveAttribute(
      'aria-expanded',
      'true',
    )
    expect(screen.getByRole('combobox', { name: '思考强度设置' })).toBeInTheDocument()
    const context = screen.getByRole('spinbutton', { name: '上下文窗口' })
    const output = screen.getByRole('spinbutton', { name: '最大输出 token' })
    expect(context).toHaveValue('128K')
    expect(output).toHaveValue('8192')
    await user.clear(context)
    await user.type(context, '512K')
    await user.clear(output)
    await user.type(output, '32K')
    await user.click(screen.getByRole('button', { name: '收起模型设置' }))
    expect(screen.queryByRole('spinbutton', { name: '上下文窗口' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /确\s*认|确\s*定/ }))

    await waitFor(() =>
      expect(modelConfigApi.update).toHaveBeenCalledExactlyOnceWith(7, {
        name: record.name,
        baseUrl: record.baseUrl,
        models: {
          'gpt-4o': {
            limit: { context: 512000, output: 32000 },
            modalities: { input: ['text'], reasoning_effort: ['low', 'high', 'max'] },
          },
        },
        isDefault: true,
      }),
    )
  })

  it('adds another model with default capabilities and preserves image input', async () => {
    const user = userEvent.setup()
    renderWithProviders(<ModelConfigFormModal record={record} trigger={<button>edit</button>} />)

    await user.click(screen.getByRole('button', { name: 'edit' }))
    await user.click(await screen.findByRole('button', { name: /添加模型/ }))
    const modelName = screen.getAllByRole('textbox', { name: '模型名称' })[1]
    const imageInput = screen.getAllByRole('checkbox', { name: '图片' })[1]
    if (!modelName || !imageInput) throw new Error('新增模型行未显示')
    await user.type(modelName, 'deepseek-v4.1-flash')
    await user.click(imageInput)
    const expand = screen.getAllByRole('button', { name: '展开模型设置' })[1]
    if (!expand) throw new Error('新增模型展开按钮未显示')
    await user.click(expand)
    expect(screen.getByRole('spinbutton', { name: '上下文窗口' })).toHaveValue('256K')
    expect(screen.getByRole('spinbutton', { name: '最大输出 token' })).toHaveValue('64K')
    await user.click(screen.getByRole('button', { name: '收起模型设置' }))
    await user.click(screen.getByRole('button', { name: /确\s*认|确\s*定/ }))

    await waitFor(() =>
      expect(modelConfigApi.update).toHaveBeenCalledExactlyOnceWith(7, {
        name: record.name,
        baseUrl: record.baseUrl,
        models: {
          ...record.models,
          'deepseek-v4.1-flash': {
            limit: { context: 256000, output: 64000 },
            modalities: { input: ['text', 'image'], reasoning_effort: ['low', 'high', 'max'] },
          },
        },
        isDefault: true,
      }),
    )
  })

  it('keeps the stored API key when the field is left blank on edit', async () => {
    const user = userEvent.setup()
    renderWithProviders(<ModelConfigFormModal record={record} trigger={<button>edit</button>} />)

    await user.click(screen.getByRole('button', { name: 'edit' }))
    const name = await screen.findByLabelText('名称')
    await user.clear(name)
    await user.type(name, 'Renamed')
    await user.click(screen.getByRole('button', { name: /确\s*认|确\s*定/ }))

    await waitFor(() =>
      expect(modelConfigApi.update).toHaveBeenCalledExactlyOnceWith(7, {
        name: 'Renamed',
        baseUrl: record.baseUrl,
        models: record.models,
        isDefault: true,
      }),
    )
  })
})
