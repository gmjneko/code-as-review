// Public API of the auth feature. Other modules must import from '@/features/auth' only.
export { useCurrentUser, useLogout } from './hooks'
export { httpAuthAdapter } from './http-auth-adapter'
export { LoginPage } from './pages/LoginPage'
export { RegisterPage } from './pages/RegisterPage'
export { sanitizeRedirect } from './redirect'
export { loginSearchSchema } from './search'
export { isAuthenticated, syncSessionAcrossTabs, useSessionStore } from './session-store'
export type { CurrentUser } from './types'
