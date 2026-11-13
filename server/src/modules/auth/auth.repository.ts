import type { PasswordResetToken, Prisma, RefreshToken, User } from '../../generated/prisma/client.js'
import { prisma } from '../../lib/prisma.js'

export const publicUserSelect = {
  id: true,
  email: true,
  fullName: true,
  phone: true,
  role: true,
  status: true,
  createdAt: true,
  updatedAt: true,
} satisfies Prisma.UserSelect

export type PublicUserRow = Prisma.UserGetPayload<{ select: typeof publicUserSelect }>

export interface NewCustomer {
  email: string
  passwordHash: string
  fullName: string
  phone: string | null
}

export interface NewRefreshToken {
  userId: number
  tokenHash: string
  expiresAt: Date
}

export function findUserByEmail(email: string): Promise<User | null> {
  return prisma.user.findUnique({ where: { email } })
}

export function findPublicUserById(id: number): Promise<PublicUserRow | null> {
  return prisma.user.findUnique({ where: { id }, select: publicUserSelect })
}

export function createCustomerWithRefreshToken(
  customer: NewCustomer,
  refreshToken: Omit<NewRefreshToken, 'userId'>,
): Promise<User> {
  return prisma.$transaction(async (tx) => {
    const user = await tx.user.create({ data: customer })
    await tx.refreshToken.create({ data: { ...refreshToken, userId: user.id } })
    return user
  })
}

export function createRefreshToken(refreshToken: NewRefreshToken): Promise<RefreshToken> {
  return prisma.refreshToken.create({ data: refreshToken })
}

export function findRefreshTokenWithUser(tokenHash: string) {
  return prisma.refreshToken.findUnique({
    where: { tokenHash },
    include: { user: { select: { id: true, role: true, status: true } } },
  })
}

/**
 * The conditional update is the rotation lock: concurrent refreshes with the same
 * token both reach it, but MySQL lets only the first one match `revokedAt: null`.
 */
export async function rotateRefreshToken(currentId: number, next: NewRefreshToken): Promise<boolean> {
  return prisma.$transaction(async (tx) => {
    const now = new Date()
    const revoked = await tx.refreshToken.updateMany({
      where: { id: currentId, revokedAt: null, expiresAt: { gt: now } },
      data: { revokedAt: now },
    })
    if (revoked.count === 0) return false

    await tx.refreshToken.create({ data: next })
    return true
  })
}

export async function revokeRefreshToken(tokenHash: string): Promise<void> {
  await prisma.refreshToken.updateMany({
    where: { tokenHash, revokedAt: null },
    data: { revokedAt: new Date() },
  })
}

export interface NewPasswordResetToken {
  userId: number
  codeHash: string
  expiresAt: Date
}

/** Only one code may be live per user, so asking again burns whatever was outstanding. */
export function replacePasswordResetToken(token: NewPasswordResetToken): Promise<PasswordResetToken> {
  return prisma.$transaction(async (tx) => {
    await tx.passwordResetToken.updateMany({
      where: { userId: token.userId, consumedAt: null },
      data: { consumedAt: new Date() },
    })
    return tx.passwordResetToken.create({ data: token })
  })
}

export async function deletePasswordResetToken(id: number): Promise<void> {
  await prisma.passwordResetToken.deleteMany({ where: { id } })
}

export function findActivePasswordResetToken(
  userId: number,
  now: Date,
): Promise<PasswordResetToken | null> {
  return prisma.passwordResetToken.findFirst({
    where: { userId, consumedAt: null, expiresAt: { gt: now } },
    orderBy: { id: 'desc' },
  })
}

export function findLatestPasswordResetToken(userId: number): Promise<PasswordResetToken | null> {
  return prisma.passwordResetToken.findFirst({
    where: { userId },
    orderBy: { id: 'desc' },
  })
}

export function countPasswordResetTokensSince(userId: number, since: Date): Promise<number> {
  return prisma.passwordResetToken.count({ where: { userId, createdAt: { gte: since } } })
}

/**
 * The attempt counter is incremented before the code is compared, so a burst of parallel
 * guesses cannot slip past `maxAttempts`; the code is burned the moment the cap is hit.
 */
export async function registerWrongResetCode(id: number, maxAttempts: number): Promise<void> {
  try {
    await prisma.$transaction(async (tx) => {
      const token = await tx.passwordResetToken.update({
        where: { id },
        data: { attempts: { increment: 1 } },
        select: { attempts: true, consumedAt: true },
      })
      if (token.consumedAt === null && token.attempts >= maxAttempts) {
        await tx.passwordResetToken.update({ where: { id }, data: { consumedAt: new Date() } })
      }
    })
  } catch {
    // The row can only disappear through a concurrent success; the caller answers the same.
  }
}

/**
 * Consuming the code, setting the password and revoking every session are one transaction:
 * a stolen refresh token must not outlive the password it was issued for.
 */
export async function consumePasswordResetToken(
  id: number,
  userId: number,
  passwordHash: string,
): Promise<boolean> {
  return prisma.$transaction(async (tx) => {
    const now = new Date()
    const consumed = await tx.passwordResetToken.updateMany({
      where: { id, consumedAt: null },
      data: { consumedAt: now },
    })
    if (consumed.count === 0) return false

    await tx.user.update({ where: { id: userId }, data: { passwordHash } })
    await tx.refreshToken.updateMany({
      where: { userId, revokedAt: null },
      data: { revokedAt: now },
    })
    return true
  })
}
