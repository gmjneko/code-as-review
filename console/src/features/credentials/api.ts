import { http } from '@/shared/api'

import type { CreateCredentialRequest, ScmCredential, UpdateCredentialRequest } from './types'

const BASE = '/scm-credentials'

export const credentialApi = {
  list: (signal?: AbortSignal) => http.get<ScmCredential[]>(BASE, { signal }),
  create: (body: CreateCredentialRequest) => http.post<ScmCredential>(BASE, body),
  update: (id: number, body: UpdateCredentialRequest) =>
    http.put<ScmCredential>(`${BASE}/${id}`, body),
  remove: (id: number) => http.delete(`${BASE}/${id}`),
}
