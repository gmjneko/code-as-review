import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { type ModelConfig, modelConfigQueries } from '@/features/model-configs'
import { type Repository, repositoryQueries } from '@/features/repositories'
import { createTestQueryClient, renderWithProviders } from '@/test/render'

import { reviewApi } from '../api'
import type { ReviewTask } from '../types'
import { CreateReviewModal } from './CreateReviewModal'

type ReviewApi = typeof reviewApi

vi.mock('../api', () => ({
  reviewApi: {
    list: vi.fn<ReviewApi['list']>(),
    create: vi.fn<ReviewApi['create']>(),
    cancel: vi.fn<ReviewApi['cancel']>(),
  },
}))

// oxlint-disable-next-line typescript/no-unsafe-type-assertion -- the form ignores the response
const createdTask = { id: 1 } as ReviewTask

const localRepo: Repository = {
  id: 5,
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

const githubRepo: Repository = {
  ...localRepo,
  id: 6,
  name: 'web',
  sourceType: 'GITHUB',
  localPath: null,
  remoteUrl: 'https://github.com/acme/web',
  externalFullName: 'acme/web',
}

function renderModal(defaultRepositoryId = localRepo.id, models: ModelConfig[] = []) {
  // Seed the option lists so the test does not depend on other features' API modules.
  const queryClient = createTestQueryClient()
  queryClient.setQueryData(repositoryQueries.list().queryKey, [localRepo, githubRepo])
  queryClient.setQueryData(modelConfigQueries.list().queryKey, models)
  return renderWithProviders(
    <CreateReviewModal defaultRepositoryId={defaultRepositoryId} trigger={<button>new</button>} />,
    { queryClient },
  )
}

const confirmButton = () => screen.getByRole('button', { name: /确\s*认|确\s*定/ })

beforeEach(() => {
  vi.mocked(reviewApi.create).mockResolvedValue(createdTask)
})

describe('CreateReviewModal', () => {
  it('submits the specific model selected from a configuration group', async () => {
    const user = userEvent.setup()
    const capabilities = {
      limit: { context: 1024000, output: 131072 },
      modalities: { input: ['text'], reasoning_effort: ['low', 'high', 'max'] },
    }
    renderModal(localRepo.id, [
      {
        id: 7,
        name: '平台一',
        baseUrl: 'https://api.example.com/v1',
        models: { 'glm-5.3': capabilities, 'deepseek-v4.1-flash': capabilities },
        apiKeyMasked: '****',
        isDefault: true,
        createdAt: '2026-10-01T02:00:00Z',
      },
    ])

    await user.click(screen.getByRole('button', { name: 'new' }))
    await user.click(await screen.findByLabelText('模型'))
    await user.click(await screen.findByText('deepseek-v4.1-flash'))
    await user.click(confirmButton())

    await waitFor(() =>
      expect(reviewApi.create).toHaveBeenCalledExactlyOnceWith(
        expect.objectContaining({ modelConfigId: 7, modelName: 'deepseek-v4.1-flash' }),
      ),
    )
  })

  it('reviews the working tree of the preselected repository without refs', async () => {
    const user = userEvent.setup()
    renderModal()

    await user.click(screen.getByRole('button', { name: 'new' }))
    await screen.findByText('backend')
    await user.click(confirmButton())

    await waitFor(() =>
      expect(reviewApi.create).toHaveBeenCalledExactlyOnceWith({
        repositoryId: 5,
        targetType: 'LOCAL_WORKING_TREE',
        baseRef: undefined,
        headRef: undefined,
        externalRef: undefined,
        background: undefined,
      }),
    )
  })

  it('requires valid refs for a commit range and sends them trimmed', async () => {
    const user = userEvent.setup()
    renderModal()

    await user.click(screen.getByRole('button', { name: 'new' }))
    const targetGroup = await screen.findByRole('radiogroup')
    await user.click(within(targetGroup).getByText('提交区间'))
    await user.type(screen.getByLabelText('基线（base）'), 'main..dev')
    await user.type(screen.getByLabelText('待评审（head）'), ' feature/login ')
    await user.click(confirmButton())

    expect(await screen.findByText(/不是合法的 Git 引用/)).toBeInTheDocument()
    expect(reviewApi.create).not.toHaveBeenCalled()

    await user.clear(screen.getByLabelText('基线（base）'))
    await user.type(screen.getByLabelText('基线（base）'), 'main')
    await user.click(confirmButton())

    await waitFor(() =>
      expect(reviewApi.create).toHaveBeenCalledExactlyOnceWith({
        repositoryId: 5,
        targetType: 'COMMIT_RANGE',
        baseRef: 'main',
        headRef: 'feature/login',
        externalRef: undefined,
        background: undefined,
      }),
    )
  })

  it('offers pull requests for a remote repository and sends the PR number', async () => {
    const user = userEvent.setup()
    renderModal(githubRepo.id)

    await user.click(screen.getByRole('button', { name: 'new' }))
    const targetGroup = await screen.findByRole('radiogroup')
    expect(within(targetGroup).queryByText('本地工作区')).not.toBeInTheDocument()
    await user.type(screen.getByLabelText('Pull Request 编号'), ' 42 ')
    await user.click(confirmButton())

    await waitFor(() =>
      expect(reviewApi.create).toHaveBeenCalledExactlyOnceWith({
        repositoryId: 6,
        targetType: 'PULL_REQUEST',
        baseRef: undefined,
        headRef: undefined,
        externalRef: '42',
        background: undefined,
      }),
    )
  })
})
