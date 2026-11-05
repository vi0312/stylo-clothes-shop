import type { Role, UserStatus } from '../../generated/prisma/client.js'

export interface PublicUser {
  id: number
  email: string
  fullName: string
  phone: string | null
  role: Role
  status: UserStatus
  createdAt: string
  updatedAt: string
}

export interface UserRecord {
  id: number
  email: string
  fullName: string
  phone: string | null
  role: Role
  status: UserStatus
  createdAt: Date
  updatedAt: Date
}

export function toPublicUser(user: UserRecord): PublicUser {
  return {
    id: user.id,
    email: user.email,
    fullName: user.fullName,
    phone: user.phone,
    role: user.role,
    status: user.status,
    createdAt: user.createdAt.toISOString(),
    updatedAt: user.updatedAt.toISOString(),
  }
}
