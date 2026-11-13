import { Router } from 'express'
import type { Mailer } from '../../lib/mailer.js'
import { currentUser, requireAuth } from '../../middleware/auth.js'
import { parseBody } from '../../middleware/validate.js'
import {
  forgotPasswordSchema,
  loginSchema,
  refreshTokenSchema,
  registerSchema,
  resetPasswordSchema,
} from './auth.schema.js'
import * as authService from './auth.service.js'

export function createAuthRouter(mailer: Mailer): Router {
  const authRouter = Router()

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

  // Always 202: the answer must not differ for an unknown, disabled or throttled account.
  authRouter.post('/auth/forgot-password', async (request, response) => {
    await authService.requestPasswordReset(parseBody(forgotPasswordSchema, request), {
      mailer,
      onMailError: (error) => request.log?.error({ err: error }, 'password reset email failed'),
    })
    response.status(202).end()
  })

  authRouter.post('/auth/reset-password', async (request, response) => {
    await authService.resetPassword(parseBody(resetPasswordSchema, request))
    response.status(204).end()
  })

  authRouter.get('/me', requireAuth, (request, response) => {
    response.json(currentUser(request))
  })

  return authRouter
}
