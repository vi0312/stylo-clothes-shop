import type { Role, User, UserStatus } from '../../src/generated/prisma/client.js'
import { hashPassword } from '../../src/lib/password.js'
import { prisma } from '../../src/lib/prisma.js'

export const TEST_PASSWORD = 'matkhau123'

export interface CreateUserOptions {
  email?: string
  password?: string
  fullName?: string
  phone?: string | null
  role?: Role
  status?: UserStatus
}

let sequence = 0

export async function createUser(options: CreateUserOptions = {}): Promise<User> {
  sequence += 1
  return prisma.user.create({
    data: {
      email: options.email ?? `user-${sequence}@stylo.test`,
      passwordHash: await hashPassword(options.password ?? TEST_PASSWORD),
      fullName: options.fullName ?? `Test User ${sequence}`,
      phone: options.phone ?? null,
      role: options.role ?? 'CUSTOMER',
      status: options.status ?? 'ACTIVE',
    },
  })
}
