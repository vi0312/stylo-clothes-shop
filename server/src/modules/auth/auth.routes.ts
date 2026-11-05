import { Router } from 'express'
import { currentUser, requireAuth } from '../../middleware/auth.js'
import { parseBody } from '../../middleware/validate.js'
import { loginSchema, refreshTokenSchema, registerSchema } from './auth.schema.js'
import * as authService from './auth.service.js'

export const authRouter = Router()

authRouter.post('/auth/register', async (request, response) => {
  const session = await authService.register(parseBody(registerSchema, request))
  response.status(201).json(session)
})

authRouter.post('/auth/login', async (request, response) => {
  const session = await authService.login(parseBody(loginSchema, request))
  response.json(session)
})

authRouter.post('/auth/refresh', async (request, response) => {
  const { refreshToken } = parseBody(refreshTokenSchema, request)
  response.json(await authService.refresh(refreshToken))
})

authRouter.post('/auth/logout', async (request, response) => {
  const { refreshToken } = parseBody(refreshTokenSchema, request)
  await authService.logout(refreshToken)
  response.status(204).end()
})

authRouter.get('/me', requireAuth, (request, response) => {
  response.json(currentUser(request))
})
