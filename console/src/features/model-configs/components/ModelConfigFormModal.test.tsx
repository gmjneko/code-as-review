import { screen, waitFor } from '@testing-library/react'
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
  modelName: 'gpt-4o',
  apiKeyMasked: 'sk-****abcd',
  isDefault: true,
  createdAt: '2026-10-01T02:00:00Z',
}

beforeEach(() => {
  vi.mocked(modelConfigApi.update).mockResolvedValue(record)
})

describe('ModelConfigFormModal', () => {
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
        modelName: record.modelName,
        isDefault: true,
      }),
    )
  })
})
