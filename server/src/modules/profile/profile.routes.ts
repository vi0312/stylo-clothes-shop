import { Router } from 'express'
import { currentUser, requireAuth } from '../../middleware/auth.js'
import { parseBody, parseParams } from '../../middleware/validate.js'
import {
  addressParamsSchema,
  createAddressSchema,
  patchAddressSchema,
  patchMeSchema,
} from './profile.schema.js'
import * as profileService from './profile.service.js'

export const profileRouter = Router()

profileRouter.use('/me', requireAuth)

profileRouter.patch('/me', async (request, response) => {
  const patch = parseBody(patchMeSchema, request)
  response.json(await profileService.updateMe(currentUser(request).id, patch))
})

profileRouter.get('/me/addresses', async (request, response) => {
  response.json(await profileService.listAddresses(currentUser(request).id))
})

profileRouter.post('/me/addresses', async (request, response) => {
  const input = parseBody(createAddressSchema, request)
  response.status(201).json(await profileService.createAddress(currentUser(request).id, input))
})

profileRouter.patch('/me/addresses/:id', async (request, response) => {
  const { id } = parseParams(addressParamsSchema, request)
  const patch = parseBody(patchAddressSchema, request)
  response.json(await profileService.updateAddress(currentUser(request).id, id, patch))
})

profileRouter.delete('/me/addresses/:id', async (request, response) => {
  const { id } = parseParams(addressParamsSchema, request)
  await profileService.deleteAddress(currentUser(request).id, id)
  response.status(204).end()
})
