import request from 'supertest'
import { beforeEach, describe, expect, it } from 'vitest'
import { createApp } from '../../src/app.js'
import type { Mailer, OutgoingMail } from '../../src/lib/mailer.js'
import { prisma } from '../../src/lib/prisma.js'
import { createUser, TEST_PASSWORD } from '../factories/user.js'
import { resetDb } from '../helpers/resetDb.js'

const outbox: OutgoingMail[] = []
const mailer: Mailer = {
  async send(mail) {
    outbox.push(mail)
  },
}

const app = createApp({ mailer })

const EMAIL = 'reset@stylo.test'
const NEW_PASSWORD = 'matkhau456'

function emailedCode(): string {
  const text = outbox.at(-1)?.text ?? ''
  const match = /\b(\d{6})\b/.exec(text)
  if (!match) throw new Error(`no code in mail: ${text}`)
  return match[1]
}

async function forgotPassword(email: string) {
  return request(app).post('/api/v1/auth/forgot-password').send({ email })
}

async function resetPassword(email: string, code: string, password: string) {
  return request(app).post('/api/v1/auth/reset-password').send({ email, code, password })
}

async function login(email: string, password: string) {
  return request(app).post('/api/v1/auth/login').send({ email, password })
}

beforeEach(async () => {
  await resetDb()
  outbox.length = 0
})

describe('POST /auth/forgot-password', () => {
  it('mails a six-digit code and stores only its argon2 hash', async () => {
    const user = await createUser({ email: EMAIL, fullName: 'Nguyễn <b>Văn</b> An' })

    const response = await forgotPassword(EMAIL)

    expect(response.status).toBe(202)
    expect(response.text).toBe('')
    expect(outbox).toHaveLength(1)
    expect(outbox[0].to).toBe(EMAIL)
    expect(outbox[0].subject).toContain('Stylo')

    const code = emailedCode()
    expect(outbox[0].text).toContain(code)
    expect(outbox[0].text).toContain('Nguyễn <b>Văn</b> An')
    expect(outbox[0].html).toContain(code)
    expect(outbox[0].html).toContain('&lt;b&gt;Văn&lt;/b&gt;')
    expect(outbox[0].html).not.toContain('<b>Văn</b>')

    const stored = await prisma.passwordResetToken.findFirstOrThrow({ where: { userId: user.id } })
    expect(stored.codeHash).toMatch(/^\$argon2id\$/)
    expect(stored.codeHash).not.toContain(code)
    expect(stored.attempts).toBe(0)
    expect(stored.consumedAt).toBeNull()
    expect(stored.expiresAt.getTime()).toBeGreaterThan(Date.now())
  })

  it('answers the same 202 for an unknown address and sends nothing', async () => {
    const response = await forgotPassword('nobody@stylo.test')

    expect(response.status).toBe(202)
    expect(response.text).toBe('')
    expect(outbox).toHaveLength(0)
    expect(await prisma.passwordResetToken.count()).toBe(0)
  })

  it('answers the same 202 for a disabled account and sends nothing', async () => {
    await createUser({ email: EMAIL, status: 'DISABLED' })

    const response = await forgotPassword(EMAIL)

    expect(response.status).toBe(202)
    expect(outbox).toHaveLength(0)
    expect(await prisma.passwordResetToken.count()).toBe(0)
  })

  it('rejects a malformed email as a validation failure', async () => {
    const response = await forgotPassword('not-an-email')

    expect(response.status).toBe(400)
    expect(response.body.error.code).toBe('VALIDATION_FAILED')
    expect(response.body.error.details).toContainEqual({
      field: 'email',
      message: expect.any(String),
    })
  })

  it('ignores a second request inside the resend cooldown', async () => {
    await createUser({ email: EMAIL })

    expect((await forgotPassword(EMAIL)).status).toBe(202)
    expect((await forgotPassword(EMAIL)).status).toBe(202)

    expect(outbox).toHaveLength(1)
    expect(await prisma.passwordResetToken.count()).toBe(1)
  })

  it('stops issuing codes once the hourly cap is reached', async () => {
    const user = await createUser({ email: EMAIL })
    const tenMinutesAgo = Date.now() - 10 * 60_000
    for (let index = 0; index < 5; index += 1) {
      await prisma.passwordResetToken.create({
        data: {
          userId: user.id,
          codeHash: '$argon2id$placeholder',
          expiresAt: new Date(tenMinutesAgo + 15 * 60_000),
          consumedAt: new Date(tenMinutesAgo),
          createdAt: new Date(tenMinutesAgo),
        },
      })
    }

    const response = await forgotPassword(EMAIL)

    expect(response.status).toBe(202)
    expect(outbox).toHaveLength(0)
    expect(await prisma.passwordResetToken.count()).toBe(5)
  })

  it('still answers 202 when the transport fails and lets the caller retry at once', async () => {
    await createUser({ email: EMAIL })
    const broken = createApp({
      mailer: {
        async send() {
          throw new Error('smtp is down')
        },
      },
    })

    const failed = await request(broken).post('/api/v1/auth/forgot-password').send({ email: EMAIL })
    expect(failed.status).toBe(202)
    expect(await prisma.passwordResetToken.count()).toBe(0)

    const retried = await forgotPassword(EMAIL)
    expect(retried.status).toBe(202)
    expect(outbox).toHaveLength(1)
  })
})

