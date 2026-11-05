import { Prisma } from '../../generated/prisma/client.js'
import { getEnv } from '../../config/env.js'
import { signAccessToken } from '../../lib/jwt.js'
import { hashPassword, verifyPassword } from '../../lib/password.js'
import { generateRefreshToken, hashRefreshToken } from '../../lib/refreshToken.js'
import { conflict, forbidden, unauthorized } from '../../utils/errors.js'
import { toPublicUser, type PublicUser } from '../users/user.dto.js'
import type { LoginInput, RegisterInput } from './auth.schema.js'
import * as repository from './auth.repository.js'

export interface AuthSession {
  accessToken: string
  refreshToken: string
  user: PublicUser
}

export interface TokenPair {
  accessToken: string
  refreshToken: string
}

const MILLISECONDS_PER_DAY = 86_400_000

function invalidCredentials() {
  return unauthorized('INVALID_CREDENTIALS', 'Invalid email or password')
}

function invalidRefreshToken() {
  return unauthorized('INVALID_TOKEN', 'Invalid or expired refresh token')
}

function accountDisabled() {
  return forbidden('ACCOUNT_DISABLED', 'Account is disabled')
}

function issueRefreshToken(): { token: string; tokenHash: string; expiresAt: Date } {
  const token = generateRefreshToken()
  return {
    token,
    tokenHash: hashRefreshToken(token),
    expiresAt: new Date(Date.now() + getEnv().REFRESH_TOKEN_TTL_DAYS * MILLISECONDS_PER_DAY),
  }
}

function isUniqueViolation(error: unknown): boolean {
  return error instanceof Prisma.PrismaClientKnownRequestError && error.code === 'P2002'
}

export async function register(input: RegisterInput): Promise<AuthSession> {
  const refresh = issueRefreshToken()
  try {
    const user = await repository.createCustomerWithRefreshToken(
      {
        email: input.email.toLowerCase(),
        passwordHash: await hashPassword(input.password),
        fullName: input.fullName,
        phone: input.phone ?? null,
      },
      { tokenHash: refresh.tokenHash, expiresAt: refresh.expiresAt },
    )

    return {
      accessToken: signAccessToken(user.id, user.role),
      refreshToken: refresh.token,
      user: toPublicUser(user),
    }
  } catch (error) {
    if (isUniqueViolation(error)) throw conflict('EMAIL_TAKEN', 'Email is already registered')
    throw error
  }
}

export async function login(input: LoginInput): Promise<AuthSession> {
  const user = await repository.findUserByEmail(input.email.toLowerCase())
  if (!user) throw invalidCredentials()
  if (!(await verifyPassword(user.passwordHash, input.password))) throw invalidCredentials()
  if (user.status === 'DISABLED') throw accountDisabled()

  const refresh = issueRefreshToken()
  await repository.createRefreshToken({
    userId: user.id,
    tokenHash: refresh.tokenHash,
    expiresAt: refresh.expiresAt,
  })

  return {
    accessToken: signAccessToken(user.id, user.role),
    refreshToken: refresh.token,
    user: toPublicUser(user),
  }
}

export async function refresh(refreshToken: string): Promise<TokenPair> {
  const stored = await repository.findRefreshTokenWithUser(hashRefreshToken(refreshToken))
  if (!stored || stored.revokedAt !== null || stored.expiresAt <= new Date()) {
    throw invalidRefreshToken()
  }
  if (stored.user.status === 'DISABLED') throw accountDisabled()

  const next = issueRefreshToken()
  const rotated = await repository.rotateRefreshToken(stored.id, {
    userId: stored.userId,
    tokenHash: next.tokenHash,
    expiresAt: next.expiresAt,
  })
  if (!rotated) throw invalidRefreshToken()

  return {
    accessToken: signAccessToken(stored.user.id, stored.user.role),
    refreshToken: next.token,
  }
}

export async function logout(refreshToken: string): Promise<void> {
  await repository.revokeRefreshToken(hashRefreshToken(refreshToken))
}
