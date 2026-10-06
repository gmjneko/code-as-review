import type { InstantString } from '@/shared/api'

export interface ScmCredential {
  id: number
  name: string
  provider: string
  authType: string
  host: string
  maskedToken: string
  remark: string | null
  expiresAt: InstantString | null
  createdAt: InstantString
}

export interface CreateCredentialRequest {
  name: string
  token: string
  host?: string
  remark?: string
}

/** A blank token leaves the existing encrypted token unchanged. */
export interface UpdateCredentialRequest {
  name?: string
  token?: string
  host?: string
  remark?: string
}
