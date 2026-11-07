import type { OrderStatus, PaymentMethod } from '../../generated/prisma/client.js'
import type { DerivedPaymentStatus } from './orders.logic.js'

export interface OrderItemDto {
  id: number
  productId: number
  variantId: number
  productName: string
  sku: string
  size: string
  color: string
  unitPrice: number
  quantity: number
  lineTotal: number
}

export interface OrderDto {
  id: number
  code: string
  status: OrderStatus
  paymentMethod: PaymentMethod
  paymentStatus: DerivedPaymentStatus
  subtotal: number
  shippingFee: number
  total: number
  note: string | null
  recipientName: string
  recipientPhone: string
  shippingAddress: string
  items: OrderItemDto[]
  paidAt: string | null
  createdAt: string
  updatedAt: string
}
