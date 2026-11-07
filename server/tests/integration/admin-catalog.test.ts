import request from 'supertest'
import { beforeEach, describe, expect, it } from 'vitest'
import { createApp } from '../../src/app.js'
import { signAccessToken } from '../../src/lib/jwt.js'
import { resetDb } from '../helpers/resetDb.js'
import { createProduct } from '../factories/catalog.js'
import { createUser } from '../factories/user.js'
import { prisma } from '../../src/lib/prisma.js'

const app = createApp()

let adminToken: string
let customerToken: string

beforeEach(async () => {
  await resetDb()
  const admin = await createUser({ role: 'ADMIN' })
  const customer = await createUser({ role: 'CUSTOMER' })
  adminToken = signAccessToken(admin.id, admin.role)
  customerToken = signAccessToken(customer.id, customer.role)
})

function auth(token: string) {
  return { Authorization: `Bearer ${token}` }
}

describe('role guard on /admin/catalog routes', () => {
  it('rejects a customer with 403 FORBIDDEN', async () => {
    const response = await request(app).get('/api/v1/admin/products').set(auth(customerToken))
    expect(response.status).toBe(403)
    expect(response.body.error.code).toBe('FORBIDDEN')
  })

  it('rejects a missing token with 401 UNAUTHORIZED', async () => {
    const response = await request(app).get('/api/v1/admin/products')
    expect(response.status).toBe(401)
    expect(response.body.error.code).toBe('UNAUTHORIZED')
  })
})

describe('categories and brands CRUD', () => {
  it('creates a category and returns it with productCount 0', async () => {
    const response = await request(app)
      .post('/api/v1/admin/categories')
      .set(auth(adminToken))
      .send({ name: 'Áo khoác' })

    expect(response.status).toBe(201)
    expect(response.body).toMatchObject({
      id: expect.any(Number),
      name: 'Áo khoác',
      status: 'ACTIVE',
      productCount: 0,
    })
  })

  it('rejects a duplicate category name with 409 NAME_TAKEN', async () => {
    await request(app).post('/api/v1/admin/categories').set(auth(adminToken)).send({ name: 'Áo' })
    const response = await request(app)
      .post('/api/v1/admin/categories')
      .set(auth(adminToken))
      .send({ name: 'Áo' })

    expect(response.status).toBe(409)
    expect(response.body.error.code).toBe('NAME_TAKEN')
  })

  it('rejects unknown body fields with VALIDATION_FAILED', async () => {
    const response = await request(app)
      .post('/api/v1/admin/categories')
      .set(auth(adminToken))
      .send({ name: 'Áo', status: 'ACTIVE' })

    expect(response.status).toBe(400)
    expect(response.body.error.code).toBe('VALIDATION_FAILED')
  })

  it('patches name and status and returns an updated productCount', async () => {
    const created = await request(app)
      .post('/api/v1/admin/categories')
      .set(auth(adminToken))
      .send({ name: 'Quần' })

    await createProduct({ name: 'Quần jean', variants: [] })

    const response = await request(app)
      .patch(`/api/v1/admin/categories/${created.body.id}`)
      .set(auth(adminToken))
      .send({ name: 'Quần dài', status: 'INACTIVE' })

    expect(response.status).toBe(200)
    expect(response.body).toMatchObject({
      id: created.body.id,
      name: 'Quần dài',
      status: 'INACTIVE',
      productCount: 0,
    })
  })

  it('blocks a name change onto an existing category with 409 NAME_TAKEN', async () => {
    const first = await request(app)
      .post('/api/v1/admin/categories')
      .set(auth(adminToken))
      .send({ name: 'Giày' })
    await request(app).post('/api/v1/admin/categories').set(auth(adminToken)).send({ name: 'Túi' })

    const response = await request(app)
      .patch(`/api/v1/admin/categories/${first.body.id}`)
      .set(auth(adminToken))
      .send({ name: 'Túi' })

    expect(response.status).toBe(409)
    expect(response.body.error.code).toBe('NAME_TAKEN')
  })

  it('deletes an unused category with 204 and blocks a referenced one with 409 IN_USE', async () => {
    const created = await request(app)
      .post('/api/v1/admin/categories')
      .set(auth(adminToken))
      .send({ name: 'Mũ' })

    const deleted = await request(app)
      .delete(`/api/v1/admin/categories/${created.body.id}`)
      .set(auth(adminToken))
    expect(deleted.status).toBe(204)

    const referenced = await createProduct({ name: 'Sản phẩm còn lại', variants: [] })
    const blocked = await request(app)
      .delete(`/api/v1/admin/categories/${referenced.categoryId}`)
      .set(auth(adminToken))

    expect(blocked.status).toBe(409)
    expect(blocked.body.error.code).toBe('IN_USE')
    expect(blocked.body.error.details).toEqual({ entity: 'category', id: referenced.categoryId })
  })

  it('returns 404 when deleting an unknown category', async () => {
    const response = await request(app).delete('/api/v1/admin/categories/9999').set(auth(adminToken))
    expect(response.status).toBe(404)
    expect(response.body.error.code).toBe('NOT_FOUND')
  })

  it('lists categories with pagination, filters and productCount', async () => {
    await request(app).post('/api/v1/admin/categories').set(auth(adminToken)).send({ name: 'Áo' })
    await request(app).post('/api/v1/admin/categories').set(auth(adminToken)).send({ name: 'Quần' })
    await createProduct({ name: 'P1', variants: [] })

    const response = await request(app)
      .get('/api/v1/admin/categories?q=Quần&sort=name_asc&page=1&limit=10')
      .set(auth(adminToken))

    expect(response.status).toBe(200)
    expect(response.body.total).toBe(1)
    expect(response.body.items).toHaveLength(1)
    expect(response.body.items[0]).toMatchObject({ name: 'Quần', productCount: 0 })
    expect(response.body.page).toBe(1)
    expect(response.body.limit).toBe(10)
  })
})

