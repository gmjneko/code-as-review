import { http } from '@/shared/api'

import type { LoginRequest, RegisterRequest, TokenResponse } from './types'

export const authApi = {
  login: (body: LoginRequest) => http.post<TokenResponse>('/auth/login', body, { auth: false }),

  register: (body: RegisterRequest) =>
    http.post<TokenResponse>('/auth/register', body, { auth: false }),

  refresh: (refreshToken: string) =>
    http.post<TokenResponse>('/auth/refresh', { refreshToken }, { auth: false }),

  logout: (refreshToken: string) => http.post<void>('/auth/logout', { refreshToken }),
}
