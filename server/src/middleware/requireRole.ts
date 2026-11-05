import type { RequestHandler } from 'express'
import type { Role } from '../generated/prisma/client.js'
import { forbidden, unauthorized } from '../utils/errors.js'

export function requireRole(...roles: Role[]): RequestHandler {
  return (request, _response, next) => {
    if (!request.user) {
      next(unauthorized('UNAUTHORIZED', 'Missing access token'))
      return
    }
    if (!roles.includes(request.user.role)) {
      next(forbidden('FORBIDDEN', 'You do not have permission to access this resource'))
      return
    }
    next()
  }
}
