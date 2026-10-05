import { createFileRoute, redirect } from '@tanstack/react-router'

import { isAuthenticated, LoginPage, loginSearchSchema, sanitizeRedirect } from '@/features/auth'

export const Route = createFileRoute('/login')({
  validateSearch: loginSearchSchema,
  beforeLoad: ({ search }) => {
    if (isAuthenticated()) {
      throw redirect({ href: sanitizeRedirect(search.redirect) })
    }
  },
  component: LoginPage,
})
