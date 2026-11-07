import { Router } from 'express'
import { currentUser, requireAuth } from '../../middleware/auth.js'
import { requireRole } from '../../middleware/requireRole.js'
import { parseBody, parseParams, parseQuery } from '../../middleware/validate.js'
import {
  changeOrderStatusSchema,
  createOrderSchema,
  listAdminOrdersQuerySchema,
  listOrdersQuerySchema,
  orderParamsSchema,
} from './orders.schema.js'
import * as ordersService from './orders.service.js'

export const ordersRouter = Router()

ordersRouter.use('/orders', requireAuth)

ordersRouter.post('/orders', async (request, response) => {
  const input = parseBody(createOrderSchema, request)
  const result = await ordersService.createOrder(currentUser(request).id, input)
  response.status(result.created ? 201 : 200).json({ order: result.order, payment: result.payment })
})

ordersRouter.get('/orders', async (request, response) => {
  const query = parseQuery(listOrdersQuerySchema, request)
  response.json(await ordersService.listOrders(currentUser(request).id, query))
})

ordersRouter.get('/orders/:id', async (request, response) => {
  const { id } = parseParams(orderParamsSchema, request)
  response.json(await ordersService.getOrder(currentUser(request).id, id))
})

ordersRouter.post('/orders/:id/cancel', async (request, response) => {
  const { id } = parseParams(orderParamsSchema, request)
  response.json(await ordersService.cancelOrder(currentUser(request).id, id))
})

export const adminOrdersRouter = Router()

adminOrdersRouter.use('/admin/orders', requireAuth, requireRole('ADMIN'))

adminOrdersRouter.get('/admin/orders', async (request, response) => {
  const query = parseQuery(listAdminOrdersQuerySchema, request)
  response.json(await ordersService.listAdminOrders(query))
})

adminOrdersRouter.get('/admin/orders/:id', async (request, response) => {
  const { id } = parseParams(orderParamsSchema, request)
  response.json(await ordersService.getAdminOrder(id))
})

adminOrdersRouter.patch('/admin/orders/:id/status', async (request, response) => {
  const { id } = parseParams(orderParamsSchema, request)
  const input = parseBody(changeOrderStatusSchema, request)
  response.json(await ordersService.changeOrderStatus(id, input.status))
})
