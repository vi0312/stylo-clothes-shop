import { randomInt } from 'node:crypto'
import type { CatalogStatus, OrderStatus, PaymentStatus } from '../../generated/prisma/client.js'
import { datedCode } from '../../lib/businessCode.js'

export function orderCode(now: Date, nextInt: (max: number) => number = randomInt): string {
  return datedCode('STY', now, nextInt)
}

export interface OrderTotals {
  subtotal: number
  total: number
}

export function subtotalOf(lines: readonly { unitPrice: number; quantity: number }[]): number {
  return lines.reduce((sum, line) => sum + line.unitPrice * line.quantity, 0)
}

export function computeTotals(
  lines: readonly { unitPrice: number; quantity: number }[],
  shippingFee: number,
): OrderTotals {
  const subtotal = subtotalOf(lines)
  return { subtotal, total: subtotal + shippingFee }
}

export interface CheckoutLine {
  variantId: number
  sku: string
  quantity: number
}

export interface LockedVariant {
  id: number
  sku: string
  price: number
  stock: number
  productId: number
  productName: string
  productStatus: CatalogStatus
  size: string
  color: string
}

export interface UnavailableDetail {
  sku: string
  reason: 'PRODUCT_INACTIVE' | 'VARIANT_MISSING'
}

export interface OutOfStockDetail {
  sku: string
  requested: number
  available: number
}

export interface ResolvedLine extends CheckoutLine {
  unitPrice: number
  productId: number
  productName: string
  size: string
  color: string
}

export interface CheckoutFailures {
  unavailable: UnavailableDetail[]
  outOfStock: OutOfStockDetail[]
  ready: ResolvedLine[]
}

/**
 * Every failing variant is reported so the client can show all of them at once; a line that
 * passed the guard comes back in `ready`, already resolved against the locked rows.
 */
export function collectCheckoutFailures(
  lines: readonly CheckoutLine[],
  variants: ReadonlyMap<number, LockedVariant>,
): CheckoutFailures {
  const unavailable: UnavailableDetail[] = []
  const outOfStock: OutOfStockDetail[] = []
  const ready: ResolvedLine[] = []

  for (const line of lines) {
    const variant = variants.get(line.variantId)
    if (!variant) {
      unavailable.push({ sku: line.sku, reason: 'VARIANT_MISSING' })
      continue
    }
    if (variant.productStatus !== 'ACTIVE') {
      unavailable.push({ sku: variant.sku, reason: 'PRODUCT_INACTIVE' })
      continue
    }
    if (variant.stock < line.quantity) {
      outOfStock.push({ sku: variant.sku, requested: line.quantity, available: variant.stock })
      continue
    }
    ready.push({
      variantId: variant.id,
      sku: variant.sku,
      quantity: line.quantity,
      unitPrice: variant.price,
      productId: variant.productId,
      productName: variant.productName,
      size: variant.size,
      color: variant.color,
    })
  }

  return { unavailable, outOfStock, ready }
}

export type DerivedPaymentStatus = 'PAID' | 'FAILED' | 'UNPAID'

/** `paid_at` is the only source of truth for "paid"; a later failed attempt never undoes it. */
export function derivePaymentStatus(
  paidAt: Date | null,
  latestAttempt: { status: PaymentStatus } | undefined,
): DerivedPaymentStatus {
  if (paidAt) return 'PAID'
  if (latestAttempt?.status === 'FAILED') return 'FAILED'
  return 'UNPAID'
}

export const ORDER_STATUSES = [
  'PENDING',
  'CONFIRMED',
  'SHIPPING',
  'DELIVERED',
  'CANCELLED',
] as const satisfies readonly OrderStatus[]

/** `DELIVERED` and `CANCELLED` are terminal, so a restock can never run twice. */
export const ORDER_TRANSITIONS: Record<OrderStatus, readonly OrderStatus[]> = {
  PENDING: ['CONFIRMED', 'CANCELLED'],
  CONFIRMED: ['SHIPPING', 'CANCELLED'],
  SHIPPING: ['DELIVERED'],
  DELIVERED: [],
  CANCELLED: [],
}

export function canTransition(from: OrderStatus, to: OrderStatus): boolean {
  return ORDER_TRANSITIONS[from].includes(to)
}

