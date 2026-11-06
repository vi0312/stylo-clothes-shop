import { existsSync, readFileSync, readdirSync } from 'node:fs'
import path from 'node:path'
import request from 'supertest'
import { beforeEach, describe, expect, it } from 'vitest'
import { createApp } from '../../src/app.js'
import { signAccessToken } from '../../src/lib/jwt.js'
import { prisma } from '../../src/lib/prisma.js'
import { MAX_PRODUCT_IMAGE_BYTES } from '../../src/lib/fileStorage.js'
import { MAX_PRODUCT_IMAGES } from '../../src/modules/catalog/catalog.constants.js'
import { createProduct } from '../factories/catalog.js'
import { createUser } from '../factories/user.js'
import { resetDb } from '../helpers/resetDb.js'

const app = createApp()
const fixtures = path.resolve('tests/fixtures')
const uploadRoot = path.resolve(process.env.UPLOAD_DIR!)
const productUploads = path.join(uploadRoot, 'products')

const tinyJpeg = readFileSync(path.join(fixtures, 'tiny.jpg'))
const tinyPng = readFileSync(path.join(fixtures, 'tiny.png'))
const tinyWebp = readFileSync(path.join(fixtures, 'tiny.webp'))
const fakeJpeg = readFileSync(path.join(fixtures, 'fake.jpg'))

let adminToken: string
let productId: number

beforeEach(async () => {
  await resetDb()
  const admin = await createUser({ role: 'ADMIN' })
  adminToken = signAccessToken(admin.id, admin.role)
  productId = (await createProduct()).id
})

function upload(
  filename: string,
  contentType: string,
  buffer: Buffer,
  targetProductId = productId,
) {
  return request(app)
    .post(`/api/v1/admin/products/${targetProductId}/images`)
    .set('Authorization', `Bearer ${adminToken}`)
    .attach('file', buffer, { filename, contentType })
}

function uploadedFilePath(url: string): string {
  return path.join(uploadRoot, url.replace('/uploads/', ''))
}

function fileCount(): number {
  return existsSync(productUploads) ? readdirSync(productUploads).length : 0
}

