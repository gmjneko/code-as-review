import { useQueryErrorResetBoundary } from '@tanstack/react-query'
import type { ErrorComponentProps } from '@tanstack/react-router'
import { useRouter } from '@tanstack/react-router'
import { Button, Result } from 'antd'
import { useEffect } from 'react'

import { getErrorMessage } from '@/shared/api'

/** Default error boundary for routes: shows the error and lets the user retry the route. */
export function RouteError({ error }: ErrorComponentProps) {
  const router = useRouter()
  const queryErrorResetBoundary = useQueryErrorResetBoundary()

  useEffect(() => {
    queryErrorResetBoundary.reset()
  }, [queryErrorResetBoundary])

  return (
    <Result
      status="error"
      title="页面加载失败"
      subTitle={getErrorMessage(error)}
      extra={
        <Button type="primary" onClick={() => void router.invalidate()}>
          重试
        </Button>
      }
    />
  )
}
