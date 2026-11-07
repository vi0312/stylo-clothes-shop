import { describe, expect, it } from 'vitest'
import { ATTEMPT_TTL_MS, isAttemptExpired, txnRef } from '../../src/modules/payments/payments.logic.js'

describe('transaction reference', () => {
  it('is STY + the UTC second + three random digits, the shape erd.md documents', () => {
    // `api.md` pairs `STY261104032000123` with `2026-11-04T03:20:00.000Z`: the stamp is UTC.
    expect(txnRef(new Date('2026-11-04T03:25:30.000Z'), () => 0)).toBe('STY261104032530000')
    expect(txnRef(new Date('2026-11-04T03:25:30.000Z'), (max) => max - 1)).toBe(
      'STY261104032530999',
    )
  })

  it('stays alphanumeric and unique enough to survive a gateway round-trip', () => {
    const now = new Date('2026-12-19T23:59:59.000Z')
    expect(txnRef(now)).toMatch(/^STY\d{15}$/)
    expect(new Set(Array.from({ length: 20 }, () => txnRef(now))).size).toBeGreaterThan(1)
  })
})

describe('attempt expiry', () => {
  const created = new Date('2026-11-04T03:20:00.000Z')

  it('reuses an attempt younger than fifteen minutes', () => {
    expect(isAttemptExpired(created, new Date(created.getTime() + ATTEMPT_TTL_MS - 1))).toBe(false)
  })

  it('expires it at exactly fifteen minutes', () => {
    expect(isAttemptExpired(created, new Date(created.getTime() + ATTEMPT_TTL_MS))).toBe(true)
  })
})
