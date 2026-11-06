import { notFound } from '../../utils/errors.js'
import { toPublicUser, type PublicUser } from '../users/user.dto.js'
import { toAddressDto, type AddressDto, type AddressListDto } from './profile.dto.js'
import * as repository from './profile.repository.js'
import type { CreateAddressInput, PatchAddressInput, PatchMeInput } from './profile.schema.js'

export async function updateMe(userId: number, patch: PatchMeInput): Promise<PublicUser> {
  const row = await repository.updateProfile(userId, patch)
  return toPublicUser(row)
}

export async function listAddresses(userId: number): Promise<AddressListDto> {
  const addresses = await repository.listAddresses(userId)
  return { items: addresses.map(toAddressDto) }
}

export async function createAddress(
  userId: number,
  input: CreateAddressInput,
): Promise<AddressDto> {
  return toAddressDto(await repository.createAddress(userId, input))
}

export async function updateAddress(
  userId: number,
  addressId: number,
  patch: PatchAddressInput,
): Promise<AddressDto> {
  const address = await repository.updateAddress(userId, addressId, patch)
  if (!address) throw notFound('NOT_FOUND', 'Address not found')
  return toAddressDto(address)
}

export async function deleteAddress(userId: number, addressId: number): Promise<void> {
  const deleted = await repository.deleteAddress(userId, addressId)
  if (!deleted) throw notFound('NOT_FOUND', 'Address not found')
}
