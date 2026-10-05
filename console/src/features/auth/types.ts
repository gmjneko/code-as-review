/** Mirrors `auth.dto.TokenResponse`. `expiresIn` is the access token lifetime in seconds. */
export interface TokenResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
}

/** Mirrors `auth.dto.AuthRequests.Login`. */
export interface LoginRequest {
  username: string
  password: string
}

/** Mirrors `auth.dto.AuthRequests.Register`. */
export interface RegisterRequest {
  username: string
  email?: string
  password: string
}

/** Identity derived from the access token claims (`sub`, `username`). */
export interface CurrentUser {
  id: string
  username: string
}
