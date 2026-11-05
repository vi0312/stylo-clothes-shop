import { describe, expect, it } from 'vitest'
import { parseDatabaseUrl, parseEnv } from '../../src/config/env.js'

const minimalEnv = {
  DATABASE_URL: 'mysql://user:secret@localhost:3307/stylo',
  JWT_ACCESS_SECRET: 'test-secret',
} satisfies NodeJS.ProcessEnv

describe('parseEnv', () => {
  it('fills defaults for optional values', () => {
    const env = parseEnv(minimalEnv)

    expect(env.NODE_ENV).toBe('development')
    expect(env.PORT).toBe(8787)
    expect(env.PAYMENT_PROVIDER).toBe('MOCK')
    expect(env.SHIPPING_FEE).toBe(30000)
    expect(env.UPLOAD_DIR).toBe('uploads')
  })

  it('coerces numeric strings', () => {
    const env = parseEnv({ ...minimalEnv, PORT: '8080', SHIPPING_FEE: '45000' })

    expect(env.PORT).toBe(8080)
    expect(env.SHIPPING_FEE).toBe(45000)
  })

  it('rejects a missing DATABASE_URL', () => {
    expect(() => parseEnv({ JWT_ACCESS_SECRET: 'test-secret' })).toThrow(/DATABASE_URL/)
  })

  it('rejects an out-of-range PORT', () => {
    expect(() => parseEnv({ ...minimalEnv, PORT: '70000' })).toThrow(/PORT/)
  })

  it('requires VNPay credentials when the provider is VNPAY', () => {
    expect(() => parseEnv({ ...minimalEnv, PAYMENT_PROVIDER: 'VNPAY' })).toThrow(
      /VNPAY_TMN_CODE/,
    )
  })
})

describe('parseDatabaseUrl', () => {
  it('parses host, port, credentials and database', () => {
    expect(parseDatabaseUrl('mysql://user:secret@db.local:3307/stylo_test')).toEqual({
      host: 'db.local',
      port: 3307,
      user: 'user',
      password: 'secret',
      database: 'stylo_test',
    })
  })

  it('defaults the port to 3306', () => {
    expect(parseDatabaseUrl('mysql://user:secret@localhost/stylo').port).toBe(3306)
  })

  it('decodes percent-encoded credentials', () => {
    expect(parseDatabaseUrl('mysql://us%40er:p%40ss@localhost/stylo').user).toBe('us@er')
  })

  it('rejects a non-MySQL protocol', () => {
    expect(() => parseDatabaseUrl('postgresql://user:secret@localhost/stylo')).toThrow(/mysql/)
  })

  it('rejects a missing database name', () => {
    expect(() => parseDatabaseUrl('mysql://user:secret@localhost/')).toThrow(/database name/)
  })
})
