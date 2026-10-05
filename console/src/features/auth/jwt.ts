interface JwtClaims {
  sub?: string
  username?: string
  exp?: number
}

/**
 * Reads the payload of a JWT for display purposes only. The signature is NOT verified; never
 * base an authorization decision on the result.
 */
export function decodeJwtClaims(token: string): JwtClaims {
  const payload = token.split('.')[1]
  if (!payload) return {}
  try {
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/')
    const bytes = Uint8Array.from(atob(base64), (char) => char.charCodeAt(0))
    // oxlint-disable-next-line typescript/no-unsafe-type-assertion -- display-only claims; missing fields are tolerated
    return JSON.parse(new TextDecoder().decode(bytes)) as JwtClaims
  } catch {
    return {}
  }
}
