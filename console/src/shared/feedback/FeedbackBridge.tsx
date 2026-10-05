import { App } from 'antd'
import { useEffect } from 'react'

import { setFeedbackApi } from './feedback'

/** Must render inside antd `<App>`; exposes its context-bound APIs to non-React code. */
export function FeedbackBridge() {
  const api = App.useApp()

  useEffect(() => {
    setFeedbackApi(api)
    return () => setFeedbackApi(null)
  }, [api])

  return null
}
