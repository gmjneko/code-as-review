/**
 * Accepts only same-origin, path-absolute targets for the post-login redirect, so the `redirect`
 * query parameter cannot be used as an open redirect (`//evil.com`, `https://…`, `/\evil.com`).
 */
export function sanitizeRedirect(target: unknown, fallback = '/'): string {
  if (typeof target !== 'string' || !target.startsWith('/')) return fallback
  if (target.startsWith('//') || target.startsWith('/\\')) return fallback
  return target
}
