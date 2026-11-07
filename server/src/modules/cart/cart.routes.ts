import { Router } from 'express'
import { currentUser, requireAuth } from '../../middleware/auth.js'
import { parseBody, parseParams } from '../../middleware/validate.js'
import {
  addCartItemSchema,
  cartItemParamsSchema,
  patchCartItemSchema,
} from './cart.schema.js'
import * as cartService from './cart.service.js'

export const cartRouter = Router()

cartRouter.use('/cart', requireAuth)

cartRouter.get('/cart', async (request, response) => {
  response.json(await cartService.getCart(currentUser(request).id))
})

cartRouter.post('/cart/items', async (request, response) => {
  const input = parseBody(addCartItemSchema, request)
  response.json(await cartService.addItem(currentUser(request).id, input))
})

cartRouter.patch('/cart/items/:itemId', async (request, response) => {
  const { itemId } = parseParams(cartItemParamsSchema, request)
  const { quantity } = parseBody(patchCartItemSchema, request)
  response.json(await cartService.updateItemQuantity(currentUser(request).id, itemId, quantity))
})

cartRouter.delete('/cart/items/:itemId', async (request, response) => {
  const { itemId } = parseParams(cartItemParamsSchema, request)
  response.json(await cartService.removeItem(currentUser(request).id, itemId))
})

cartRouter.delete('/cart', async (request, response) => {
  response.json(await cartService.clearCart(currentUser(request).id))
})
