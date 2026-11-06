import type { Address, Prisma } from '../../generated/prisma/client.js'
import { prisma } from '../../lib/prisma.js'
import { publicUserSelect, type PublicUserRow } from '../auth/auth.repository.js'
import type { CreateAddressInput, PatchAddressInput, PatchMeInput } from './profile.schema.js'

const addressListOrder: Prisma.AddressOrderByWithRelationInput[] = [
  { isDefault: 'desc' },
  { createdAt: 'desc' },
  { id: 'desc' },
]

/**
 * Address mutations lock the owning user row so concurrent requests cannot break the
 * "exactly one default" invariant (two first addresses, two defaults at once).
 */
async function lockUser(tx: Prisma.TransactionClient, userId: number): Promise<void> {
  await tx.$queryRaw`SELECT id FROM user WHERE id = ${userId} FOR UPDATE`
}

export function updateProfile(userId: number, patch: PatchMeInput): Promise<PublicUserRow> {
  return prisma.user.update({
    where: { id: userId },
    data: {
      ...(patch.fullName === undefined ? {} : { fullName: patch.fullName }),
      ...(patch.phone === undefined ? {} : { phone: patch.phone }),
    },
    select: publicUserSelect,
  })
}

export function listAddresses(userId: number): Promise<Address[]> {
  return prisma.address.findMany({ where: { userId }, orderBy: addressListOrder })
}

export function createAddress(userId: number, input: CreateAddressInput): Promise<Address> {
  return prisma.$transaction(async (tx) => {
    await lockUser(tx, userId)

    const existingCount = await tx.address.count({ where: { userId } })
    const isDefault = existingCount === 0 || input.isDefault === true
    if (isDefault && existingCount > 0) {
      await tx.address.updateMany({
        where: { userId, isDefault: true },
        data: { isDefault: false },
      })
    }

    return tx.address.create({
      data: {
        userId,
        recipientName: input.recipientName,
        phone: input.phone,
        line: input.line,
        isDefault,
      },
    })
  })
}

export function updateAddress(
  userId: number,
  addressId: number,
  patch: PatchAddressInput,
): Promise<Address | null> {
  return prisma.$transaction(async (tx) => {
    await lockUser(tx, userId)

    const existing = await tx.address.findFirst({ where: { id: addressId, userId } })
    if (!existing) return null

    if (patch.isDefault === true) {
      await tx.address.updateMany({
        where: { userId, isDefault: true, id: { not: addressId } },
        data: { isDefault: false },
      })
    }

    return tx.address.update({
      where: { id: addressId },
      data: {
        ...(patch.recipientName === undefined ? {} : { recipientName: patch.recipientName }),
        ...(patch.phone === undefined ? {} : { phone: patch.phone }),
        ...(patch.line === undefined ? {} : { line: patch.line }),
        ...(patch.isDefault === undefined ? {} : { isDefault: patch.isDefault }),
      },
    })
  })
}

export function deleteAddress(userId: number, addressId: number): Promise<boolean> {
  return prisma.$transaction(async (tx) => {
    await lockUser(tx, userId)

    const existing = await tx.address.findFirst({ where: { id: addressId, userId } })
    if (!existing) return false

    await tx.address.delete({ where: { id: addressId } })
    if (!existing.isDefault) return true

    const oldest = await tx.address.findFirst({
      where: { userId },
      orderBy: [{ createdAt: 'asc' }, { id: 'asc' }],
    })
    if (oldest) {
      await tx.address.update({ where: { id: oldest.id }, data: { isDefault: true } })
    }
    return true
  })
}
