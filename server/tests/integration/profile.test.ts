import request from 'supertest'
import { beforeEach, describe, expect, it } from 'vitest'
import { createApp } from '../../src/app.js'
import { signAccessToken } from '../../src/lib/jwt.js'
import { prisma } from '../../src/lib/prisma.js'
import { createUser } from '../factories/user.js'
import { resetDb } from '../helpers/resetDb.js'

const app = createApp()

let customerToken: string
let customerId: number
let otherToken: string

const addressBody = {
  recipientName: 'Nguyễn Văn An',
  phone: '0901234567',
  line: '12 Nguyễn Trãi, P. Bến Thành, Q.1, TP.HCM',
}

beforeEach(async () => {
  await resetDb()
  const customer = await createUser({ email: 'an@example.com' })
  const other = await createUser({ email: 'binh@example.com' })
  customerId = customer.id
  customerToken = signAccessToken(customer.id, customer.role)
  otherToken = signAccessToken(other.id, other.role)
})

function auth(token: string) {
  return { Authorization: `Bearer ${token}` }
}

async function createAddress(
  token: string,
  body: Record<string, unknown> = {},
) {
  return request(app)
    .post('/api/v1/me/addresses')
    .set(auth(token))
    .send({ ...addressBody, ...body })
}

async function listAddresses(token: string) {
  const response = await request(app).get('/api/v1/me/addresses').set(auth(token))
  expect(response.status).toBe(200)
  return response.body.items as Array<{
    id: number
    recipientName: string
    phone: string
    line: string
    isDefault: boolean
  }>
}

describe('PATCH /me', () => {
  it('updates the allowed fields and GET /me reflects them', async () => {
    const response = await request(app)
      .patch('/api/v1/me')
      .set(auth(customerToken))
      .send({ fullName: 'Trần Thị Bình', phone: '0912345678' })

    expect(response.status).toBe(200)
    expect(response.body).toMatchObject({
      id: customerId,
      email: 'an@example.com',
      fullName: 'Trần Thị Bình',
      phone: '0912345678',
    })

    const me = await request(app).get('/api/v1/me').set(auth(customerToken))
    expect(me.status).toBe(200)
    expect(me.body.fullName).toBe('Trần Thị Bình')
    expect(me.body.phone).toBe('0912345678')
  })

  it('clears the phone with null and keeps the email untouched', async () => {
    const response = await request(app)
      .patch('/api/v1/me')
      .set(auth(customerToken))
      .send({ phone: null })

    expect(response.status).toBe(200)
    expect(response.body.phone).toBeNull()
    expect(response.body.email).toBe('an@example.com')
  })

  it('rejects an empty body, an unknown field and unauthenticated access', async () => {
    const empty = await request(app).patch('/api/v1/me').set(auth(customerToken)).send({})
    expect(empty.status).toBe(400)
    expect(empty.body.error.code).toBe('VALIDATION_FAILED')

    const extra = await request(app)
      .patch('/api/v1/me')
      .set(auth(customerToken))
      .send({ fullName: 'Hợp lệ', email: 'khac@example.com', role: 'ADMIN' })
    expect(extra.status).toBe(400)
    expect(extra.body.error.code).toBe('VALIDATION_FAILED')

    const anonymous = await request(app).patch('/api/v1/me').send({ fullName: 'Khách' })
    expect(anonymous.status).toBe(401)
    expect(anonymous.body.error.code).toBe('UNAUTHORIZED')
  })
})

describe('GET /me/addresses', () => {
  it('returns the caller addresses with the default first and newest next', async () => {
    const first = await createAddress(customerToken)
    const second = await createAddress(customerToken, { line: '99 Lê Lợi, Q.1' })
    const third = await createAddress(customerToken, { line: '1 Hai Bà Trưng, Q.3' })
    await createAddress(otherToken, { line: 'Địa chỉ của người khác' })

    const items = await listAddresses(customerToken)

    expect(items.map((item) => item.id)).toEqual([first.body.id, third.body.id, second.body.id])
    expect(items[0].isDefault).toBe(true)
    expect(items.filter((item) => item.isDefault)).toHaveLength(1)
  })
})