describe('POST /auth/reset-password', () => {
  it('changes the password, revokes every session and burns the code', async () => {
    await createUser({ email: EMAIL })
    const session = await login(EMAIL, TEST_PASSWORD)
    expect(session.status).toBe(200)

    await forgotPassword(EMAIL)
    const code = emailedCode()

    const response = await resetPassword(EMAIL, code, NEW_PASSWORD)
    expect(response.status).toBe(204)
    expect(response.text).toBe('')

    expect((await login(EMAIL, NEW_PASSWORD)).status).toBe(200)
    expect((await login(EMAIL, TEST_PASSWORD)).status).toBe(401)

    const refresh = await request(app)
      .post('/api/v1/auth/refresh')
      .send({ refreshToken: session.body.refreshToken })
    expect(refresh.status).toBe(401)
    expect(refresh.body.error.code).toBe('INVALID_TOKEN')

    const stored = await prisma.passwordResetToken.findFirstOrThrow()
    expect(stored.consumedAt).not.toBeNull()
    expect((await resetPassword(EMAIL, code, 'matkhau789')).status).toBe(401)
  })

  it('rejects a wrong code without touching the password', async () => {
    const user = await createUser({ email: EMAIL })
    await forgotPassword(EMAIL)
    const wrong = emailedCode() === '000000' ? '111111' : '000000'

    const response = await resetPassword(EMAIL, wrong, NEW_PASSWORD)

    expect(response.status).toBe(401)
    expect(response.body.error.code).toBe('INVALID_RESET_CODE')
    expect((await login(EMAIL, TEST_PASSWORD)).status).toBe(200)

    const stored = await prisma.passwordResetToken.findFirstOrThrow({ where: { userId: user.id } })
    expect(stored.attempts).toBe(1)
    expect(stored.consumedAt).toBeNull()
  })

  it('burns the code after the attempt cap, so the right code no longer works', async () => {
    const user = await createUser({ email: EMAIL })
    await forgotPassword(EMAIL)
    const code = emailedCode()
    const wrong = code === '000000' ? '111111' : '000000'

    for (let attempt = 0; attempt < 5; attempt += 1) {
      expect((await resetPassword(EMAIL, wrong, NEW_PASSWORD)).status).toBe(401)
    }

    expect((await resetPassword(EMAIL, code, NEW_PASSWORD)).status).toBe(401)
    expect((await login(EMAIL, TEST_PASSWORD)).status).toBe(200)

    const stored = await prisma.passwordResetToken.findFirstOrThrow({ where: { userId: user.id } })
    expect(stored.consumedAt).not.toBeNull()
  })

  it('rejects an expired code', async () => {
    await createUser({ email: EMAIL })
    await forgotPassword(EMAIL)
    const code = emailedCode()
    await prisma.passwordResetToken.updateMany({ data: { expiresAt: new Date(Date.now() - 1_000) } })

    const response = await resetPassword(EMAIL, code, NEW_PASSWORD)

    expect(response.status).toBe(401)
    expect(response.body.error.code).toBe('INVALID_RESET_CODE')
  })

  it('answers an unknown address with the same code as a wrong code', async () => {
    const response = await resetPassword('nobody@stylo.test', '123456', NEW_PASSWORD)

    expect(response.status).toBe(401)
    expect(response.body.error.code).toBe('INVALID_RESET_CODE')
  })

  it('accepts only one of two parallel attempts with the same code', async () => {
    await createUser({ email: EMAIL })
    await forgotPassword(EMAIL)
    const code = emailedCode()

    const results = await Promise.all([
      resetPassword(EMAIL, code, NEW_PASSWORD),
      resetPassword(EMAIL, code, NEW_PASSWORD),
    ])

    expect(results.map((result) => result.status).sort()).toEqual([204, 401])
  })

  it('rejects a password that breaks the register policy', async () => {
    await createUser({ email: EMAIL })
    await forgotPassword(EMAIL)

    const response = await resetPassword(EMAIL, emailedCode(), 'short')

    expect(response.status).toBe(400)
    expect(response.body.error.code).toBe('VALIDATION_FAILED')
    expect(response.body.error.details).toContainEqual({
      field: 'password',
      message: expect.any(String),
    })
  })
})
