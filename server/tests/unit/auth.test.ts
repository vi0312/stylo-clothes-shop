import { describe, expect, it } from 'vitest'
import { signAccessToken, verifyAccessToken } from '../../src/lib/jwt.js'
import { hashPassword, verifyPassword } from '../../src/lib/password.js'
import { generateRefreshToken, hashRefreshToken } from '../../src/lib/refreshToken.js'
import { AppError } from '../../src/utils/errors.js'

function expectUnauthorized(run: () => unknown): void {
  try {
    run()
  } catch (error) {
    expect(error).toBeInstanceOf(AppError)
    expect((error as AppError).status).toBe(401)
    expect((error as AppError).code).toBe('UNAUTHORIZED')
    return
  }
  throw new Error('expected verifyAccessToken to reject the token')
}

describe('access tokens', () => {
  it('round-trips the subject and role', () => {
    const token = signAccessToken(42, 'ADMIN')

    expect(verifyAccessToken(token)).toEqual({ sub: 42, role: 'ADMIN' })
  })

  it('rejects an expired token', () => {
    expectUnauthorized(() => verifyAccessToken(signAccessToken(42, 'CUSTOMER', -1)))
  })

  it('rejects a tampered payload or signature and a malformed token', () => {
    const token = signAccessToken(42, 'CUSTOMER')
    const [header, payload, signature] = token.split('.') as [string, string, string]
    const tamperedPayload = `${payload.slice(0, -1)}${payload.endsWith('A') ? 'B' : 'A'}`

    expectUnauthorized(() => verifyAccessToken(`${header}.${tamperedPayload}.${signature}`))
    expectUnauthorized(() => verifyAccessToken(`${header}.${payload}.${signature.slice(0, -1)}`))
    expectUnauthorized(() => verifyAccessToken('not-a-token'))
    expectUnauthorized(() => verifyAccessToken(`${header}.${payload}`))
  })
})

describe('refresh tokens', () => {
  it('generates 32-byte tokens and stores only a stable SHA-256 hash', () => {
    const first = generateRefreshToken()
    const second = generateRefreshToken()

    expect(first).toMatch(/^[0-9a-f]{64}$/)
    expect(second).not.toBe(first)
    expect(hashRefreshToken(first)).toMatch(/^[0-9a-f]{64}$/)
    expect(hashRefreshToken(first)).toBe(hashRefreshToken(first))
    expect(hashRefreshToken(first)).not.toBe(hashRefreshToken(second))
  })
})

describe('passwords', () => {
  it('hashes with argon2id and verifies without storing the plain text', async () => {
    const hash = await hashPassword('matkhau123')

    expect(hash).toMatch(/^\$argon2id\$/)
    expect(hash).not.toContain('matkhau123')
    await expect(verifyPassword(hash, 'matkhau123')).resolves.toBe(true)
    await expect(verifyPassword(hash, 'wrong-password')).resolves.toBe(false)
    await expect(verifyPassword('not-a-hash', 'matkhau123')).resolves.toBe(false)
  })
})