describe('POST /me/addresses', () => {
  it('makes the first address the default even when isDefault is omitted or false', async () => {
    const first = await createAddress(customerToken, { isDefault: false })
    expect(first.status).toBe(201)
    expect(first.body.isDefault).toBe(true)

    const second = await createAddress(customerToken, { line: 'Địa chỉ thứ hai' })
    expect(second.status).toBe(201)
    expect(second.body.isDefault).toBe(false)
  })

  it('clears the previous default in the same request when isDefault is true', async () => {
    await createAddress(customerToken)
    const promoted = await createAddress(customerToken, {
      line: 'Địa chỉ mới',
      isDefault: true,
    })

    const items = await listAddresses(customerToken)
    expect(items.filter((item) => item.isDefault).map((item) => item.id)).toEqual([
      promoted.body.id,
    ])
  })

  it('rejects invalid fields and unknown keys', async () => {
    const missing = await createAddress(customerToken, { recipientName: '' })
    expect(missing.status).toBe(400)
    expect(missing.body.error.code).toBe('VALIDATION_FAILED')

    const tooLong = await createAddress(customerToken, { line: 'x'.repeat(256) })
    expect(tooLong.status).toBe(400)

    const extra = await createAddress(customerToken, { userId: 2 })
    expect(extra.status).toBe(400)
  })
})

describe('PATCH /me/addresses/:id', () => {
  it('updates fields and moves the default in one transaction', async () => {
    const first = await createAddress(customerToken)
    const second = await createAddress(customerToken, { line: 'Địa chỉ thứ hai' })

    const updated = await request(app)
      .patch(`/api/v1/me/addresses/${second.body.id}`)
      .set(auth(customerToken))
      .send({ recipientName: 'Nguyễn Văn Bình', isDefault: true })

    expect(updated.status).toBe(200)
    expect(updated.body).toMatchObject({
      id: second.body.id,
      recipientName: 'Nguyễn Văn Bình',
      isDefault: true,
    })

    const items = await listAddresses(customerToken)
    expect(items.find((item) => item.id === first.body.id)?.isDefault).toBe(false)
    expect(items.find((item) => item.id === second.body.id)?.isDefault).toBe(true)
  })

  it('rejects isDefault false and an empty patch', async () => {
    const address = await createAddress(customerToken)

    const unset = await request(app)
      .patch(`/api/v1/me/addresses/${address.body.id}`)
      .set(auth(customerToken))
      .send({ isDefault: false })
    expect(unset.status).toBe(400)
    expect(unset.body.error.code).toBe('VALIDATION_FAILED')
    expect(unset.body.error.details).toContainEqual({
      field: 'isDefault',
      message: expect.any(String),
    })

    const empty = await request(app)
      .patch(`/api/v1/me/addresses/${address.body.id}`)
      .set(auth(customerToken))
      .send({})
    expect(empty.status).toBe(400)
  })

  it('returns 404 for another customer address', async () => {
    const otherAddress = await createAddress(otherToken)

    const response = await request(app)
      .patch(`/api/v1/me/addresses/${otherAddress.body.id}`)
      .set(auth(customerToken))
      .send({ line: 'Cố tình sửa' })

    expect(response.status).toBe(404)
    expect(response.body.error.code).toBe('NOT_FOUND')
    const stored = await prisma.address.findUniqueOrThrow({
      where: { id: otherAddress.body.id },
    })
    expect(stored.line).toBe(addressBody.line)
  })
})

describe('DELETE /me/addresses/:id', () => {
  it('promotes the oldest remaining address when the default is deleted', async () => {
    const oldest = await createAddress(customerToken)
    const middle = await createAddress(customerToken, { line: 'Địa chỉ giữa' })
    const newest = await createAddress(customerToken, { line: 'Địa chỉ mới nhất' })

    const promoted = await request(app)
      .patch(`/api/v1/me/addresses/${newest.body.id}`)
      .set(auth(customerToken))
      .send({ isDefault: true })
    expect(promoted.status).toBe(200)

    const deleted = await request(app)
      .delete(`/api/v1/me/addresses/${newest.body.id}`)
      .set(auth(customerToken))
    expect(deleted.status).toBe(204)

    const items = await listAddresses(customerToken)
    expect(items.map((item) => item.id)).toEqual([oldest.body.id, middle.body.id])
    expect(items.find((item) => item.id === oldest.body.id)?.isDefault).toBe(true)
    expect(items.filter((item) => item.isDefault)).toHaveLength(1)
  })

  it('returns 404 for another customer address and leaves it intact', async () => {
    const otherAddress = await createAddress(otherToken)

    const response = await request(app)
      .delete(`/api/v1/me/addresses/${otherAddress.body.id}`)
      .set(auth(customerToken))

    expect(response.status).toBe(404)
    expect(response.body.error.code).toBe('NOT_FOUND')
    expect(await prisma.address.count({ where: { id: otherAddress.body.id } })).toBe(1)
  })
})
