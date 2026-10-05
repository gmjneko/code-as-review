import { createFileRoute, redirect } from '@tanstack/react-router'

import { isAuthenticated, RegisterPage } from '@/features/auth'

export const Route = createFileRoute('/register')({
  beforeLoad: () => {
    if (isAuthenticated()) {
      throw redirect({ to: '/' })
    }
  },
  component: RegisterPage,
})
