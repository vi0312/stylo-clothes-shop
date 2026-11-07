import type { UserStatus } from '../../generated/prisma/client.js'
import { conflict, notFound } from '../../utils/errors.js'
import { toPublicUser, type PublicUser } from './user.dto.js'
import * as repository from './users.repository.js'
import type { AdminUserListQuery } from './users.schema.js'

export interface AdminUserPage {
  items: PublicUser[]
  page: number
  limit: number
  total: number
}

export async function listUsers(query: AdminUserListQuery): Promise<AdminUserPage> {
  const { users, total } = await repository.listUsers(query)

  return {
    items: users.map(toPublicUser),
    page: query.page,
    limit: query.limit,
    total,
  }
}

export async function setUserStatus(
  actor: PublicUser,
  id: number,
  status: UserStatus,
): Promise<PublicUser> {
  if (actor.id === id && status === 'DISABLED') {
    throw conflict('CANNOT_DISABLE_SELF', 'You cannot disable your own account')
  }

  const user = await repository.setUserStatus(id, status)
  if (!user) throw notFound('NOT_FOUND', `User ${id} not found`)

  return toPublicUser(user)
}
