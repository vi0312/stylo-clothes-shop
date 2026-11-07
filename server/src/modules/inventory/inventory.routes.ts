import { Router } from 'express'
import { currentUser, requireAuth } from '../../middleware/auth.js'
import { requireRole } from '../../middleware/requireRole.js'
import { parseBody, parseParams, parseQuery } from '../../middleware/validate.js'
import {
  createInventoryReceiptSchema,
  inventoryReceiptParamsSchema,
  listInventoryReceiptsQuerySchema,
} from './inventory.schema.js'
import * as inventoryService from './inventory.service.js'

export const adminInventoryRouter = Router()

adminInventoryRouter.use('/admin/inventory-receipts', requireAuth, requireRole('ADMIN'))

adminInventoryRouter.post('/admin/inventory-receipts', async (request, response) => {
  const input = parseBody(createInventoryReceiptSchema, request)
  const receipt = await inventoryService.createReceipt(currentUser(request).id, input)
  response.status(201).json(receipt)
})

adminInventoryRouter.get('/admin/inventory-receipts', async (request, response) => {
  const query = parseQuery(listInventoryReceiptsQuerySchema, request)
  response.json(await inventoryService.listReceipts(query))
})

adminInventoryRouter.get('/admin/inventory-receipts/:id', async (request, response) => {
  const { id } = parseParams(inventoryReceiptParamsSchema, request)
  response.json(await inventoryService.getReceipt(id))
})
