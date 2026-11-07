import request from 'supertest'
import { beforeEach, describe, expect, it } from 'vitest'
import { createApp } from '../../src/app.js'
import { resetDb } from '../helpers/resetDb.js'
import { createBrand, createCategory, createProduct } from '../factories/catalog.js'
import { seed } from '../../prisma/seed.js'

const app = createApp()

beforeEach(async () => {
  await resetDb()
})

describe('GET /products', () => {
  it('returns a paginated list of sellable products', async () => {
    const category = await createCategory({ name: 'Áo thun' })
    const brand = await createBrand({ name: 'Stylo' })

    const product = await createProduct({
      name: 'Áo thun basic',
      categoryId: category.id,
      brandId: brand.id,
      images: [
        { url: '/uploads/products/cover.jpg', position: 0 },
        { url: '/uploads/products/detail.jpg', position: 1 },
      ],
      variants: [
        { size: 'M', color: 'Đen', price: 199_000, stock: 5 },
        { size: 'L', color: 'Đen', price: 229_000, stock: 3 },
      ],
    })

    const response = await request(app).get('/api/v1/products')

    expect(response.status).toBe(200)
    expect(response.body).toEqual({
      items: [
        {
          id: product.id,
          name: 'Áo thun basic',
          coverUrl: '/uploads/products/cover.jpg',
          minPrice: 199_000,
          maxPrice: 229_000,
          category: { id: category.id, name: 'Áo thun' },
          brand: { id: brand.id, name: 'Stylo' },
        },
      ],
      page: 1,
      limit: 20,
      total: 1,
    })
  })

  it('excludes INACTIVE products and products with no in-stock variants', async () => {
    // Active product with stock
    const activeProduct = await createProduct({
      name: 'Sản phẩm bán được',
      variants: [{ size: 'M', color: 'Đen', price: 100_000, stock: 5 }],
    })

    // Inactive product even with stock
    await createProduct({
      name: 'Sản phẩm ngưng bán',
      status: 'INACTIVE',
      variants: [{ size: 'M', color: 'Đen', price: 100_000, stock: 10 }],
    })

    // Active product but stock = 0 on all variants
    await createProduct({
      name: 'Sản phẩm hết hàng',
      status: 'ACTIVE',
      variants: [
        { size: 'M', color: 'Đen', price: 100_000, stock: 0 },
        { size: 'L', color: 'Trắng', price: 120_000, stock: 0 },
      ],
    })

    // Active product with no variants
    await createProduct({
      name: 'Sản phẩm không có biến thể',
      status: 'ACTIVE',
      variants: [],
    })

    const response = await request(app).get('/api/v1/products')

    expect(response.status).toBe(200)
    expect(response.body.total).toBe(1)
    expect(response.body.items).toHaveLength(1)
    expect(response.body.items[0].id).toBe(activeProduct.id)
  })

  it('sets coverUrl to null if no image has position 0', async () => {
    await createProduct({
      images: [{ url: '/uploads/products/side.jpg', position: 1 }],
      variants: [{ size: 'M', color: 'Đen', price: 150_000, stock: 2 }],
    })

    const response = await request(app).get('/api/v1/products')
    expect(response.status).toBe(200)
    expect(response.body.items[0].coverUrl).toBeNull()
  })

  it('filters by accent-insensitive q matching product, brand or category name', async () => {
    const catShirt = await createCategory({ name: 'Áo sơ mi' })
    const catPant = await createCategory({ name: 'Quần tây' })
    const brandStylo = await createBrand({ name: 'Stylo Local' })
    const brandAnPhuoc = await createBrand({ name: 'An Phước' })

    const p1 = await createProduct({
      name: 'Áo khoác dù',
      categoryId: catShirt.id,
      brandId: brandStylo.id,
      variants: [{ size: 'M', color: 'Xanh', price: 300_000, stock: 1 }],
    })
    const p2 = await createProduct({
      name: 'Quần âu nam',
      categoryId: catPant.id,
      brandId: brandAnPhuoc.id,
      variants: [{ size: '30', color: 'Đen', price: 400_000, stock: 1 }],
    })

    // Search by product name without accents: "khoac" matches "Áo khoác dù"
    const res1 = await request(app).get('/api/v1/products?q=khoac')
    expect(res1.body.total).toBe(1)
    expect(res1.body.items[0].id).toBe(p1.id)

    // Search by category name: "so mi" matches catShirt
    const res2 = await request(app).get('/api/v1/products?q=so+mi')
    expect(res2.body.total).toBe(1)
    expect(res2.body.items[0].id).toBe(p1.id)

    // Search by brand name: "an phuoc" matches brandAnPhuoc
    const res3 = await request(app).get('/api/v1/products?q=an+phuoc')
    expect(res3.body.total).toBe(1)
    expect(res3.body.items[0].id).toBe(p2.id)

    // Search with no match
    const res4 = await request(app).get('/api/v1/products?q=khongtotai')
    expect(res4.body.total).toBe(0)
    expect(res4.body.items).toHaveLength(0)
  })

  it('filters by categoryId and brandId', async () => {
    const cat1 = await createCategory()
    const cat2 = await createCategory()
    const brand1 = await createBrand()
    const brand2 = await createBrand()

    const p1 = await createProduct({
      categoryId: cat1.id,
      brandId: brand1.id,
      variants: [{ size: 'M', color: 'Đỏ', price: 100_000, stock: 1 }],
    })
    await createProduct({
      categoryId: cat2.id,
      brandId: brand2.id,
      variants: [{ size: 'M', color: 'Đỏ', price: 100_000, stock: 1 }],
    })

    const resCat = await request(app).get(`/api/v1/products?categoryId=${cat1.id}`)
    expect(resCat.body.total).toBe(1)
    expect(resCat.body.items[0].id).toBe(p1.id)

    const resBrand = await request(app).get(`/api/v1/products?brandId=${brand1.id}`)
    expect(resBrand.body.total).toBe(1)
    expect(resBrand.body.items[0].id).toBe(p1.id)

    const resUnknown = await request(app).get('/api/v1/products?categoryId=99999')
    expect(resUnknown.body.total).toBe(0)
  })

  it('requires a single variant to satisfy all variant filters (color, size, price) with stock > 0', async () => {
    // Product A: Variant 1 is (Đen, M, 100k, stock 5), Variant 2 is (Trắng, L, 200k, stock 5)
    await createProduct({
      name: 'Product A',
      variants: [
        { size: 'M', color: 'Đen', price: 100_000, stock: 5 },
        { size: 'L', color: 'Trắng', price: 200_000, stock: 5 },
      ],
    })

    // Product B: Variant 1 is (Đen, L, 150k, stock 3)
    const productB = await createProduct({
      name: 'Product B',
      variants: [{ size: 'L', color: 'Đen', price: 150_000, stock: 3 }],
    })

    // Product C: Has (Đen, L, 150k) but stock = 0; and (Trắng, M, 100k) with stock = 5
    await createProduct({
      name: 'Product C',
      variants: [
        { size: 'L', color: 'Đen', price: 150_000, stock: 0 },
        { size: 'M', color: 'Trắng', price: 100_000, stock: 5 },
      ],
    })

    // Filter color=Đen AND size=L:
    // Product A has Đen (size M) and L (color Trắng), so NO single variant satisfies both -> excluded!
    // Product B has a single variant with Đen + L + stock 3 -> MATCHES!
    // Product C has Đen + L but stock = 0 -> excluded!
    const response = await request(app).get('/api/v1/products?color=Đen&size=L')
    expect(response.body.total).toBe(1)
    expect(response.body.items[0].id).toBe(productB.id)
  })

  it('filters by price range (minPrice, maxPrice)', async () => {
    const pCheap = await createProduct({
      variants: [{ size: 'M', color: 'Đen', price: 100_000, stock: 2 }],
    })
    const pMid = await createProduct({
      variants: [{ size: 'M', color: 'Đen', price: 200_000, stock: 2 }],
    })
    const pExpensive = await createProduct({
      variants: [{ size: 'M', color: 'Đen', price: 300_000, stock: 2 }],
    })

    const resMin = await request(app).get('/api/v1/products?minPrice=200000')
    expect(resMin.body.items.map((i: { id: number }) => i.id).sort()).toEqual(
      [pMid.id, pExpensive.id].sort(),
    )

    const resMax = await request(app).get('/api/v1/products?maxPrice=200000')
    expect(resMax.body.items.map((i: { id: number }) => i.id).sort()).toEqual(
      [pCheap.id, pMid.id].sort(),
    )

    const resRange = await request(app).get('/api/v1/products?minPrice=150000&maxPrice=250000')
    expect(resRange.body.items.map((i: { id: number }) => i.id)).toEqual([pMid.id])
  })

  it('sorts by newest, name_asc, price_asc, price_desc', async () => {
    const pA = await createProduct({
      name: 'Áo khoác',
      variants: [
        { size: 'M', color: 'Đen', price: 50_000, stock: 0 }, // Out of stock min price
        { size: 'L', color: 'Đen', price: 300_000, stock: 2 }, // In-stock min price = 300k
      ],
    })
    const pB = await createProduct({
      name: 'Quần jeans',
      variants: [{ size: '30', color: 'Xanh', price: 100_000, stock: 2 }], // In-stock min price = 100k
    })
    const pC = await createProduct({
      name: 'Váy midi',
      variants: [{ size: 'S', color: 'Trắng', price: 200_000, stock: 2 }], // In-stock min price = 200k
    })

    // newest (default): pC, pB, pA
    const resNewest = await request(app).get('/api/v1/products?sort=newest')
    expect(resNewest.body.items.map((i: { id: number }) => i.id)).toEqual([pC.id, pB.id, pA.id])

    // name_asc: Áo khoác (pA), Quần jeans (pB), Váy midi (pC)
    const resName = await request(app).get('/api/v1/products?sort=name_asc')
    expect(resName.body.items.map((i: { id: number }) => i.id)).toEqual([pA.id, pB.id, pC.id])

    // price_asc: pB (100k), pC (200k), pA (300k - in-stock min price, ignoring 50k stock 0)
    const resPriceAsc = await request(app).get('/api/v1/products?sort=price_asc')
    expect(resPriceAsc.body.items.map((i: { id: number }) => i.id)).toEqual([pB.id, pC.id, pA.id])

    // price_desc: pA (300k), pC (200k), pB (100k)
    const resPriceDesc = await request(app).get('/api/v1/products?sort=price_desc')
    expect(resPriceDesc.body.items.map((i: { id: number }) => i.id)).toEqual([pA.id, pC.id, pB.id])
  })

  it('paginates correctly and returns empty array when page is out of range', async () => {
    for (let i = 1; i <= 5; i += 1) {
      await createProduct({ name: `Product ${i}` })
    }

    const resPage1 = await request(app).get('/api/v1/products?page=1&limit=2')
    expect(resPage1.body.total).toBe(5)
    expect(resPage1.body.page).toBe(1)
    expect(resPage1.body.limit).toBe(2)
    expect(resPage1.body.items).toHaveLength(2)

    const resPage2 = await request(app).get('/api/v1/products?page=2&limit=2')
    expect(resPage2.body.items).toHaveLength(2)

    const resPage3 = await request(app).get('/api/v1/products?page=3&limit=2')
    expect(resPage3.body.items).toHaveLength(1)

    const resPage4 = await request(app).get('/api/v1/products?page=4&limit=2')
    expect(resPage4.body.total).toBe(5)
    expect(resPage4.body.items).toHaveLength(0)
  })

  it('returns 400 VALIDATION_FAILED on invalid query parameters', async () => {
    // Unknown sort
    const resSort = await request(app).get('/api/v1/products?sort=popular')
    expect(resSort.status).toBe(400)
    expect(resSort.body.error.code).toBe('VALIDATION_FAILED')

    // minPrice > maxPrice
    const resPrice = await request(app).get('/api/v1/products?minPrice=500000&maxPrice=100000')
    expect(resPrice.status).toBe(400)
    expect(resPrice.body.error.code).toBe('VALIDATION_FAILED')

    // limit = 0
    const resLimit0 = await request(app).get('/api/v1/products?limit=0')
    expect(resLimit0.status).toBe(400)
    expect(resLimit0.body.error.code).toBe('VALIDATION_FAILED')

    // limit > 100
    const resLimit101 = await request(app).get('/api/v1/products?limit=101')
    expect(resLimit101.status).toBe(400)
    expect(resLimit101.body.error.code).toBe('VALIDATION_FAILED')

    // page = 0
    const resPage0 = await request(app).get('/api/v1/products?page=0')
    expect(resPage0.status).toBe(400)
    expect(resPage0.body.error.code).toBe('VALIDATION_FAILED')
  })

  it('ignores unknown query parameters', async () => {
    const product = await createProduct()
    const response = await request(app).get('/api/v1/products?unknownParam=foobar')
    expect(response.status).toBe(200)
    expect(response.body.items[0].id).toBe(product.id)
  })
})

