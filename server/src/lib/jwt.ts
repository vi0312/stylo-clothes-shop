import { createHmac, timingSafeEqual } from 'node:crypto'
import { z } from 'zod'
import { getEnv } from '../config/env.js'
import type { Role } from '../generated/prisma/client.js'
import { AppError, unauthorized } from '../utils/errors.js'

export interface AccessTokenClaims {
  sub: number
  role: Role
}

const headerSchema = z.object({ alg: z.literal('HS256'), typ: z.literal('JWT').optional() })

const payloadSchema = z.object({
  sub: z.number().int().positive(),
  role: z.enum(['CUSTOMER', 'ADMIN']),
  iat: z.number().int().optional(),
  exp: z.number().int(),
})

function invalidAccessToken(): AppError {
  return unauthorized('UNAUTHORIZED', 'Missing or invalid access token')
}

function encode(value: unknown): string {
  return Buffer.from(JSON.stringify(value), 'utf8').toString('base64url')
}

function signature(input: string): string {
  return createHmac('sha256', getEnv().JWT_ACCESS_SECRET).update(input).digest('base64url')
}

function decodePart(part: string): unknown {
  try {
    return JSON.parse(Buffer.from(part, 'base64url').toString('utf8'))
  } catch {
    throw invalidAccessToken()
  }
}

export function signAccessToken(
  userId: number,
  role: Role,
  ttlSeconds: number = getEnv().ACCESS_TOKEN_TTL_SECONDS,
): string {
  const issuedAt = Math.floor(Date.now() / 1000)
  const header = encode({ alg: 'HS256', typ: 'JWT' })
  const payload = encode({ sub: userId, role, iat: issuedAt, exp: issuedAt + ttlSeconds })
  return `${header}.${payload}.${signature(`${header}.${payload}`)}`
}

export function verifyAccessToken(token: string): AccessTokenClaims {
  const parts = token.split('.')
  if (parts.length !== 3) throw invalidAccessToken()

  const [headerPart, payloadPart, signaturePart] = parts as [string, string, string]
  const expected = Buffer.from(signature(`${headerPart}.${payloadPart}`), 'utf8')
  const actual = Buffer.from(signaturePart, 'utf8')
  if (actual.length !== expected.length || !timingSafeEqual(actual, expected)) {
    throw invalidAccessToken()
  }

  const header = headerSchema.safeParse(decodePart(headerPart))
  const payload = payloadSchema.safeParse(decodePart(payloadPart))
  if (!header.success || !payload.success) throw invalidAccessToken()
  if (payload.data.exp <= Math.floor(Date.now() / 1000)) throw invalidAccessToken()

  return { sub: payload.data.sub, role: payload.data.role }
}
