import { Router } from 'express'
import { currentUser, requireAuth } from '../../middleware/auth.js'
import { parseBody } from '../../middleware/validate.js'
import { createOrderSchema } from './orders.schema.js'
import * as ordersService from './orders.service.js'

export const ordersRouter = Router()

ordersRouter.use('/orders', requireAuth)

ordersRouter.post('/orders', async (request, response) => {
  const input = parseBody(createOrderSchema, request)
  const result = await ordersService.createOrder(currentUser(request).id, input)
  // Online payment attempts arrive with the payment ticket; the shape stays stable.
  response.status(result.created ? 201 : 200).json({ order: result.order, payment: null })
})
