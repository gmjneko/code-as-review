import { QueryClientProvider, type QueryClient } from '@tanstack/react-query'
import { App as AntdApp, ConfigProvider } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import type { ReactNode } from 'react'

import { FeedbackBridge } from '@/shared/feedback'

interface AppProvidersProps {
  queryClient: QueryClient
  children: ReactNode
}

/**
 * Global providers. Theme customization goes into `ConfigProvider` here and nowhere else. Keep the
 * antd locale in sync with the dayjs locale set in main.tsx.
 */
export function AppProviders({ queryClient, children }: AppProvidersProps) {
  return (
    <ConfigProvider locale={zhCN}>
      <AntdApp>
        <FeedbackBridge />
        <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
      </AntdApp>
    </ConfigProvider>
  )
}