describe('GET /products/:id', () => {
  it('returns full ProductDetail with sorted images and variants', async () => {
    const category = await createCategory({ name: 'Áo thun' })
    const brand = await createBrand({ name: 'Stylo' })

    const product = await createProduct({
      name: 'Áo thun cotton',
      description: 'Cotton 100%, form rộng.',
      categoryId: category.id,
      brandId: brand.id,
      images: [
        { url: '/uploads/products/pos1.jpg', position: 1 },
        { url: '/uploads/products/pos0.jpg', position: 0 },
      ],
      variants: [
        { sku: 'SKU-01', size: 'M', color: 'Đen', price: 199_000, stock: 5 },
        { sku: 'SKU-02', size: 'L', color: 'Đen', price: 219_000, stock: 0 },
      ],
    })

    const response = await request(app).get(`/api/v1/products/${product.id}`)

    expect(response.status).toBe(200)
    expect(response.body).toEqual({
      id: product.id,
      name: 'Áo thun cotton',
      description: 'Cotton 100%, form rộng.',
      category: { id: category.id, name: 'Áo thun' },
      brand: { id: brand.id, name: 'Stylo' },
      images: [
        { id: expect.any(Number), url: '/uploads/products/pos0.jpg', position: 0 },
        { id: expect.any(Number), url: '/uploads/products/pos1.jpg', position: 1 },
      ],
      variants: [
        {
          id: expect.any(Number),
          sku: 'SKU-01',
          size: 'M',
          color: 'Đen',
          price: 199_000,
          stock: 5,
        },
        {
          id: expect.any(Number),
          sku: 'SKU-02',
          size: 'L',
          color: 'Đen',
          price: 219_000,
          stock: 0,
        },
      ],
      minPrice: 199_000,
      maxPrice: 199_000,
      createdAt: expect.stringMatching(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/),
      updatedAt: expect.stringMatching(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/),
    })
  })

  it('sets minPrice and maxPrice to null when every variant has stock = 0', async () => {
    const product = await createProduct({
      variants: [{ size: 'M', color: 'Đen', price: 199_000, stock: 0 }],
    })

    const response = await request(app).get(`/api/v1/products/${product.id}`)
    expect(response.status).toBe(200)
    expect(response.body.minPrice).toBeNull()
    expect(response.body.maxPrice).toBeNull()
    expect(response.body.variants).toHaveLength(1)
  })

  it('returns 404 NOT_FOUND when product does not exist', async () => {
    const response = await request(app).get('/api/v1/products/999999')
    expect(response.status).toBe(404)
    expect(response.body).toEqual({
      error: {
        code: 'NOT_FOUND',
        message: 'Product not found',
      },
    })
  })

  it('returns 404 NOT_FOUND when product is INACTIVE', async () => {
    const product = await createProduct({ status: 'INACTIVE' })
    const response = await request(app).get(`/api/v1/products/${product.id}`)
    expect(response.status).toBe(404)
    expect(response.body.error.code).toBe('NOT_FOUND')
  })

  it('returns 400 VALIDATION_FAILED when id is not a positive integer', async () => {
    const resAlpha = await request(app).get('/api/v1/products/abc')
    expect(resAlpha.status).toBe(400)
    expect(resAlpha.body.error.code).toBe('VALIDATION_FAILED')

    const resNegative = await request(app).get('/api/v1/products/-5')
    expect(resNegative.status).toBe(400)
    expect(resNegative.body.error.code).toBe('VALIDATION_FAILED')
  })
})

