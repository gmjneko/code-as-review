import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { modelConfigQueries } from '@/features/model-configs'
import { repositoryQueries } from '@/features/repositories'
import { renderWithProviders } from '@/test/render'

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

function renderModal() {
  const result = renderWithProviders(
    <CreateReviewModal defaultRepositoryId={5} trigger={<button>new</button>} />,
  )
  // Seed the option lists so the test does not depend on other features' API modules.
  result.queryClient.setQueryData(repositoryQueries.list().queryKey, [
    {
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
    },
  ])
  result.queryClient.setQueryData(modelConfigQueries.list().queryKey, [])
  return result
}

const confirmButton = () => screen.getByRole('button', { name: /确\s*认|确\s*定/ })

beforeEach(() => {
  vi.mocked(reviewApi.create).mockResolvedValue(createdTask)
})

describe('CreateReviewModal', () => {
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
        background: undefined,
      }),
    )
  })
})