describe('admin product images', () => {
  it('accepts JPEG, PNG and WebP by magic bytes and assigns positions in upload order', async () => {
    const results = [
      await upload('photo.jpg', 'image/jpeg', tinyJpeg),
      await upload('photo.png', 'image/png', tinyPng),
      await upload('photo.webp', 'image/webp', tinyWebp),
    ]

    for (const [index, response] of results.entries()) {
      expect(response.status).toBe(201)
      expect(response.body).toMatchObject({
        id: expect.any(Number),
        position: index,
        url: expect.stringMatching(/^\/uploads\/products\/[\w-]+\.(jpg|png|webp)$/),
      })
    }

    const served = await request(app).get(results[1]!.body.url)
    expect(served.status).toBe(200)
    expect(served.headers['content-type']).toContain('image/png')

    const detail = await request(app)
      .get(`/api/v1/admin/products/${productId}`)
      .set('Authorization', `Bearer ${adminToken}`)
    expect(detail.body.images.map((image: { position: number }) => image.position)).toEqual([
      0, 1, 2,
    ])

    const storefront = await request(app).get('/api/v1/products?limit=100')
    const card = storefront.body.items.find((item: { id: number }) => item.id === productId)
    expect(card.coverUrl).toBe(results[0]!.body.url)

    expect(await prisma.productImage.count({ where: { productId } })).toBe(3)
  })

  it('rejects a text file with an image extension and image content type', async () => {
    const response = await upload('fake.jpg', 'image/jpeg', fakeJpeg)

    expect(response.status).toBe(400)
    expect(response.body.error.code).toBe('VALIDATION_FAILED')
    expect(response.body.error.details).toEqual([
      { field: 'file', message: 'File must be a JPEG, PNG or WebP image' },
    ])
    expect(await prisma.productImage.count({ where: { productId } })).toBe(0)
  })

  it('rejects the sixth image without keeping an orphan file', async () => {
    for (let index = 0; index < MAX_PRODUCT_IMAGES; index += 1) {
      expect((await upload(`photo-${index}.png`, 'image/png', tinyPng)).status).toBe(201)
    }

    const filesBefore = fileCount()
    const response = await upload('photo-6.png', 'image/png', tinyPng)

    expect(response.status).toBe(400)
    expect(response.body.error.code).toBe('VALIDATION_FAILED')
    expect(await prisma.productImage.count({ where: { productId } })).toBe(MAX_PRODUCT_IMAGES)
    expect(fileCount()).toBe(filesBefore)
  })

  it('rejects an image larger than 5 MB with 413 FILE_TOO_LARGE', async () => {
    const response = await upload(
      'huge.png',
      'image/png',
      Buffer.alloc(MAX_PRODUCT_IMAGE_BYTES + 1),
    )

    expect(response.status).toBe(413)
    expect(response.body.error.code).toBe('FILE_TOO_LARGE')
    expect(await prisma.productImage.count({ where: { productId } })).toBe(0)
  })

  it('serializes concurrent uploads so only one takes the last free slot', async () => {
    for (let index = 0; index < MAX_PRODUCT_IMAGES - 1; index += 1) {
      expect((await upload(`photo-${index}.png`, 'image/png', tinyPng)).status).toBe(201)
    }

    const filesBefore = fileCount()
    const responses = await Promise.all([
      upload('concurrent-a.png', 'image/png', tinyPng),
      upload('concurrent-b.png', 'image/png', tinyPng),
    ])

    expect(responses.map((response) => response.status).sort()).toEqual([201, 400])
    const rejected = responses.find((response) => response.status === 400)!
    expect(rejected.body.error.code).toBe('VALIDATION_FAILED')
    expect(await prisma.productImage.count({ where: { productId } })).toBe(MAX_PRODUCT_IMAGES)
    expect(fileCount()).toBe(filesBefore + 1)
  })

  it('deletes the file, frees the position and keeps the remaining images', async () => {
    const first = await upload('first.png', 'image/png', tinyPng)
    const second = await upload('second.png', 'image/png', tinyPng)
    const third = await upload('third.png', 'image/png', tinyPng)

    const deletedPath = uploadedFilePath(second.body.url)
    expect(existsSync(deletedPath)).toBe(true)

    const deleted = await request(app)
      .delete(`/api/v1/admin/products/${productId}/images/${second.body.id}`)
      .set('Authorization', `Bearer ${adminToken}`)

    expect(deleted.status).toBe(204)
    expect(existsSync(deletedPath)).toBe(false)

    const served = await request(app).get(second.body.url)
    expect(served.status).toBe(404)

    const replacement = await upload('replacement.png', 'image/png', tinyPng)
    expect(replacement.status).toBe(201)
    expect(replacement.body.position).toBe(1)

    const remaining = await prisma.productImage.findMany({
      where: { productId },
      orderBy: { position: 'asc' },
    })
    expect(remaining.map((image) => image.id)).toEqual([
      first.body.id,
      replacement.body.id,
      third.body.id,
    ])
  })

  it('returns 404 for an image that belongs to another product', async () => {
    const image = await upload('photo.png', 'image/png', tinyPng)
    const otherProduct = await createProduct()

    const response = await request(app)
      .delete(`/api/v1/admin/products/${otherProduct.id}/images/${image.body.id}`)
      .set('Authorization', `Bearer ${adminToken}`)

    expect(response.status).toBe(404)
    expect(response.body.error.code).toBe('NOT_FOUND')
    expect(existsSync(uploadedFilePath(image.body.url))).toBe(true)
    expect(await prisma.productImage.count({ where: { id: image.body.id } })).toBe(1)
  })

  it('returns 404 for a product that does not exist and writes no file', async () => {
    const filesBefore = fileCount()
    const response = await upload('photo.png', 'image/png', tinyPng, 999_999)

    expect(response.status).toBe(404)
    expect(response.body.error.code).toBe('NOT_FOUND')
    expect(fileCount()).toBe(filesBefore)
  })

  it('requires the file field', async () => {
    const response = await request(app)
      .post(`/api/v1/admin/products/${productId}/images`)
      .set('Authorization', `Bearer ${adminToken}`)
      .field('note', 'missing file')

    expect(response.status).toBe(400)
    expect(response.body.error.code).toBe('VALIDATION_FAILED')
    expect(response.body.error.details).toEqual([
      { field: 'file', message: 'An image file is required' },
    ])
  })

  it('rejects a file sent under the wrong field name', async () => {
    const response = await request(app)
      .post(`/api/v1/admin/products/${productId}/images`)
      .set('Authorization', `Bearer ${adminToken}`)
      .attach('image', tinyPng, { filename: 'photo.png', contentType: 'image/png' })

    expect(response.status).toBe(400)
    expect(response.body.error.code).toBe('VALIDATION_FAILED')
    expect(response.body.error.details[0].field).toBe('image')
    expect(await prisma.productImage.count({ where: { productId } })).toBe(0)
  })
})
