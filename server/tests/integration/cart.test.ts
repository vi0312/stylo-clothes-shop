import request from 'supertest'
import { beforeEach, describe, expect, it } from 'vitest'
import { createApp } from '../../src/app.js'
import { signAccessToken } from '../../src/lib/jwt.js'
import { prisma } from '../../src/lib/prisma.js'
import { createProduct } from '../factories/catalog.js'
import { createUser } from '../factories/user.js'
import { resetDb } from '../helpers/resetDb.js'

const app = createApp()

let customerToken: string
let otherToken: string

beforeEach(async () => {
  await resetDb()
  const customer = await createUser({ email: 'an@example.com' })
  const other = await createUser({ email: 'binh@example.com' })
  customerToken = signAccessToken(customer.id, customer.role)
  otherToken = signAccessToken(other.id, other.role)
})

function auth(token: string) {
  return { Authorization: `Bearer ${token}` }
}

async function addItem(
  token: string,
  body: Record<string, unknown>,
) {
  return request(app).post('/api/v1/cart/items').set(auth(token)).send(body)
}

async function getCart(token: string) {
  const response = await request(app).get('/api/v1/cart').set(auth(token))
  expect(response.status).toBe(200)
  return response.body
}

interface CartItemBody {
  id: number
  variantId: number
  sku: string
  productName: string
  imageUrl: string | null
  size: string
  color: string
  unitPrice: number
  quantity: number
  lineTotal: number
  availableStock: number
  available: boolean
}

interface CartBody {
  id: number | null
  items: CartItemBody[]
  itemCount: number
  subtotal: number
}

