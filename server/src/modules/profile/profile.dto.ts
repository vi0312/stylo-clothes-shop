import type { Address } from '../../generated/prisma/client.js'

export interface AddressDto {
  id: number
  recipientName: string
  phone: string
  line: string
  isDefault: boolean
  createdAt: string
  updatedAt: string
}

export interface AddressListDto {
  items: AddressDto[]
}

export function toAddressDto(address: Address): AddressDto {
  return {
    id: address.id,
    recipientName: address.recipientName,
    phone: address.phone,
    line: address.line,
    isDefault: address.isDefault,
    createdAt: address.createdAt.toISOString(),
    updatedAt: address.updatedAt.toISOString(),
  }
}
