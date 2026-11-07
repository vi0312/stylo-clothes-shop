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

describe('admin users', () => {
  async function adminSession() {
    const admin = await createUser({
      email: 'admin@example.com',
      fullName: 'Quản Trị Viên',
      phone: '0900000000',
      role: 'ADMIN',
    })
    return { admin, token: signAccessToken(admin.id, admin.role) }
  }

  it('searches by email, full name and phone', async () => {
    const { token } = await adminSession()
    const bin = await createUser({
      email: 'binh@example.com',
      fullName: 'Trần Bình',
      phone: '0912345678',
    })
    await createUser({ email: 'an@example.com', fullName: 'Nguyễn An', phone: '0987654321' })

    const byEmail = await request(app)
      .get('/api/v1/admin/users')
      .query({ q: 'binh@' })
      .set('Authorization', `Bearer ${token}`)
    expect(byEmail.status).toBe(200)
    expect(byEmail.body).toEqual(
      expect.objectContaining({ page: 1, limit: 20, total: 1 }),
    )
    expect(byEmail.body.items).toEqual([
      expect.objectContaining({
        id: bin.id,
        email: 'binh@example.com',
        fullName: 'Trần Bình',
        phone: '0912345678',
        role: 'CUSTOMER',
        status: 'ACTIVE',
      }),
    ])
    expect(byEmail.text).not.toContain('passwordHash')

    const byName = await request(app)
      .get('/api/v1/admin/users')
      .query({ q: 'Bình' })
      .set('Authorization', `Bearer ${token}`)
    expect(byName.body.items.map((user: { id: number }) => user.id)).toEqual([bin.id])

    const byPhone = await request(app)
      .get('/api/v1/admin/users')
      .query({ q: '0987' })
      .set('Authorization', `Bearer ${token}`)
    expect(byPhone.body.items).toHaveLength(1)
    expect(byPhone.body.items[0].email).toBe('an@example.com')
  })

  it('filters by status and role, paginates and sorts by name', async () => {
    const { admin, token } = await adminSession()
    const disabled = await createUser({
      email: 'off@example.com',
      fullName: 'Lê Khoá',
      status: 'DISABLED',
    })
    await createUser({ email: 'zoe@example.com', fullName: 'Zoe' })
    await createUser({ email: 'an@example.com', fullName: 'An' })

    const disabledOnly = await request(app)
      .get('/api/v1/admin/users')
      .query({ status: 'DISABLED' })
      .set('Authorization', `Bearer ${token}`)
    expect(disabledOnly.body.items.map((user: { id: number }) => user.id)).toEqual([
      disabled.id,
    ])

    const adminsOnly = await request(app)
      .get('/api/v1/admin/users')
      .query({ role: 'ADMIN' })
      .set('Authorization', `Bearer ${token}`)
    expect(adminsOnly.body.items.map((user: { id: number }) => user.id)).toEqual([admin.id])

    const firstPage = await request(app)
      .get('/api/v1/admin/users')
      .query({ status: 'ACTIVE', sort: 'name_asc', page: 1, limit: 2 })
      .set('Authorization', `Bearer ${token}`)
    const secondPage = await request(app)
      .get('/api/v1/admin/users')
      .query({ status: 'ACTIVE', sort: 'name_asc', page: 2, limit: 2 })
      .set('Authorization', `Bearer ${token}`)

    expect(firstPage.body).toEqual(expect.objectContaining({ page: 1, limit: 2, total: 3 }))
    expect(secondPage.body).toEqual(expect.objectContaining({ page: 2, limit: 2, total: 3 }))
    expect(firstPage.body.items).toHaveLength(2)
    expect(secondPage.body.items).toHaveLength(1)

    const seen = [...firstPage.body.items, ...secondPage.body.items].map(
      (user: { id: number }) => user.id,
    )
    expect(new Set(seen).size).toBe(3)
  })

  it('disabling revokes refresh tokens and blocks the account immediately', async () => {
    const session = await register()
    const { token } = await adminSession()
    const userId = session.body.user.id as number

    const response = await request(app)
      .patch(`/api/v1/admin/users/${userId}/status`)
      .set('Authorization', `Bearer ${token}`)
      .send({ status: 'DISABLED' })

    expect(response.status).toBe(200)
    expect(response.body).toEqual(
      expect.objectContaining({ id: userId, status: 'DISABLED' }),
    )

    const me = await request(app)
      .get('/api/v1/me')
      .set('Authorization', `Bearer ${session.body.accessToken}`)
    expect(me.status).toBe(403)
    expect(me.body.error.code).toBe('ACCOUNT_DISABLED')

    const refresh = await request(app)
      .post('/api/v1/auth/refresh')
      .send({ refreshToken: session.body.refreshToken })
    expect(refresh.status).toBe(401)
    expect(refresh.body.error.code).toBe('INVALID_TOKEN')

    const login = await request(app)
      .post('/api/v1/auth/login')
      .send({ email: 'an@example.com', password: registerBody.password })
    expect(login.status).toBe(403)
    expect(login.body.error.code).toBe('ACCOUNT_DISABLED')

    const revoked = await prisma.refreshToken.count({
      where: { userId, revokedAt: null },
    })
    expect(revoked).toBe(0)
  })

  it('re-enables a disabled account', async () => {
    const { token } = await adminSession()
    const customer = await createUser({
      email: 'back@example.com',
      status: 'DISABLED',
    })

    const response = await request(app)
      .patch(`/api/v1/admin/users/${customer.id}/status`)
      .set('Authorization', `Bearer ${token}`)
      .send({ status: 'ACTIVE' })
    expect(response.status).toBe(200)
    expect(response.body.status).toBe('ACTIVE')

    const login = await request(app)
      .post('/api/v1/auth/login')
      .send({ email: 'back@example.com', password: 'matkhau123' })
    expect(login.status).toBe(200)
  })

  it('refuses to disable the acting admin', async () => {
    const { admin, token } = await adminSession()

    const response = await request(app)
      .patch(`/api/v1/admin/users/${admin.id}/status`)
      .set('Authorization', `Bearer ${token}`)
      .send({ status: 'DISABLED' })

    expect(response.status).toBe(409)
    expect(response.body.error.code).toBe('CANNOT_DISABLE_SELF')

    const stored = await prisma.user.findUniqueOrThrow({ where: { id: admin.id } })
    expect(stored.status).toBe('ACTIVE')
  })

  it('validates the query and body, and 404s an unknown user', async () => {
    const { token } = await adminSession()

    const unknown = await request(app)
      .patch('/api/v1/admin/users/999999/status')
      .set('Authorization', `Bearer ${token}`)
      .send({ status: 'DISABLED' })
    expect(unknown.status).toBe(404)
    expect(unknown.body.error.code).toBe('NOT_FOUND')

    const badStatus = await request(app)
      .patch('/api/v1/admin/users/1/status')
      .set('Authorization', `Bearer ${token}`)
      .send({ status: 'BANNED' })
    expect(badStatus.status).toBe(400)
    expect(badStatus.body.error.code).toBe('VALIDATION_FAILED')

    const rolePatch = await request(app)
      .patch('/api/v1/admin/users/1/status')
      .set('Authorization', `Bearer ${token}`)
      .send({ status: 'ACTIVE', role: 'ADMIN' })
    expect(rolePatch.status).toBe(400)
    expect(rolePatch.body.error.code).toBe('VALIDATION_FAILED')

    const badSort = await request(app)
      .get('/api/v1/admin/users')
      .query({ sort: 'oldest' })
      .set('Authorization', `Bearer ${token}`)
    expect(badSort.status).toBe(400)
    expect(badSort.body.error.code).toBe('VALIDATION_FAILED')
  })

  it('rejects a customer and a missing token', async () => {
    const customer = await createUser({ role: 'CUSTOMER' })

    const forbidden = await request(app)
      .get('/api/v1/admin/users')
      .set('Authorization', `Bearer ${signAccessToken(customer.id, customer.role)}`)
    expect(forbidden.status).toBe(403)
    expect(forbidden.body.error.code).toBe('FORBIDDEN')

    const missing = await request(app).get('/api/v1/admin/users')
    expect(missing.status).toBe(401)
    expect(missing.body.error.code).toBe('UNAUTHORIZED')
  })
})
