/** Typed access to build-time configuration. Read `import.meta.env` only through this module. */
export const env = {
  apiBaseUrl: (import.meta.env.VITE_API_BASE_URL ?? '/api').replace(/\/+$/, ''),
} as const