describe('GET /catalog/filters', () => {
  it('returns sellable categories, brands, colors, sizes and price range', async () => {
    const catShirt = await createCategory({ name: 'Áo sơ mi', status: 'ACTIVE' })
    const catPant = await createCategory({ name: 'Quần tây', status: 'ACTIVE' })
    const catHidden = await createCategory({ name: 'Danh mục ẩn', status: 'INACTIVE' })

    const brandStylo = await createBrand({ name: 'Stylo', status: 'ACTIVE' })
    const brandLocal = await createBrand({ name: 'Local', status: 'ACTIVE' })
    const brandHidden = await createBrand({ name: 'Hãng ẩn', status: 'INACTIVE' })

    // Product 1: Sellable in catShirt, brandStylo
    await createProduct({
      categoryId: catShirt.id,
      brandId: brandStylo.id,
      variants: [
        { size: 'M', color: 'Đen', price: 199_000, stock: 5 },
        { size: 'L', color: 'Trắng', price: 299_000, stock: 2 },
      ],
    })

    // Product 2: Sellable in catPant, brandLocal
    await createProduct({
      categoryId: catPant.id,
      brandId: brandLocal.id,
      variants: [{ size: '30', color: 'Be', price: 499_000, stock: 3 }],
    })

    // Product 3: INACTIVE category (should not appear in categories filter)
    await createProduct({
      categoryId: catHidden.id,
      brandId: brandStylo.id,
      variants: [{ size: 'XL', color: 'Xám', price: 150_000, stock: 5 }],
    })

    // Product 4: INACTIVE brand (should not appear in brands filter)
    await createProduct({
      categoryId: catShirt.id,
      brandId: brandHidden.id,
      variants: [{ size: 'S', color: 'Nâu', price: 250_000, stock: 5 }],
    })

    // Product 5: Product is INACTIVE (its colors, sizes, category should not leak)
    const catNoStock = await createCategory({ name: 'Không bán', status: 'ACTIVE' })
    await createProduct({
      categoryId: catNoStock.id,
      brandId: brandStylo.id,
      status: 'INACTIVE',
      variants: [{ size: 'XXL', color: 'Hồng', price: 999_000, stock: 10 }],
    })

    // Product 6: Stock = 0 on all variants
    const catZeroStock = await createCategory({ name: 'Hết sạch hàng', status: 'ACTIVE' })
    await createProduct({
      categoryId: catZeroStock.id,
      brandId: brandStylo.id,
      status: 'ACTIVE',
      variants: [{ size: '32', color: 'Cam', price: 800_000, stock: 0 }],
    })

    const response = await request(app).get('/api/v1/catalog/filters')

    expect(response.status).toBe(200)
    expect(response.body).toEqual({
      categories: [
        { id: catShirt.id, name: 'Áo sơ mi' },
        { id: catPant.id, name: 'Quần tây' },
      ],
      brands: [
        { id: brandLocal.id, name: 'Local' },
        { id: brandStylo.id, name: 'Stylo' },
      ],
      colors: expect.arrayContaining(['Be', 'Đen', 'Nâu', 'Trắng', 'Xám']),
      sizes: expect.arrayContaining(['30', 'L', 'M', 'S', 'XL']),
      priceRange: {
        min: 150_000,
        max: 499_000,
      },
    })

    // Confirm that excluded colors and categories are NOT present
    expect(response.body.colors).not.toContain('Hồng')
    expect(response.body.colors).not.toContain('Cam')
    expect(response.body.sizes).not.toContain('XXL')
    expect(response.body.sizes).not.toContain('32')
    expect(response.body.categories.some((c: { id: number }) => c.id === catHidden.id)).toBe(false)
    expect(response.body.categories.some((c: { id: number }) => c.id === catNoStock.id)).toBe(false)
    expect(response.body.categories.some((c: { id: number }) => c.id === catZeroStock.id)).toBe(
      false,
    )
    expect(response.body.brands.some((b: { id: number }) => b.id === brandHidden.id)).toBe(false)
  })

  it('returns empty filters and null priceRange when nothing is sellable', async () => {
    const response = await request(app).get('/api/v1/catalog/filters')

    expect(response.status).toBe(200)
    expect(response.body).toEqual({
      categories: [],
      brands: [],
      colors: [],
      sizes: [],
      priceRange: { min: null, max: null },
    })
  })
})

