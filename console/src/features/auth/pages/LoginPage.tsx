import { LockOutlined, UserOutlined } from '@ant-design/icons'
import { LoginForm, ProFormText } from '@ant-design/pro-components'
import { getRouteApi, Link, useRouter } from '@tanstack/react-router'
import { Typography } from 'antd'

import { runAction } from '@/shared/utils'

import { AuthLayout } from '../components/AuthLayout'
import { useLogin } from '../hooks'
import { sanitizeRedirect } from '../redirect'
import type { LoginRequest } from '../types'

const routeApi = getRouteApi('/login')

export function LoginPage() {
  const { redirect } = routeApi.useSearch()
  const router = useRouter()
  const login = useLogin()

  const handleFinish = (values: LoginRequest) =>
    runAction(async () => {
      await login.mutateAsync(values)
      router.history.replace(sanitizeRedirect(redirect))
    })

  return (
    <AuthLayout>
      <LoginForm<LoginRequest>
        title="Code as Review"
        subTitle="AI 代码评审平台"
        onFinish={handleFinish}
        actions={
          <Typography.Text type="secondary">
            还没有账号？<Link to="/register">立即注册</Link>
          </Typography.Text>
        }
      >
        <ProFormText
          name="username"
          placeholder="用户名"
          fieldProps={{ size: 'large', prefix: <UserOutlined />, autoComplete: 'username' }}
          rules={[{ required: true, message: '请输入用户名' }]}
        />
        <ProFormText.Password
          name="password"
          placeholder="密码"
          fieldProps={{ size: 'large', prefix: <LockOutlined />, autoComplete: 'current-password' }}
          rules={[{ required: true, message: '请输入密码' }]}
        />
      </LoginForm>
    </AuthLayout>
  )
}
