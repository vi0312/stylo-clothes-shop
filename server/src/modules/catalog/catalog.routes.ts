import { Router } from 'express'
import { productImageUpload } from '../../middleware/upload.js'
import { requireAuth } from '../../middleware/auth.js'
import { requireRole } from '../../middleware/requireRole.js'
import { parseBody, parseParams, parseQuery } from '../../middleware/validate.js'
import {
  createBrandSchema,
  createCategorySchema,
  createProductSchema,
  createVariantSchema,
  idParamsSchema,
  adminListQuerySchema,
  adminProductListQuerySchema,
  patchBrandSchema,
  patchCategorySchema,
  patchProductSchema,
  patchVariantSchema,
  productImageParamsSchema,
  productParamsSchema,
  listProductsQuerySchema,
} from './catalog.schema.js'
import * as adminService from './catalog.admin.service.js'
import * as catalogService from './catalog.service.js'

export const adminCatalogRouter = Router()

export const catalogRouter = Router()

catalogRouter.get('/products', async (request, response) => {
  const query = parseQuery(listProductsQuerySchema, request)
  const result = await catalogService.listProducts(query)
  response.json(result)
})

catalogRouter.get('/products/:id', async (request, response) => {
  const { id } = parseParams(productParamsSchema, request)
  const product = await catalogService.getProductDetail(id)
  response.json(product)
})

catalogRouter.get('/catalog/filters', async (_request, response) => {
  const filters = await catalogService.getFilters()
  response.json(filters)
})

adminCatalogRouter.use('/admin', requireAuth, requireRole('ADMIN'))

adminCatalogRouter.get('/admin/categories', async (request, response) => {
  const query = parseQuery(adminListQuerySchema, request)
  response.json(await adminService.listTaxonomy('category', query))
})

adminCatalogRouter.post('/admin/categories', async (request, response) => {
  const input = parseBody(createCategorySchema, request)
  const category = await adminService.createTaxonomy('category', input)
  response.status(201).json(category)
})

adminCatalogRouter.patch('/admin/categories/:id', async (request, response) => {
  const params = parseParams(idParamsSchema, request)
  const patch = parseBody(patchCategorySchema, request)
  response.json(await adminService.patchTaxonomy('category', params.id, patch))
})

adminCatalogRouter.delete('/admin/categories/:id', async (request, response) => {
  const params = parseParams(idParamsSchema, request)
  await adminService.deleteTaxonomy('category', params.id)
  response.status(204).end()
})

adminCatalogRouter.get('/admin/brands', async (request, response) => {
  const query = parseQuery(adminListQuerySchema, request)
  response.json(await adminService.listTaxonomy('brand', query))
})

adminCatalogRouter.post('/admin/brands', async (request, response) => {
  const input = parseBody(createBrandSchema, request)
  const brand = await adminService.createTaxonomy('brand', input)
  response.status(201).json(brand)
})

adminCatalogRouter.patch('/admin/brands/:id', async (request, response) => {
  const params = parseParams(idParamsSchema, request)
  const patch = parseBody(patchBrandSchema, request)
  response.json(await adminService.patchTaxonomy('brand', params.id, patch))
})

adminCatalogRouter.delete('/admin/brands/:id', async (request, response) => {
  const params = parseParams(idParamsSchema, request)
  await adminService.deleteTaxonomy('brand', params.id)
  response.status(204).end()
})

adminCatalogRouter.get('/admin/products', async (request, response) => {
  const query = parseQuery(adminProductListQuerySchema, request)
  response.json(await adminService.listAdminProducts(query))
})

adminCatalogRouter.post('/admin/products', async (request, response) => {
  const input = parseBody(createProductSchema, request)
  const product = await adminService.createProduct(input)
  response.status(201).json(product)
})

adminCatalogRouter.get('/admin/products/:id', async (request, response) => {
  const params = parseParams(productParamsSchema, request)
  response.json(await adminService.getAdminProductDetail(params.id))
})

adminCatalogRouter.patch('/admin/products/:id', async (request, response) => {
  const params = parseParams(productParamsSchema, request)
  const patch = parseBody(patchProductSchema, request)
  response.json(await adminService.patchProduct(params.id, patch))
})

adminCatalogRouter.delete('/admin/products/:id', async (request, response) => {
  const params = parseParams(productParamsSchema, request)
  await adminService.deleteProductSafely(params.id)
  response.status(204).end()
})

adminCatalogRouter.post(
  '/admin/products/:id/images',
  productImageUpload.single('file'),
  async (request, response) => {
    const params = parseParams(productParamsSchema, request)
    const image = await adminService.addProductImage(params.id, request.file)
    response.status(201).json(image)
  },
)

adminCatalogRouter.delete('/admin/products/:id/images/:imageId', async (request, response) => {
  const params = parseParams(productImageParamsSchema, request)
  await adminService.removeProductImage(params.id, params.imageId)
  response.status(204).end()
})

adminCatalogRouter.post('/admin/products/:id/variants', async (request, response) => {
  const params = parseParams(productParamsSchema, request)
  const input = parseBody(createVariantSchema, request)
  const variant = await adminService.createVariant(params.id, input)
  response.status(201).json(variant)
})

adminCatalogRouter.patch('/admin/variants/:id', async (request, response) => {
  const params = parseParams(idParamsSchema, request)
  const patch = parseBody(patchVariantSchema, request)
  response.json(await adminService.patchVariant(params.id, patch))
})

adminCatalogRouter.delete('/admin/variants/:id', async (request, response) => {
  const params = parseParams(idParamsSchema, request)
  await adminService.deleteVariantSafely(params.id)
  response.status(204).end()
})
