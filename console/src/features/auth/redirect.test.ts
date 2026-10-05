import { describe, expect, it } from 'vitest'

import { sanitizeRedirect } from './redirect'

describe('sanitizeRedirect', () => {
  it.each(['/reviews', '/reviews?page=2', '/model-configs#top'])('keeps %s', (target) => {
    expect(sanitizeRedirect(target)).toBe(target)
  })

  it.each([undefined, 42, '', 'reviews', '//evil.com', '/\\evil.com', 'https://evil.com'])(
    'rejects %s',
    (target) => {
      expect(sanitizeRedirect(target)).toBe('/')
    },
  )
})
