import { queryOptions, useMutation, useQueryClient } from '@tanstack/react-query'

import { modelConfigApi } from './api'
import type { UpdateModelConfigRequest } from './types'

export const modelConfigKeys = {
  all: ['model-configs'] as const,
  list: () => [...modelConfigKeys.all, 'list'] as const,
}

export const modelConfigQueries = {
  list: () =>
    queryOptions({
      queryKey: modelConfigKeys.list(),
      queryFn: ({ signal }) => modelConfigApi.list(signal),
    }),
}

/**
 * Every mutation invalidates the whole module: toggling `isDefault` on one config changes the
 * others too. Returning the promise keeps the mutation pending until the list has refetched.
 */
function useInvalidateModelConfigs() {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: modelConfigKeys.all })
}

export function useCreateModelConfig() {
  const invalidate = useInvalidateModelConfigs()
  return useMutation({
    mutationFn: modelConfigApi.create,
    onSuccess: invalidate,
    meta: { successMessage: '模型配置已创建' },
  })
}

export function useUpdateModelConfig() {
  const invalidate = useInvalidateModelConfigs()
  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: UpdateModelConfigRequest }) =>
      modelConfigApi.update(id, body),
    onSuccess: invalidate,
    meta: { successMessage: '模型配置已更新' },
  })
}

export function useDeleteModelConfig() {
  const invalidate = useInvalidateModelConfigs()
  return useMutation({
    mutationFn: modelConfigApi.remove,
    onSuccess: invalidate,
    meta: { successMessage: '模型配置已删除' },
  })
}
