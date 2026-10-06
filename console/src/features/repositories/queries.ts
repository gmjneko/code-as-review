import { queryOptions, useMutation, useQueryClient } from '@tanstack/react-query'

import { repositoryApi } from './api'
import type { CreateRepositoryRequest, UpdateRepositoryRequest } from './types'

export const repositoryKeys = {
  all: ['repositories'] as const,
  list: () => [...repositoryKeys.all, 'list'] as const,
}

export const repositoryQueries = {
  list: () =>
    queryOptions({
      queryKey: repositoryKeys.list(),
      queryFn: ({ signal }) => repositoryApi.list(signal),
    }),
}

function useInvalidateRepositories() {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: repositoryKeys.all })
}

export function useCreateRepository() {
  const invalidate = useInvalidateRepositories()
  return useMutation({
    mutationFn: (body: CreateRepositoryRequest) => repositoryApi.create(body),
    onSuccess: invalidate,
    meta: { successMessage: '仓库已添加' },
  })
}

export function useUpdateRepository() {
  const invalidate = useInvalidateRepositories()
  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: UpdateRepositoryRequest }) =>
      repositoryApi.update(id, body),
    onSuccess: invalidate,
    meta: { successMessage: '仓库已更新' },
  })
}

export function useDeleteRepository() {
  const invalidate = useInvalidateRepositories()
  return useMutation({
    mutationFn: (id: number) => repositoryApi.remove(id),
    onSuccess: invalidate,
    meta: { successMessage: '仓库已删除' },
  })
}
