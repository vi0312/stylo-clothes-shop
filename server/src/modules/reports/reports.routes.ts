import { Router } from 'express'
import { requireAuth } from '../../middleware/auth.js'
import { requireRole } from '../../middleware/requireRole.js'
import { parseQuery } from '../../middleware/validate.js'
import {
  inventoryCostQuerySchema,
  lowStockQuerySchema,
  ordersByStatusQuerySchema,
  revenueQuerySchema,
  topProductsQuerySchema,
} from './reports.schema.js'
import * as reportsService from './reports.service.js'

export const adminReportsRouter = Router()

adminReportsRouter.use('/admin/reports', requireAuth, requireRole('ADMIN'))

adminReportsRouter.get('/admin/reports/revenue', async (request, response) => {
  const query = parseQuery(revenueQuerySchema, request)
  response.json(await reportsService.revenueReport(query))
})

adminReportsRouter.get('/admin/reports/top-products', async (request, response) => {
  const query = parseQuery(topProductsQuerySchema, request)
  response.json(await reportsService.topProductsReport(query))
})

adminReportsRouter.get('/admin/reports/orders-by-status', async (request, response) => {
  const query = parseQuery(ordersByStatusQuerySchema, request)
  response.json(await reportsService.ordersByStatusReport(query))
})

adminReportsRouter.get('/admin/reports/low-stock', async (request, response) => {
  const query = parseQuery(lowStockQuerySchema, request)
  response.json(await reportsService.lowStockReport(query))
})

adminReportsRouter.get('/admin/reports/inventory-cost', async (request, response) => {
  const query = parseQuery(inventoryCostQuerySchema, request)
  response.json(await reportsService.inventoryCostReport(query))
})
