import { Router } from 'express'
import { currentUser, requireAuth } from '../../middleware/auth.js'
import { requireRole } from '../../middleware/requireRole.js'
import { parseBody, parseParams, parseQuery } from '../../middleware/validate.js'
import {
  adminUserListQuerySchema,
  patchUserStatusSchema,
  userIdParamsSchema,
} from './users.schema.js'
import * as userService from './users.service.js'

export const adminUsersRouter = Router()

adminUsersRouter.use('/admin/users', requireAuth, requireRole('ADMIN'))

adminUsersRouter.get('/admin/users', async (request, response) => {
  const query = parseQuery(adminUserListQuerySchema, request)
  response.json(await userService.listUsers(query))
})

adminUsersRouter.patch('/admin/users/:id/status', async (request, response) => {
  const { id } = parseParams(userIdParamsSchema, request)
  const input = parseBody(patchUserStatusSchema, request)
  response.json(await userService.setUserStatus(currentUser(request), id, input.status))
})
