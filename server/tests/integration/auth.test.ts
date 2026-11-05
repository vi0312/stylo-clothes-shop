import express from 'express'
import request from 'supertest'
import { beforeEach, describe, expect, it } from 'vitest'
import { createApp } from '../../src/app.js'
import { signAccessToken } from '../../src/lib/jwt.js'
import { hashRefreshToken } from '../../src/lib/refreshToken.js'
import { prisma } from '../../src/lib/prisma.js'
import { errorHandler } from '../../src/middleware/error.js'
import { requireAuth } from '../../src/middleware/auth.js'
import { requireRole } from '../../src/middleware/requireRole.js'
import { resetDb } from '../helpers/resetDb.js'
import { createUser } from '../factories/user.js'

const app = createApp()

const registerBody = {
  email: 'An@Example.com',
  password: 'matkhau123',
  fullName: 'Nguyễn Văn An',
  phone: '0901234567',
}

beforeEach(async () => {
  await resetDb()
})

async function register(overrides: Partial<typeof registerBody> = {}) {
  return request(app)
    .post('/api/v1/auth/register')
    .send({ ...registerBody, ...overrides })
}

describe('POST /auth/register', () => {
  it('normalizes the email, hashes the password and never returns the hash', async () => {
    const response = await register()

    expect(response.status).toBe(201)
    expect(response.body).toEqual({
      accessToken: expect.any(String),
      refreshToken: expect.stringMatching(/^[0-9a-f]{64}$/),
      user: {
        id: expect.any(Number),
        email: 'an@example.com',
        fullName: 'Nguyễn Văn An',
        phone: '0901234567',
        role: 'CUSTOMER',
        status: 'ACTIVE',
        createdAt: expect.stringMatching(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/),
        updatedAt: expect.stringMatching(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/),
      },
    })
    expect(response.text).not.toContain('passwordHash')

    const stored = await prisma.user.findUniqueOrThrow({ where: { email: 'an@example.com' } })
    expect(stored.passwordHash).toMatch(/^\$argon2id\$/)
    expect(stored.passwordHash).not.toContain('matkhau123')
  })

  it('rejects a duplicate email case-insensitively', async () => {
    await register()

    const response = await register({ email: 'AN@EXAMPLE.COM' })

    expect(response.status).toBe(409)
    expect(response.body.error.code).toBe('EMAIL_TAKEN')
  })

  it('rejects an invalid password and an unknown body field', async () => {
    const shortPassword = await register({ password: 'short' })
    expect(shortPassword.status).toBe(400)
    expect(shortPassword.body.error.code).toBe('VALIDATION_FAILED')
    expect(shortPassword.body.error.details).toContainEqual({
      field: 'password',
      message: expect.any(String),
    })

    const extraField = await request(app)
      .post('/api/v1/auth/register')
      .send({ ...registerBody, role: 'ADMIN' })
    expect(extraField.status).toBe(400)
    expect(extraField.body.error.code).toBe('VALIDATION_FAILED')
  })
})

describe('POST /auth/login', () => {
  it('returns the same 401 for a wrong email and a wrong password', async () => {
    await register()

    const wrongEmail = await request(app)
      .post('/api/v1/auth/login')
      .send({ email: 'nobody@example.com', password: 'matkhau123' })
    const wrongPassword = await request(app)
      .post('/api/v1/auth/login')
      .send({ email: 'an@example.com', password: 'wrong-password' })

    expect(wrongEmail.status).toBe(401)
    expect(wrongPassword.status).toBe(401)
    expect(wrongEmail.body).toEqual(wrongPassword.body)
    expect(wrongEmail.body.error.code).toBe('INVALID_CREDENTIALS')
  })

  it('returns tokens for valid credentials', async () => {
    await register()

    const response = await request(app)
      .post('/api/v1/auth/login')
      .send({ email: 'AN@example.com', password: registerBody.password })

    expect(response.status).toBe(200)
    expect(response.body).toEqual({
      accessToken: expect.any(String),
      refreshToken: expect.stringMatching(/^[0-9a-f]{64}$/),
      user: expect.objectContaining({ email: 'an@example.com' }),
    })
  })

  it('rejects a DISABLED account with ACCOUNT_DISABLED', async () => {
    await createUser({ email: 'blocked@example.com', password: 'matkhau123', status: 'DISABLED' })

    const response = await request(app)
      .post('/api/v1/auth/login')
      .send({ email: 'blocked@example.com', password: 'matkhau123' })

    expect(response.status).toBe(403)
    expect(response.body.error.code).toBe('ACCOUNT_DISABLED')
  })
})

