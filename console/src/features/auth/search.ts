import { z } from 'zod'

export const loginSearchSchema = z.object({
  /** Where to go after signing in; validated again by `sanitizeRedirect` before use. */
  redirect: z.string().optional().catch(undefined),
})
