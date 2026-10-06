import { z } from 'zod'

export const REVIEW_LIST_DEFAULTS = { page: 1, size: 20 } as const

/**
 * URL search params of the review list. Invalid values fall back to the defaults instead of
 * failing the route, and defaults are stripped from the URL by the route.
 */
export const reviewListSearchSchema = z.object({
  page: z.number().int().min(1).default(REVIEW_LIST_DEFAULTS.page).catch(REVIEW_LIST_DEFAULTS.page),
  size: z
    .number()
    .int()
    .min(1)
    .max(100)
    .default(REVIEW_LIST_DEFAULTS.size)
    .catch(REVIEW_LIST_DEFAULTS.size),
  repositoryId: z.number().int().positive().optional().catch(undefined),
})
