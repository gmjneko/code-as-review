import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { renderWithProviders } from '@/test/render'

import { repositoryApi } from '../api'
import type { Repository } from '../types'
import { RepositoryFormModal } from './RepositoryFormModal'

type RepositoryApi = typeof repositoryApi

vi.mock('../api', () => ({
  repositoryApi: {
    list: vi.fn<RepositoryApi['list']>(),
    create: vi.fn<RepositoryApi['create']>(),
    update: vi.fn<RepositoryApi['update']>(),
    remove: vi.fn<RepositoryApi['remove']>(),
  },
}))

const record: Repository = {
  id: 3,
  name: 'backend',
  sourceType: 'LOCAL',
  localPath: '/srv/repos/backend',
  remoteUrl: null,
  externalFullName: null,
  defaultBranch: 'main',
  credentialId: null,
  lastSyncedAt: null,
  createdAt: '2026-10-01T02:00:00Z',
}

const confirmButton = () => screen.getByRole('button', { name: /确\s*认|确\s*定/ })

beforeEach(() => {
  vi.mocked(repositoryApi.create).mockResolvedValue(record)
  vi.mocked(repositoryApi.update).mockResolvedValue(record)
})

describe('RepositoryFormModal', () => {
  it('registers a local repository with trimmed values and no remote fields', async () => {
    const user = userEvent.setup()
    renderWithProviders(<RepositoryFormModal trigger={<button>add</button>} />)

    await user.click(screen.getByRole('button', { name: 'add' }))
    await user.type(await screen.findByLabelText('名称'), ' backend ')
    await user.type(screen.getByLabelText('本地路径'), '/srv/repos/backend ')
    await user.click(confirmButton())

    await waitFor(() =>
      expect(repositoryApi.create).toHaveBeenCalledExactlyOnceWith({
        name: 'backend',
        sourceType: 'LOCAL',
        localPath: '/srv/repos/backend',
        remoteUrl: undefined,
        defaultBranch: undefined,
      }),
    )
  })

  it('sends an empty default branch when it is cleared on edit', async () => {
    const user = userEvent.setup()
    renderWithProviders(<RepositoryFormModal record={record} trigger={<button>edit</button>} />)

    await user.click(screen.getByRole('button', { name: 'edit' }))
    await user.clear(await screen.findByLabelText('默认分支'))
    await user.click(confirmButton())

    await waitFor(() =>
      expect(repositoryApi.update).toHaveBeenCalledExactlyOnceWith(3, {
        name: 'backend',
        defaultBranch: '',
      }),
    )
  })
})