describe('cart', () => {
  it('returns the empty cart shape before the first add', async () => {
    const cart = await getCart(customerToken)

    expect(cart).toEqual({ id: null, items: [], itemCount: 0, subtotal: 0 })
  })

  it('requires an access token on every route', async () => {
    expect((await request(app).get('/api/v1/cart')).status).toBe(401)
    expect(
      (await request(app).post('/api/v1/cart/items').send({ variantId: 1, quantity: 1 }))
        .status,
    ).toBe(401)
  })

  it('creates the cart on first add and sums repeated adds of the same variant', async () => {
    const product = await createProduct({
      name: 'Áo thun cotton',
      images: [{ url: '/uploads/products/9a1c.jpg', position: 0 }],
      variants: [{ sku: 'AT-DEN-M', size: 'M', color: 'Đen', price: 199_000, stock: 5 }],
    })
    const variant = product.variants[0]

    const first = await addItem(customerToken, { variantId: variant.id, quantity: 2 })
    expect(first.status).toBe(200)
    expect(first.body).toMatchObject({
      id: expect.any(Number),
      itemCount: 2,
      subtotal: 398_000,
      items: [
        {
          variantId: variant.id,
          sku: 'AT-DEN-M',
          productId: product.id,
          productName: 'Áo thun cotton',
          imageUrl: '/uploads/products/9a1c.jpg',
          size: 'M',
          color: 'Đen',
          unitPrice: 199_000,
          quantity: 2,
          lineTotal: 398_000,
          availableStock: 5,
          available: true,
        },
      ],
    })

    const second = await addItem(customerToken, { variantId: variant.id, quantity: 1 })
    expect(second.status).toBe(200)
    expect(second.body.itemCount).toBe(3)
    expect(second.body.items).toHaveLength(1)
    expect(second.body.items[0].quantity).toBe(3)
    expect(second.body.subtotal).toBe(597_000)
  })

  it('rejects an add beyond stock without clamping', async () => {
    const product = await createProduct({
      variants: [{ sku: 'AT-DEN-M', size: 'M', color: 'Đen', price: 100_000, stock: 2 }],
    })
    const variant = product.variants[0]

    const response = await addItem(customerToken, { variantId: variant.id, quantity: 3 })

    expect(response.status).toBe(409)
    expect(response.body.error.code).toBe('OUT_OF_STOCK')
    expect(response.body.error.details).toEqual([
      { sku: 'AT-DEN-M', requested: 3, available: 2 },
    ])
    expect((await getCart(customerToken)).items).toEqual([])
  })

  it('counts the resulting quantity when adding a variant already in the cart', async () => {
    const product = await createProduct({
      variants: [{ sku: 'QUAN-XANH-L', size: 'L', color: 'Xanh', price: 250_000, stock: 3 }],
    })
    const variant = product.variants[0]
    await addItem(customerToken, { variantId: variant.id, quantity: 2 })

    const response = await addItem(customerToken, { variantId: variant.id, quantity: 2 })

    expect(response.status).toBe(409)
    expect(response.body.error.details).toEqual([
      { sku: 'QUAN-XANH-L', requested: 4, available: 3 },
    ])
    expect((await getCart(customerToken)).items[0].quantity).toBe(2)
  })

  it('rejects a missing variant and an inactive product add', async () => {
    const missing = await addItem(customerToken, { variantId: 999_999, quantity: 1 })
    expect(missing.status).toBe(404)
    expect(missing.body.error.code).toBe('NOT_FOUND')

    const inactive = await createProduct({
      status: 'INACTIVE',
      variants: [{ sku: 'AO-OLD-S', size: 'S', color: 'Be', price: 150_000, stock: 4 }],
    })
    const response = await addItem(customerToken, {
      variantId: inactive.variants[0].id,
      quantity: 1,
    })
    expect(response.status).toBe(409)
    expect(response.body.error.code).toBe('ITEM_UNAVAILABLE')
    expect(response.body.error.details).toEqual([
      { sku: 'AO-OLD-S', reason: 'PRODUCT_INACTIVE' },
    ])
  })

  it('patches quantity, rejects above stock, and returns the whole cart', async () => {
    const product = await createProduct({
      variants: [{ sku: 'AO-TRANG-L', size: 'L', color: 'Trắng', price: 150_000, stock: 4 }],
    })
    const variant = product.variants[0]
    const added = await addItem(customerToken, { variantId: variant.id, quantity: 1 })
    const itemId = added.body.items[0].id

    const patched = await request(app)
      .patch(`/api/v1/cart/items/${itemId}`)
      .set(auth(customerToken))
      .send({ quantity: 3 })
    expect(patched.status).toBe(200)
    expect(patched.body.itemCount).toBe(3)
    expect(patched.body.subtotal).toBe(450_000)

    const tooMany = await request(app)
      .patch(`/api/v1/cart/items/${itemId}`)
      .set(auth(customerToken))
      .send({ quantity: 5 })
    expect(tooMany.status).toBe(409)
    expect(tooMany.body.error.code).toBe('OUT_OF_STOCK')
    expect(tooMany.body.error.details).toEqual([
      { sku: 'AO-TRANG-L', requested: 5, available: 4 },
    ])
    expect((await getCart(customerToken)).items[0].quantity).toBe(3)

    const invalid = await request(app)
      .patch(`/api/v1/cart/items/${itemId}`)
      .set(auth(customerToken))
      .send({ quantity: 0 })
    expect(invalid.status).toBe(400)
    expect(invalid.body.error.code).toBe('VALIDATION_FAILED')
  })

  it('keeps an inactive product line visible with available false and out of the subtotal', async () => {
    const active = await createProduct({
      name: 'Quần jeans',
      variants: [{ sku: 'QJ-XANH-M', size: 'M', color: 'Xanh', price: 300_000, stock: 2 }],
    })
    const inactive = await createProduct({
      name: 'Áo cũ',
      variants: [{ sku: 'AC-DEN-M', size: 'M', color: 'Đen', price: 100_000, stock: 2 }],
    })
    await addItem(customerToken, { variantId: active.variants[0].id, quantity: 1 })
    await addItem(customerToken, { variantId: inactive.variants[0].id, quantity: 2 })
    await prisma.product.update({
      where: { id: inactive.id },
      data: { status: 'INACTIVE' },
    })

    const cart: CartBody = await getCart(customerToken)
    const unavailable = cart.items.find((item) => item.sku === 'AC-DEN-M')

    expect(unavailable).toMatchObject({ available: false, lineTotal: 200_000 })
    expect(cart.itemCount).toBe(3)
    expect(cart.subtotal).toBe(300_000)
  })

  it('deletes a line and rejects another customer’s item id with 404', async () => {
    const product = await createProduct({
      variants: [{ sku: 'VAY-HONG-S', size: 'S', color: 'Hồng', price: 200_000, stock: 5 }],
    })
    const added = await addItem(customerToken, {
      variantId: product.variants[0].id,
      quantity: 1,
    })
    const itemId = added.body.items[0].id

    const foreign = await request(app)
      .delete(`/api/v1/cart/items/${itemId}`)
      .set(auth(otherToken))
    expect(foreign.status).toBe(404)
    expect(foreign.body.error.code).toBe('NOT_FOUND')

    const deleted = await request(app)
      .delete(`/api/v1/cart/items/${itemId}`)
      .set(auth(customerToken))
    expect(deleted.status).toBe(200)
    expect(deleted.body).toEqual({
      id: added.body.id,
      items: [],
      itemCount: 0,
      subtotal: 0,
    })

    const missing = await request(app)
      .delete(`/api/v1/cart/items/${itemId}`)
      .set(auth(customerToken))
    expect(missing.status).toBe(404)
  })

  it('clears the cart and returns the empty shape', async () => {
    const product = await createProduct({
      variants: [{ sku: 'AO-BE-M', size: 'M', color: 'Be', price: 120_000, stock: 5 }],
    })
    await addItem(customerToken, { variantId: product.variants[0].id, quantity: 2 })

    const cleared = await request(app).delete('/api/v1/cart').set(auth(customerToken))
    expect(cleared.status).toBe(200)
    expect(cleared.body).toEqual({ id: null, items: [], itemCount: 0, subtotal: 0 })

    expect(await getCart(customerToken)).toEqual({
      id: null,
      items: [],
      itemCount: 0,
      subtotal: 0,
    })
  })

  it('uses the current variant price, not the price when added', async () => {
    const product = await createProduct({
      variants: [{ sku: 'AO-GIA-M', size: 'M', color: 'Xám', price: 100_000, stock: 5 }],
    })
    const variant = product.variants[0]
    await addItem(customerToken, { variantId: variant.id, quantity: 1 })
    await prisma.productVariant.update({ where: { id: variant.id }, data: { price: 120_000 } })

    const cart: CartBody = await getCart(customerToken)
    expect(cart.items[0]).toMatchObject({ unitPrice: 120_000, lineTotal: 120_000 })
    expect(cart.subtotal).toBe(120_000)
  })
})
