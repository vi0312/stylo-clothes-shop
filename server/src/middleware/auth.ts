import type { Request, RequestHandler } from 'express'
import { findPublicUserById } from '../modules/auth/auth.repository.js'
import { toPublicUser, type PublicUser } from '../modules/users/user.dto.js'
import { verifyAccessToken } from '../lib/jwt.js'
import { forbidden, unauthorized } from '../utils/errors.js'

function readBearerToken(header: string | undefined): string | null {
  if (!header) return null
  const match = /^Bearer\s+(.+)$/i.exec(header.trim())
  return match?.[1]?.trim() || null
}

export const requireAuth: RequestHandler = async (request, _response, next) => {
  try {
    const token = readBearerToken(request.headers.authorization)
    if (!token) throw unauthorized('UNAUTHORIZED', 'Missing access token')

    const claims = verifyAccessToken(token)
    const row = await findPublicUserById(claims.sub)
    if (!row) throw unauthorized('UNAUTHORIZED', 'Missing or invalid access token')
    if (row.status === 'DISABLED') throw forbidden('ACCOUNT_DISABLED', 'Account is disabled')

    request.user = toPublicUser(row)
    next()
  } catch (error) {
    next(error)
  }
}

export function currentUser(request: Request): PublicUser {
  if (!request.user) throw unauthorized('UNAUTHORIZED', 'Missing access token')
  return request.user
}
