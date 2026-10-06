import { queryOptions, useMutation, useQueryClient } from '@tanstack/react-query'

import { credentialApi } from './api'
import type { CreateCredentialRequest, UpdateCredentialRequest } from './types'

export const credentialKeys = {
  all: ['credentials'] as const,
  list: () => [...credentialKeys.all, 'list'] as const,
}

export const credentialQueries = {
  list: () =>
    queryOptions({
      queryKey: credentialKeys.list(),
      queryFn: ({ signal }) => credentialApi.list(signal),
    }),
}

function useInvalidateCredentials() {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: credentialKeys.all })
}

export function useCreateCredential() {
  const invalidate = useInvalidateCredentials()
  return useMutation({
    mutationFn: (body: CreateCredentialRequest) => credentialApi.create(body),
    onSuccess: invalidate,
    meta: { successMessage: '凭据已保存' },
  })
}

export function useUpdateCredential() {
  const invalidate = useInvalidateCredentials()
  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: UpdateCredentialRequest }) =>
      credentialApi.update(id, body),
    onSuccess: invalidate,
    meta: { successMessage: '凭据已更新' },
  })
}

export function useDeleteCredential() {
  const invalidate = useInvalidateCredentials()
  return useMutation({
    mutationFn: (id: number) => credentialApi.remove(id),
    onSuccess: invalidate,
    meta: { successMessage: '凭据已删除' },
  })
}
