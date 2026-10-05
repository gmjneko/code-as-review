import { describe, expect, it, vi } from 'vitest'

import { ApiError } from '@/shared/api'
import { feedback } from '@/shared/feedback'

import { createQueryClient } from './query-client'

vi.mock('@/shared/feedback', () => ({
  feedback: {
    message: {
      error: vi.fn<(content: string) => void>(),
      success: vi.fn<(content: string) => void>(),
    },
  },
}))

function runFailingMutation(error: unknown, meta?: { silent?: boolean }) {
  const queryClient = createQueryClient()
  const mutation = queryClient
    .getMutationCache()
    .build(queryClient, { mutationFn: () => Promise.reject(error), meta })
  return mutation.execute(undefined).catch(() => {})
}

describe('global mutation error reporting', () => {
  it('reports a 401 that is not a session expiry, such as a wrong password', async () => {
    await runFailingMutation(new ApiError(401, 'UNAUTHORIZED', 'invalid username or password'))

    expect(feedback.message.error).toHaveBeenCalledWith('invalid username or password')
  })

  it('stays quiet when the session expired, since the user is being redirected', async () => {
    await runFailingMutation(new ApiError(401, 'SESSION_EXPIRED', '登录已失效'))

    expect(feedback.message.error).not.toHaveBeenCalled()
  })

  it('stays quiet for silent mutations', async () => {
    await runFailingMutation(new ApiError(500, 'INTERNAL_ERROR', 'boom'), { silent: true })

    expect(feedback.message.error).not.toHaveBeenCalled()
  })
})