describe('brands CRUD', () => {
  it('mirrors the category lifecycle for brands', async () => {
    const created = await request(app)
      .post('/api/v1/admin/brands')
      .set(auth(adminToken))
      .send({ name: 'Stylo Brand' })

    expect(created.status).toBe(201)
    expect(created.body).toMatchObject({ name: 'Stylo Brand', status: 'ACTIVE', productCount: 0 })

    const duplicate = await request(app)
      .post('/api/v1/admin/brands')
      .set(auth(adminToken))
      .send({ name: 'Stylo Brand' })
    expect(duplicate.status).toBe(409)
    expect(duplicate.body.error.code).toBe('NAME_TAKEN')

    const patched = await request(app)
      .patch(`/api/v1/admin/brands/${created.body.id}`)
      .set(auth(adminToken))
      .send({ status: 'INACTIVE' })
    expect(patched.status).toBe(200)
    expect(patched.body.status).toBe('INACTIVE')

    const deleted = await request(app)
      .delete(`/api/v1/admin/brands/${created.body.id}`)
      .set(auth(adminToken))
    expect(deleted.status).toBe(204)
  })
})

describe('admin product CRUD', () => {
  it('creates a product without variants and hides it from the storefront', async () => {
    const category = await request(app)
      .post('/api/v1/admin/categories')
      .set(auth(adminToken))
      .send({ name: 'Áo khoác' })
    const brand = await request(app)
      .post('/api/v1/admin/brands')
      .set(auth(adminToken))
      .send({ name: 'Stylo' })

    const response = await request(app)
      .post('/api/v1/admin/products')
      .set(auth(adminToken))
      .send({
        name: 'Áo khoác denim',
        description: 'Denim cotton 100%',
        categoryId: category.body.id,
        brandId: brand.body.id,
        status: 'ACTIVE',
      })

    expect(response.status).toBe(201)
    expect(response.body).toMatchObject({
      name: 'Áo khoác denim',
      status: 'ACTIVE',
      category: { id: category.body.id, name: 'Áo khoác' },
      brand: { id: brand.body.id, name: 'Stylo' },
      minPrice: null,
      maxPrice: null,
      variants: [],
    })

    const summary = await request(app)
      .get('/api/v1/admin/products?q=khoác denim')
      .set(auth(adminToken))
    expect(summary.body.items[0]).toMatchObject({
      id: response.body.id,
      variantCount: 0,
      totalStock: 0,
      minPrice: null,
      maxPrice: null,
    })

    const storefront = await request(app).get('/api/v1/products')
    expect(storefront.body.total).toBe(0)
  })

  it('rejects unknown category or brand with 404 NOT_FOUND', async () => {
    const response = await request(app)
      .post('/api/v1/admin/products')
      .set(auth(adminToken))
      .send({
        name: 'Không tồn tại',
        categoryId: 99999,
        brandId: 99999,
      })

    expect(response.status).toBe(404)
    expect(response.body.error.code).toBe('NOT_FOUND')
  })

  it('lists products with q, status, category and brand filters', async () => {
    await request(app).post('/api/v1/admin/categories').set(auth(adminToken)).send({ name: 'Áo' })
    const brand = await request(app)
      .post('/api/v1/admin/brands')
      .set(auth(adminToken))
      .send({ name: 'Stylo Test' })
    const created = await request(app)
      .post('/api/v1/admin/products')
      .set(auth(adminToken))
      .send({
        name: 'Áo khoác denim test',
        categoryId: 1,
        brandId: brand.body.id,
      })
    expect(created.status).toBe(201)

    const byName = await request(app)
      .get(`/api/v1/admin/products?q=${encodeURIComponent('khoác denim')}`)
      .set(auth(adminToken))
    expect(byName.status).toBe(200)
    expect(byName.body.total).toBe(1)
    expect(byName.body.items[0].id).toBe(created.body.id)

    const byStatus = await request(app)
      .get('/api/v1/admin/products?status=INACTIVE')
      .set(auth(adminToken))
    expect(byStatus.status).toBe(200)
    expect(byStatus.body.total).toBe(0)

    const bySearchableStatus = await request(app)
      .get('/api/v1/admin/products?status=ACTIVE&sort=oldest&page=1&limit=100')
      .set(auth(adminToken))
    expect(bySearchableStatus.status).toBe(200)
    expect(bySearchableStatus.body.items.length).toBeGreaterThanOrEqual(1)

    const byBrand = await request(app)
      .get(`/api/v1/admin/products?brandId=${brand.body.id}`)
      .set(auth(adminToken))
    expect(byBrand.body.total).toBe(1)

    const unknownSort = await request(app)
      .get('/api/v1/admin/products?sort=price_asc')
      .set(auth(adminToken))
    expect(unknownSort.status).toBe(400)
    expect(unknownSort.body.error.code).toBe('VALIDATION_FAILED')
  })

  it('summarises variantCount, totalStock and the full price range', async () => {
    const created = await request(app)
      .post('/api/v1/admin/products')
      .set(auth(adminToken))
      .send(await validProductBody())

    await createVariant(created.body.id, { sku: 'SUM-1-M', price: 150_000 })
    await createVariant(created.body.id, { sku: 'SUM-1-L', price: 250_000 })

    const summary = await request(app)
      .get(`/api/v1/admin/products?q=${encodeURIComponent(created.body.name)}`)
      .set(auth(adminToken))

    expect(summary.status).toBe(200)
    expect(summary.body.items[0]).toMatchObject({
      id: created.body.id,
      variantCount: 2,
      totalStock: 0,
      minPrice: 150_000,
      maxPrice: 250_000,
    })

    const detail = await request(app)
      .get(`/api/v1/admin/products/${created.body.id}`)
      .set(auth(adminToken))

    expect(detail.status).toBe(200)
    expect(detail.body.variants).toHaveLength(2)
    expect(detail.body.variants[0].stock).toBe(0)
    expect(detail.body.minPrice).toBe(150_000)
    expect(detail.body.maxPrice).toBe(250_000)
  })

  it('patches fields and hides the product from the storefront when INACTIVE', async () => {
    const created = await request(app)
      .post('/api/v1/admin/products')
      .set(auth(adminToken))
      .send(await validProductBody())

    await createVariant(created.body.id, { sku: 'HIDE-1' })

    const visible = await request(app).get('/api/v1/products')
    expect(visible.body.total).toBe(0) // no stock yet

    const response = await request(app)
      .patch(`/api/v1/admin/products/${created.body.id}`)
      .set(auth(adminToken))
      .send({ name: 'Tên mới', description: null, status: 'INACTIVE' })

    expect(response.status).toBe(200)
    expect(response.body).toMatchObject({ name: 'Tên mới', description: null, status: 'INACTIVE' })

    const inactiveStorefront = await request(app).get('/api/v1/products')
    expect(inactiveStorefront.body.items.some((p: { id: number }) => p.id === created.body.id)).toBe(
      false,
    )
  })

  it('blocks deleting a product referenced by an order with 409 IN_USE', async () => {
    const product = await createProduct({
      name: 'Đã bán',
      variants: [{ size: 'M', color: 'Đen', price: 100_000, stock: 5 }],
    })
    const variant = product.variants[0]
    const customer = await createUser()
    const order = await prisma.order.create({
      data: {
        code: 'DH000001',
        userId: customer.id,
        clientOrderId: 'c3018ba4-248d-4d02-9d01-61f7e93a29cf',
        paymentMethod: 'COD',
        recipientName: 'Khách',
        recipientPhone: '0900000001',
        shippingAddress: '1 Đường AB, Q1, TP.HCM',
        subtotal: 100_000,
        shippingFee: 30_000,
        total: 130_000,
        items: {
          create: {
            productId: product.id,
            variantId: variant.id,
            productName: product.name,
            variantSku: variant.sku,
            size: 'M',
            color: 'Đen',
            unitPrice: 100_000,
            quantity: 1,
          },
        },
      },
    })
    await prisma.stockMovement.create({
      data: {
        variantId: variant.id,
        delta: -1,
        reason: 'SALE',
        refType: 'ORDER',
        refId: order.id,
      },
    })

    const response = await request(app)
      .delete(`/api/v1/admin/products/${product.id}`)
      .set(auth(adminToken))

    expect(response.status).toBe(409)
    expect(response.body.error.code).toBe('IN_USE')
    expect(response.body.error.details).toEqual({ entity: 'product', id: product.id })

    const stillThere = await prisma.product.findUnique({ where: { id: product.id } })
    expect(stillThere).not.toBeNull()
  })

  it('deletes an unreferenced product with 204', async () => {
    const created = await request(app)
      .post('/api/v1/admin/products')
      .set(auth(adminToken))
      .send(await validProductBody())

    const response = await request(app)
      .delete(`/api/v1/admin/products/${created.body.id}`)
      .set(auth(adminToken))

    expect(response.status).toBe(204)
    const gone = await prisma.product.findUnique({ where: { id: created.body.id } })
    expect(gone).toBeNull()
  })

  it('returns INACTIVE products on the storefront 404 but admin 200', async () => {
    const product = await createProduct({
      name: 'Sản phẩm ẩn',
      status: 'INACTIVE',
      variants: [{ size: 'M', color: 'Đen', price: 100_000, stock: 5 }],
    })

    const storefront = await request(app).get(`/api/v1/products/${product.id}`)
    expect(storefront.status).toBe(404)

    const admin = await request(app)
      .get(`/api/v1/admin/products/${product.id}`)
      .set(auth(adminToken))
    expect(admin.status).toBe(200)
    expect(admin.body.status).toBe('INACTIVE')
  })
})

