import { Flex, Spin } from 'antd'

export function PageLoading() {
  return (
    <Flex align="center" justify="center" style={{ minHeight: 240 }}>
      <Spin size="large" />
    </Flex>
  )
}
