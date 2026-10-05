import { Flex, theme } from 'antd'
import type { ReactNode } from 'react'

export function AuthLayout({ children }: { children: ReactNode }) {
  const { token } = theme.useToken()

  return (
    <Flex
      align="center"
      justify="center"
      style={{ minHeight: '100vh', paddingBlock: token.paddingXL, background: token.colorBgLayout }}
    >
      {children}
    </Flex>
  )
}
