import type { OrderStatus, PaymentMethod } from '../../generated/prisma/client.js'
import type { PaymentDto } from '../payments/payments.dto.js'
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

export interface OrderSummaryDto {
  id: number
  code: string
  status: OrderStatus
  paymentMethod: PaymentMethod
  paymentStatus: DerivedPaymentStatus
  total: number
  itemCount: number
  createdAt: string
}

export interface AdminOrderSummaryDto extends OrderSummaryDto {
  customerName: string
  recipientName: string
  recipientPhone: string
}

export interface AdminOrderItemDto extends OrderItemDto {
  unitCost: number | null
}

export interface AdminOrderDetailDto {
  id: number
  code: string
  status: OrderStatus
  paymentMethod: PaymentMethod
  paymentStatus: DerivedPaymentStatus
  customer: { id: number; email: string; fullName: string; phone: string | null }
  subtotal: number
  shippingFee: number
  total: number
  note: string | null
  recipientName: string
  recipientPhone: string
  shippingAddress: string
  items: AdminOrderItemDto[]
  payments: PaymentDto[]
  paidAt: string | null
  createdAt: string
  updatedAt: string
}

export interface Paginated<T> {
  items: T[]
  page: number
  limit: number
  total: number
}
