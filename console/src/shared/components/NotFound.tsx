import { Link } from '@tanstack/react-router'
import { Button, Result } from 'antd'

export function NotFound() {
  return (
    <Result
      status="404"
      title="页面不存在"
      subTitle="您访问的页面不存在或已被移除。"
      extra={
        <Link to="/">
          <Button type="primary">返回首页</Button>
        </Link>
      }
    />
  )
}
