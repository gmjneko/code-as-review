import { ApiError } from '@/shared/api'

/**
 * Wraps the body of a UI callback that takes a promise (ProForm `onFinish`, Popconfirm
 * `onConfirm`, ...). Resolves `true` on success and `false` on failure, so a failed mutation keeps
 * a ModalForm open instead of surfacing as `[ProForm] onFinish error`. API errors have already
 * been shown by the global mutation error handler; anything else is a bug and is logged.
 */
export async function runAction(action: () => Promise<unknown>): Promise<boolean> {
  try {
    await action()
    return true
  } catch (error) {
    if (!(error instanceof ApiError)) {
      console.error(error)
    }
    return false
  }
}
