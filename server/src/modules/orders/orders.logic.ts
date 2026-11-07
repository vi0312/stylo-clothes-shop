import { randomInt } from 'node:crypto'
import type { CatalogStatus, OrderStatus, PaymentStatus } from '../../generated/prisma/client.js'

const ORDER_CODE_ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789'
const ORDER_CODE_SUFFIX_LENGTH = 4

/** `order.code` is a display/reconciliation code dated in the business time zone. */
const ORDER_TIME_ZONE = 'Asia/Ho_Chi_Minh'

export function orderCode(now: Date, nextInt: (max: number) => number = randomInt): string {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: ORDER_TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(now)

  const part = (type: Intl.DateTimeFormatPartTypes): string =>
    parts.find((entry) => entry.type === type)?.value ?? ''

  let suffix = ''
  for (let index = 0; index < ORDER_CODE_SUFFIX_LENGTH; index += 1) {
    suffix += ORDER_CODE_ALPHABET[nextInt(ORDER_CODE_ALPHABET.length)]
  }

  return `STY-${part('year').slice(-2)}${part('month')}${part('day')}-${suffix}`
}

export interface OrderTotals {
  subtotal: number
  total: number
}

export function computeTotals(
  lines: readonly { unitPrice: number; quantity: number }[],
  shippingFee: number,
): OrderTotals {
  const subtotal = lines.reduce((sum, line) => sum + line.unitPrice * line.quantity, 0)
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

/** Vietnam has no DST, so a calendar day always starts at the same UTC offset. */
const BUSINESS_UTC_OFFSET_MS = 7 * 60 * 60 * 1000
const DAY_MS = 24 * 60 * 60 * 1000

export interface BusinessDayWindow {
  gte?: Date
  lt?: Date
}

/**
 * `from`/`to` are `YYYY-MM-DD` calendar dates in `Asia/Ho_Chi_Minh`; the window is
 * half-open `[from 00:00, (to + 1) 00:00)` so both ends include the whole day.
 */
export function businessDayWindow(from?: string, to?: string): BusinessDayWindow {
  const window: BusinessDayWindow = {}
  if (from) window.gte = businessDayStart(from)
  if (to) window.lt = new Date(businessDayStart(to).getTime() + DAY_MS)
  return window
}

function businessDayStart(date: string): Date {
  const [year, month, day] = date.split('-').map(Number)
  return new Date(Date.UTC(year, month - 1, day) - BUSINESS_UTC_OFFSET_MS)
}