async function validProductBody() {
  const category = await request(app)
    .post('/api/v1/admin/categories')
    .set(auth(adminToken))
    .send({ name: `Danh mục helper ${Date.now()}-${Math.random()}` })
  const brand = await request(app)
    .post('/api/v1/admin/brands')
    .set(auth(adminToken))
    .send({ name: `Thương hiệu helper ${Date.now()}-${Math.random()}` })

  return {
    name: `Sản phẩm helper ${Date.now()}-${Math.random()}`,
    description: 'Mô tả helper',
    categoryId: category.body.id as number,
    brandId: brand.body.id as number,
  }
}

async function createVariant(
  productId: number,
  overrides: { sku?: string; size?: string; color?: string; price?: number } = {},
) {
  const tag = Math.random().toString(36).slice(2, 8)
  const response = await request(app)
    .post(`/api/v1/admin/products/${productId}/variants`)
    .set(auth(adminToken))
    .send({
      sku: overrides.sku ?? `VAR-${tag}`,
      size: overrides.size ?? `M-${tag}`,
      color: overrides.color ?? `Đen-${tag}`,
      price: overrides.price ?? 199_000,
    })
  expect(response.status).toBe(201)
  return response.body as { id: number; sku: string; stock: number }
}

describe('variant CRUD', () => {
  it('creates variants with stock 0 and enforces product existence', async () => {
    const created = await request(app)
      .post('/api/v1/admin/products')
      .set(auth(adminToken))
      .send(await validProductBody())

    const variant = await createVariant(created.body.id, { sku: 'NEW-1' })
    expect(variant.stock).toBe(0)

    const notFound = await request(app)
      .post('/api/v1/admin/products/99999/variants')
      .set(auth(adminToken))
      .send({ sku: 'GHOST-1', size: 'M', color: 'Đen', price: 199_000 })
    expect(notFound.status).toBe(404)
    expect(notFound.body.error.code).toBe('NOT_FOUND')
  })

  it('rejects duplicate SKU with 409 SKU_TAKEN', async () => {
    const created = await request(app)
      .post('/api/v1/admin/products')
      .set(auth(adminToken))
      .send(await validProductBody())
    await createVariant(created.body.id, { sku: 'AT-DEN-M' })

    const other = await request(app)
      .post('/api/v1/admin/products')
      .set(auth(adminToken))
      .send(await validProductBody())

    const response = await request(app)
      .post(`/api/v1/admin/products/${other.body.id}/variants`)
      .set(auth(adminToken))
      .send({ sku: 'AT-DEN-M', size: 'L', color: 'Trắng', price: 219_000 })

    expect(response.status).toBe(409)
    expect(response.body.error.code).toBe('SKU_TAKEN')
  })

  it('rejects duplicate (size, color) within a product with 409 VARIANT_EXISTS', async () => {
    const created = await request(app)
      .post('/api/v1/admin/products')
      .set(auth(adminToken))
      .send(await validProductBody())
    await createVariant(created.body.id, { sku: 'TW-1', size: 'M', color: 'Đen' })

    const response = await request(app)
      .post(`/api/v1/admin/products/${created.body.id}/variants`)
      .set(auth(adminToken))
      .send({ sku: 'TW-2', size: 'M', color: 'Đen', price: 199_000 })

    expect(response.status).toBe(409)
    expect(response.body.error.code).toBe('VARIANT_EXISTS')

    const other = await request(app)
      .post('/api/v1/admin/products')
      .set(auth(adminToken))
      .send(await validProductBody())
    const sameSizeOtherProduct = await request(app)
      .post(`/api/v1/admin/products/${other.body.id}/variants`)
      .set(auth(adminToken))
      .send({ sku: 'TW-3', size: 'M', color: 'Đen', price: 199_000 })
    expect(sameSizeOtherProduct.status).toBe(201)
  })

  it('rejects stock in a variant body with 400 VALIDATION_FAILED', async () => {
    const created = await request(app)
      .post('/api/v1/admin/products')
      .set(auth(adminToken))
      .send(await validProductBody())

    const create = await request(app)
      .post(`/api/v1/admin/products/${created.body.id}/variants`)
      .set(auth(adminToken))
      .send({ sku: 'STK-1', size: 'M', color: 'Đen', price: 199_000, stock: 5 })
    expect(create.status).toBe(400)
    expect(create.body.error.code).toBe('VALIDATION_FAILED')

    const variantId = (await createVariant(created.body.id, { sku: 'STK-2' })).id
    const patch = await request(app)
      .patch(`/api/v1/admin/variants/${variantId}`)
      .set(auth(adminToken))
      .send({ stock: 10 })
    expect(patch.status).toBe(400)
    expect(patch.body.error.code).toBe('VALIDATION_FAILED')
  })

  it('patches variant SKU, size, color and price; changing (size,color) to an existing one is 409', async () => {
    const created = await request(app)
      .post('/api/v1/admin/products')
      .set(auth(adminToken))
      .send(await validProductBody())
    const first = await createVariant(created.body.id, { sku: 'PV-1', size: 'M', color: 'Đen' })
    await createVariant(created.body.id, { sku: 'PV-2', size: 'M', color: 'Đỏ' })

    const patched = await request(app)
      .patch(`/api/v1/admin/variants/${first.id}`)
      .set(auth(adminToken))
      .send({ sku: 'PV-1B', price: 250_000 })
    expect(patched.status).toBe(200)
    expect(patched.body).toMatchObject({ id: first.id, sku: 'PV-1B', price: 250_000 })

    const clash = await request(app)
      .patch(`/api/v1/admin/variants/${first.id}`)
      .set(auth(adminToken))
      .send({ color: 'Đỏ' })
    expect(clash.status).toBe(409)
    expect(clash.body.error.code).toBe('VARIANT_EXISTS')
  })

  it('searches variants by SKU or product name for the receipt picker', async () => {
    const product = await createProduct({
      name: 'Áo sơ mi linen',
      variants: [
        { sku: 'ASL-TRANG-L', size: 'L', color: 'Trắng', price: 320_000, stock: 4 },
        { sku: 'ASL-DEN-M', size: 'M', color: 'Đen', price: 320_000, stock: 0 },
      ],
    })
    await createProduct({
      name: 'Áo khoác ẩn',
      status: 'INACTIVE',
      variants: [{ sku: 'AK-AN-M', size: 'M', color: 'Xanh', price: 500_000, stock: 2 }],
    })

    const bySku = await request(app)
      .get('/api/v1/admin/variants?q=ASL-DEN')
      .set(auth(adminToken))
    expect(bySku.status).toBe(200)
    expect(bySku.body.items).toEqual([
      {
        id: product.variants[1].id,
        productId: product.id,
        productName: 'Áo sơ mi linen',
        sku: 'ASL-DEN-M',
        size: 'M',
        color: 'Đen',
        price: 320_000,
        stock: 0,
      },
    ])

    const byName = await request(app)
      .get(`/api/v1/admin/variants?q=${encodeURIComponent('sơ mi')}&limit=10`)
      .set(auth(adminToken))
    expect(byName.body.items.map((item: { sku: string }) => item.sku)).toEqual([
      'ASL-DEN-M',
      'ASL-TRANG-L',
    ])

    const inactiveHit = await request(app)
      .get('/api/v1/admin/variants?q=AK-AN')
      .set(auth(adminToken))
    expect(inactiveHit.body.items).toHaveLength(1)

    const forbidden = await request(app)
      .get('/api/v1/admin/variants?q=ASL')
      .set(auth(customerToken))
    expect(forbidden.status).toBe(403)
  })

  it('blocks deleting a variant referenced by receipts or movements with 409 IN_USE', async () => {
    const product = await createProduct({
      name: 'Sản phẩm để kho',
      variants: [{ size: 'M', color: 'Đen', price: 100_000, stock: 3 }],
    })
    const variantId = product.variants[0].id
    const admin = await createUser({ role: 'ADMIN' })
    const receipt = await prisma.inventoryReceipt.create({
      data: {
        code: 'PN000001',
        supplier: 'Nhà cung cấp test',
        totalCost: 300_000,
        createdBy: admin.id,
        items: {
          create: { variantId, quantity: 3, unitCost: 100_000 },
        },
      },
    })
    await prisma.stockMovement.create({
      data: {
        variantId,
        delta: 3,
        reason: 'RECEIPT',
        refType: 'RECEIPT',
        refId: receipt.id,
        unitCost: 100_000,
      },
    })

    const response = await request(app)
      .delete(`/api/v1/admin/variants/${variantId}`)
      .set(auth(adminToken))

    expect(response.status).toBe(409)
    expect(response.body.error.code).toBe('IN_USE')
    expect(response.body.error.details).toEqual({ entity: 'variant', id: variantId })
  })

  it('deletes an unreferenced variant with 204', async () => {
    const product = await createProduct({
      name: 'Sản phẩm xoá variant',
      variants: [{ size: 'M', color: 'Đen', price: 100_000, stock: 0 }],
    })
    const variantId = product.variants[0].id

    const response = await request(app)
      .delete(`/api/v1/admin/variants/${variantId}`)
      .set(auth(adminToken))

    expect(response.status).toBe(204)
    const gone = await prisma.productVariant.findUnique({ where: { id: variantId } })
    expect(gone).toBeNull()
  })
})
