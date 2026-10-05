import type { App } from 'antd'

type FeedbackApi = ReturnType<typeof App.useApp>

let current: FeedbackApi | null = null

/** Installed by {@link FeedbackBridge}; not part of the public API. */
export function setFeedbackApi(api: FeedbackApi | null): void {
  current = api
}

function getApi(): FeedbackApi {
  if (!current) {
    throw new Error('Feedback API used before <FeedbackBridge /> mounted')
  }
  return current
}

/**
 * Context-aware antd `message` / `notification` / `modal` for code that runs outside React
 * components (query cache callbacks, the HTTP layer). Inside components prefer `App.useApp()`.
 */
export const feedback = {
  get message() {
    return getApi().message
  },
  get notification() {
    return getApi().notification
  },
  get modal() {
    return getApi().modal
  },
}
