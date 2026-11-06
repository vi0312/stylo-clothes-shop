import { Router } from 'express'
import { parseParams, parseQuery } from '../../middleware/validate.js'
import { listProductsQuerySchema, productParamsSchema } from './catalog.schema.js'
import * as catalogService from './catalog.service.js'

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
