import { Prisma } from '../../generated/prisma/client.js'
import { getEnv } from '../../config/env.js'
import { signAccessToken } from '../../lib/jwt.js'
import type { Mailer } from '../../lib/mailer.js'
import { hashPassword, verifyPassword } from '../../lib/password.js'
import { generateResetCode } from '../../lib/passwordResetCode.js'
import { passwordResetMail } from '../../lib/passwordResetMail.js'
import { generateRefreshToken, hashRefreshToken } from '../../lib/refreshToken.js'
import { conflict, forbidden, unauthorized } from '../../utils/errors.js'
import { toPublicUser, type PublicUser } from '../users/user.dto.js'
import type { ForgotPasswordInput, LoginInput, RegisterInput, ResetPasswordInput } from './auth.schema.js'
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

export interface PasswordResetDeps {
  mailer: Mailer
  /** Transport failures stay invisible to the caller; the route logs them instead. */
  onMailError: (error: unknown) => void
}

function invalidResetCode() {
  return unauthorized('INVALID_RESET_CODE', 'Invalid or expired reset code')
}

export async function requestPasswordReset(
  input: ForgotPasswordInput,
  deps: PasswordResetDeps,
): Promise<void> {
  const env = getEnv()
  const user = await repository.findUserByEmail(input.email.toLowerCase())

  if (!user || user.status === 'DISABLED') {
    // The same argon2 work as a real request, so latency does not announce the account.
    await hashPassword(generateResetCode())
    return
  }

  const now = new Date()
  const latest = await repository.findLatestPasswordResetToken(user.id)
  if (latest && now.getTime() - latest.createdAt.getTime() < env.PASSWORD_RESET_RESEND_SECONDS * 1_000) {
    return
  }
  const recent = await repository.countPasswordResetTokensSince(
    user.id,
    new Date(now.getTime() - 3_600_000),
  )
  if (recent >= env.PASSWORD_RESET_MAX_PER_HOUR) return

  const code = generateResetCode()
  const token = await repository.replacePasswordResetToken({
    userId: user.id,
    codeHash: await hashPassword(code),
    expiresAt: new Date(now.getTime() + env.PASSWORD_RESET_TTL_MINUTES * 60_000),
  })

  try {
    await deps.mailer.send({
      to: user.email,
      ...passwordResetMail({
        fullName: user.fullName,
        email: user.email,
        code,
        ttlMinutes: env.PASSWORD_RESET_TTL_MINUTES,
      }),
    })
  } catch (error) {
    // A failed send must not start the cooldown: dropping the token lets the retry through.
    await repository.deletePasswordResetToken(token.id)
    deps.onMailError(error)
  }
}

export async function resetPassword(input: ResetPasswordInput): Promise<void> {
  const user = await repository.findUserByEmail(input.email.toLowerCase())
  if (!user || user.status === 'DISABLED') throw invalidResetCode()

  const token = await repository.findActivePasswordResetToken(user.id, new Date())
  if (!token) throw invalidResetCode()

  if (!(await verifyPassword(token.codeHash, input.code))) {
    await repository.registerWrongResetCode(token.id, getEnv().PASSWORD_RESET_MAX_ATTEMPTS)
    throw invalidResetCode()
  }

  const consumed = await repository.consumePasswordResetToken(
    token.id,
    user.id,
    await hashPassword(input.password),
  )
  if (!consumed) throw invalidResetCode()
}