describe('Seed data catalog query', () => {
  it('allows querying the seeded catalog through public endpoints', async () => {
    await seed()

    const listRes = await request(app).get('/api/v1/products')
    expect(listRes.status).toBe(200)
    expect(listRes.body.total).toBeGreaterThan(0)
    expect(listRes.body.items.length).toBeGreaterThan(0)

    const searchRes = await request(app).get('/api/v1/products').query({ q: 'áo thun basic' })
    expect(searchRes.status).toBe(200)
    const productNames = searchRes.body.items.map((p: { name: string }) => p.name)
    expect(productNames).toContain('Áo thun basic')

    const filtersRes = await request(app).get('/api/v1/catalog/filters')
    expect(filtersRes.status).toBe(200)
    const categoryNames = filtersRes.body.categories.map((c: { name: string }) => c.name)
    expect(categoryNames).toEqual(expect.arrayContaining(['Áo', 'Quần', 'Áo khoác', 'Giày']))
    expect(categoryNames).not.toContain('Phụ kiện')
    expect(filtersRes.body.brands.map((b: { name: string }) => b.name)).toContain('Stylo')
    expect(filtersRes.body.colors.length).toBeGreaterThan(0)
    expect(filtersRes.body.sizes.length).toBeGreaterThan(0)
    expect(filtersRes.body.priceRange.min).toBeGreaterThan(0)
    expect(filtersRes.body.priceRange.max).toBeGreaterThanOrEqual(filtersRes.body.priceRange.min)

    const firstProduct = searchRes.body.items[0]
    const detailRes = await request(app).get(`/api/v1/products/${firstProduct.id}`)
    expect(detailRes.status).toBe(200)
    expect(detailRes.body.id).toBe(firstProduct.id)
    expect(detailRes.body.variants.length).toBeGreaterThan(0)
  })
})
