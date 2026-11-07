import type { Prisma, UserStatus } from '../../generated/prisma/client.js'
import { prisma } from '../../lib/prisma.js'
import { publicUserSelect, type PublicUserRow } from '../auth/auth.repository.js'
import type { AdminUserListQuery } from './users.schema.js'

function buildUserWhere(query: AdminUserListQuery): Prisma.UserWhereInput {
  const where: Prisma.UserWhereInput = {}

  if (query.status !== undefined) where.status = query.status
  if (query.role !== undefined) where.role = query.role
  if (query.q) {
    where.OR = [
      { email: { contains: query.q } },
      { fullName: { contains: query.q } },
      { phone: { contains: query.q } },
    ]
  }

  return where
}

export async function listUsers(
  query: AdminUserListQuery,
): Promise<{ users: PublicUserRow[]; total: number }> {
  const where = buildUserWhere(query)

  const [total, users] = await Promise.all([
    prisma.user.count({ where }),
    prisma.user.findMany({
      where,
      select: publicUserSelect,
      orderBy: query.sort === 'name_asc' ? { fullName: 'asc' } : { createdAt: 'desc' },
      skip: (query.page - 1) * query.limit,
      take: query.limit,
    }),
  ])

  return { users, total }
}

/**
 * Disabling and revoking sessions land in one transaction: a disabled account must never
 * keep a refresh token that could mint a fresh access token.
 */
export async function setUserStatus(
  id: number,
  status: UserStatus,
): Promise<PublicUserRow | null> {
  return prisma.$transaction(async (tx) => {
    const existing = await tx.user.findUnique({ where: { id }, select: { id: true } })
    if (!existing) return null

    const user = await tx.user.update({
      where: { id },
      data: { status },
      select: publicUserSelect,
    })

    if (status === 'DISABLED') {
      await tx.refreshToken.updateMany({
        where: { userId: id, revokedAt: null },
        data: { revokedAt: new Date() },
      })
    }

    return user
  })
}
