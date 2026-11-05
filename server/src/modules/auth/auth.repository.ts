import type { Prisma, RefreshToken, User } from '../../generated/prisma/client.js'
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
