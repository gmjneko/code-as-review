import { createFileRoute, redirect } from '@tanstack/react-router'

import { BasicLayout } from '@/app/layout/BasicLayout'
import { isAuthenticated } from '@/features/auth'

/** Pathless layout route: everything below it requires a session and renders in BasicLayout. */
export const Route = createFileRoute('/_authenticated')({
  beforeLoad: ({ location }) => {
    if (!isAuthenticated()) {
      throw redirect({ to: '/login', search: { redirect: location.href } })
    }
  },
  component: BasicLayout,
})