describe('POST /auth/refresh', () => {
  it('rotates the refresh token and rejects the old one', async () => {
    const session = await register()
    const firstToken = session.body.refreshToken as string

    const rotated = await request(app).post('/api/v1/auth/refresh').send({ refreshToken: firstToken })

    expect(rotated.status).toBe(200)
    expect(rotated.body.accessToken).toEqual(expect.any(String))
    expect(rotated.body.refreshToken).toMatch(/^[0-9a-f]{64}$/)
    expect(rotated.body.refreshToken).not.toBe(firstToken)

    const storedOld = await prisma.refreshToken.findUniqueOrThrow({
      where: { tokenHash: hashRefreshToken(firstToken) },
    })
    expect(storedOld.revokedAt).not.toBeNull()
    const storedNew = await prisma.refreshToken.findUnique({
      where: { tokenHash: hashRefreshToken(rotated.body.refreshToken as string) },
    })
    expect(storedNew).not.toBeNull()

    const reused = await request(app).post('/api/v1/auth/refresh').send({ refreshToken: firstToken })
    expect(reused.status).toBe(401)
    expect(reused.body.error.code).toBe('INVALID_TOKEN')

    const next = await request(app)
      .post('/api/v1/auth/refresh')
      .send({ refreshToken: rotated.body.refreshToken })
    expect(next.status).toBe(200)
  })

  it('lets exactly one of two concurrent refreshes win', async () => {
    const session = await register()
    const token = session.body.refreshToken as string

    const [first, second] = await Promise.all([
      request(app).post('/api/v1/auth/refresh').send({ refreshToken: token }),
      request(app).post('/api/v1/auth/refresh').send({ refreshToken: token }),
    ])

    const statuses = [first.status, second.status].sort()
    expect(statuses).toEqual([200, 401])
    const rejected = first.status === 401 ? first : second
    expect(rejected.body.error.code).toBe('INVALID_TOKEN')
  })

  it('rejects unknown and expired tokens with INVALID_TOKEN', async () => {
    const unknown = await request(app)
      .post('/api/v1/auth/refresh')
      .send({ refreshToken: 'a'.repeat(64) })
    expect(unknown.status).toBe(401)
    expect(unknown.body.error.code).toBe('INVALID_TOKEN')

    const user = await createUser()
    const expired = 'b'.repeat(64)
    await prisma.refreshToken.create({
      data: {
        userId: user.id,
        tokenHash: hashRefreshToken(expired),
        expiresAt: new Date(Date.now() - 1000),
      },
    })
    const expiredResponse = await request(app)
      .post('/api/v1/auth/refresh')
      .send({ refreshToken: expired })
    expect(expiredResponse.status).toBe(401)
    expect(expiredResponse.body.error.code).toBe('INVALID_TOKEN')
  })

  it('rejects a DISABLED account with ACCOUNT_DISABLED', async () => {
    const session = await register()
    await prisma.user.update({
      where: { email: 'an@example.com' },
      data: { status: 'DISABLED' },
    })

    const response = await request(app)
      .post('/api/v1/auth/refresh')
      .send({ refreshToken: session.body.refreshToken })

    expect(response.status).toBe(403)
    expect(response.body.error.code).toBe('ACCOUNT_DISABLED')
  })
})

describe('POST /auth/logout', () => {
  it('revokes the refresh token and stays 204 when repeated', async () => {
    const session = await register()
    const token = session.body.refreshToken as string

    const logout = await request(app).post('/api/v1/auth/logout').send({ refreshToken: token })
    expect(logout.status).toBe(204)

    const refresh = await request(app).post('/api/v1/auth/refresh').send({ refreshToken: token })
    expect(refresh.status).toBe(401)
    expect(refresh.body.error.code).toBe('INVALID_TOKEN')

    const again = await request(app).post('/api/v1/auth/logout').send({ refreshToken: token })
    expect(again.status).toBe(204)
  })
})

describe('GET /me', () => {
  it('returns the current user without the password hash', async () => {
    const session = await register()

    const response = await request(app)
      .get('/api/v1/me')
      .set('Authorization', `Bearer ${session.body.accessToken}`)

    expect(response.status).toBe(200)
    expect(response.body).toEqual(session.body.user)
    expect(response.text).not.toContain('passwordHash')
  })

  it('rejects a missing, malformed or expired access token with UNAUTHORIZED', async () => {
    const missing = await request(app).get('/api/v1/me')
    expect(missing.status).toBe(401)
    expect(missing.body.error.code).toBe('UNAUTHORIZED')

    const malformed = await request(app).get('/api/v1/me').set('Authorization', 'Bearer nope')
    expect(malformed.status).toBe(401)
    expect(malformed.body.error.code).toBe('UNAUTHORIZED')

    const user = await createUser()
    const expired = signAccessToken(user.id, user.role, -1)
    const expiredResponse = await request(app)
      .get('/api/v1/me')
      .set('Authorization', `Bearer ${expired}`)
    expect(expiredResponse.status).toBe(401)
    expect(expiredResponse.body.error.code).toBe('UNAUTHORIZED')
  })

  it('rejects a DISABLED account on the next authenticated request', async () => {
    const session = await register()
    await prisma.user.update({
      where: { email: 'an@example.com' },
      data: { status: 'DISABLED' },
    })

    const response = await request(app)
      .get('/api/v1/me')
      .set('Authorization', `Bearer ${session.body.accessToken}`)

    expect(response.status).toBe(403)
    expect(response.body.error.code).toBe('ACCOUNT_DISABLED')
  })
})

describe('role guard', () => {
  /**
   * Admin routers arrive in later tickets; this mounts the guard the same way they
   * will so the 401/403 contract is verified now.
   */
  const guardedApp = express()
  guardedApp.get(
    '/admin/probe',
    requireAuth,
    requireRole('ADMIN'),
    (_request, response) => {
      response.json({ ok: true })
    },
  )
  guardedApp.use(errorHandler)

  it('allows an admin, forbids a customer and rejects a missing token', async () => {
    const admin = await createUser({ role: 'ADMIN' })
    const customer = await createUser({ role: 'CUSTOMER' })

    const allowed = await request(guardedApp)
      .get('/admin/probe')
      .set('Authorization', `Bearer ${signAccessToken(admin.id, admin.role)}`)
    expect(allowed.status).toBe(200)

    const forbidden = await request(guardedApp)
      .get('/admin/probe')
      .set('Authorization', `Bearer ${signAccessToken(customer.id, customer.role)}`)
    expect(forbidden.status).toBe(403)
    expect(forbidden.body.error.code).toBe('FORBIDDEN')

    const missing = await request(guardedApp).get('/admin/probe')
    expect(missing.status).toBe(401)
    expect(missing.body.error.code).toBe('UNAUTHORIZED')
  })
})
