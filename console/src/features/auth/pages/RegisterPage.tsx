import { LockOutlined, MailOutlined, UserOutlined } from '@ant-design/icons'
import { LoginForm, ProFormText } from '@ant-design/pro-components'
import { Link, useNavigate } from '@tanstack/react-router'
import { Typography } from 'antd'

import { runAction } from '@/shared/utils'

import { AuthLayout } from '../components/AuthLayout'
import { useRegister } from '../hooks'
import type { RegisterRequest } from '../types'

interface RegisterFormValues extends RegisterRequest {
  confirmPassword: string
}

export function RegisterPage() {
  const navigate = useNavigate()
  const register = useRegister()

  const handleFinish = ({ confirmPassword, email, ...values }: RegisterFormValues) =>
    runAction(async () => {
      // The backend treats a blank email as "not provided"; omit it rather than send "".
      await register.mutateAsync({ ...values, ...(email ? { email } : {}) })
      await navigate({ to: '/', replace: true })
    })

  return (
    <AuthLayout>
      <LoginForm<RegisterFormValues>
        title="创建账号"
        subTitle="注册后即可配置模型并接入代码仓库"
        submitter={{ searchConfig: { submitText: '注册' } }}
        onFinish={handleFinish}
        actions={
          <Typography.Text type="secondary">
            已有账号？<Link to="/login">返回登录</Link>
          </Typography.Text>
        }
      >
        {/* Constraints mirror auth.dto.AuthRequests.Register. */}
        <ProFormText
          name="username"
          placeholder="用户名"
          fieldProps={{ size: 'large', prefix: <UserOutlined />, autoComplete: 'username' }}
          rules={[
            { required: true, message: '请输入用户名' },
            { min: 3, max: 64, message: '长度为 3-64 个字符' },
            { pattern: /^[A-Za-z0-9_.-]+$/, message: '只能包含字母、数字以及 _ . -' },
          ]}
        />
        <ProFormText
          name="email"
          placeholder="邮箱（可选）"
          fieldProps={{ size: 'large', prefix: <MailOutlined />, autoComplete: 'email' }}
          rules={[
            { type: 'email', message: '邮箱格式不正确' },
            { max: 128, message: '最多 128 个字符' },
          ]}
        />
        <ProFormText.Password
          name="password"
          placeholder="密码"
          fieldProps={{ size: 'large', prefix: <LockOutlined />, autoComplete: 'new-password' }}
          rules={[
            { required: true, message: '请输入密码' },
            { min: 8, max: 72, message: '长度为 8-72 个字符' },
          ]}
        />
        <ProFormText.Password
          name="confirmPassword"
          placeholder="确认密码"
          dependencies={['password']}
          fieldProps={{ size: 'large', prefix: <LockOutlined />, autoComplete: 'new-password' }}
          rules={[
            { required: true, message: '请再次输入密码' },
            ({ getFieldValue }) => ({
              validator: (_, value) =>
                !value || getFieldValue('password') === value
                  ? Promise.resolve()
                  : Promise.reject(new Error('两次输入的密码不一致')),
            }),
          ]}
        />
      </LoginForm>
    </AuthLayout>
  )
}
