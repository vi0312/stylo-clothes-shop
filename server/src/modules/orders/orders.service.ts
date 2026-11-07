import { getEnv } from '../../config/env.js'
import { Prisma, type OrderStatus } from '../../generated/prisma/client.js'
import { prisma } from '../../lib/prisma.js'
import { isUniqueViolation } from '../../lib/prismaErrors.js'
import { conflict, notFound } from '../../utils/errors.js'
import { lockCart } from '../cart/cart.repository.js'
import type { PaymentAttemptDto } from '../payments/payments.dto.js'
import { lockOrder, type LockedOrderRow } from '../payments/payments.repository.js'
import { createAttemptInTx, toPayment, toPaymentAttempt } from '../payments/payments.service.js'
import type {
  AdminOrderDetailDto,
  AdminOrderSummaryDto,
  OrderDto,
  OrderSummaryDto,
  Paginated,
} from './orders.dto.js'
import type {
  CreateOrderInput,
  ListAdminOrdersQuery,
  ListOrdersQuery,
} from './orders.schema.js'
import {
  canTransition,
  collectCheckoutFailures,
  computeTotals,
  derivePaymentStatus,
  orderCode,
  type LockedVariant,
} from './orders.logic.js'
import * as repository from './orders.repository.js'

const MAX_ORDER_CODE_ATTEMPTS = 3

export interface CreateOrderResult {
  order: OrderDto
  payment: PaymentAttemptDto | null
  created: boolean
}

/**
 * A repeated `clientOrderId` replays only a still-pending attempt; a failed one is left for
 * `POST /orders/:id/payments`, which expires and replaces it.
 */
function currentAttempt(row: repository.OrderRow): PaymentAttemptDto | null {
  if (row.paymentMethod !== 'ONLINE') return null
  const latest = row.payments[0]
  if (!latest || latest.status !== 'PENDING') return null
  return toPaymentAttempt(latest, row.code)
}

function toOrderDto(row: repository.OrderRow): OrderDto {
  return {
    id: row.id,
    code: row.code,
    status: row.status,
    paymentMethod: row.paymentMethod,
    paymentStatus: derivePaymentStatus(row.paidAt, row.payments[0]),
    subtotal: row.subtotal,
    shippingFee: row.shippingFee,
    total: row.total,
    note: row.note,
    recipientName: row.recipientName,
    recipientPhone: row.recipientPhone,
    shippingAddress: row.shippingAddress,
    items: row.items.map((item) => ({
      id: item.id,
      productId: item.productId,
      variantId: item.variantId,
      productName: item.productName,
      sku: item.variantSku,
      size: item.size,
      color: item.color,
      unitPrice: item.unitPrice,
      quantity: item.quantity,
      lineTotal: item.unitPrice * item.quantity,
    })),
    paidAt: row.paidAt?.toISOString() ?? null,
    createdAt: row.createdAt.toISOString(),
    updatedAt: row.updatedAt.toISOString(),
  }
}

async function placeOrder(userId: number, input: CreateOrderInput): Promise<CreateOrderResult> {
  const shippingFee = getEnv().SHIPPING_FEE

  return prisma.$transaction(async (tx) => {
    // Same mutex the cart uses: a concurrent add or a duplicate retry serializes here.
    await lockCart(tx, userId)

    const duplicate = await repository.findOrderByClientOrderIdInTx(
      tx,
      userId,
      input.clientOrderId,
    )
    if (duplicate) {
      return {
        order: toOrderDto(duplicate),
        payment: currentAttempt(duplicate),
        created: false,
      }
    }

    const cart = await repository.findCartForCheckout(tx, userId)
    if (!cart || cart.items.length === 0) {
      throw conflict('CART_EMPTY', 'The cart is empty.')
    }

    const address = await repository.findOwnedAddress(tx, userId, input.addressId)
    if (!address) {
      throw notFound('NOT_FOUND', 'Address not found')
    }

    const variantIds = [...new Set(cart.items.map((item) => item.variantId))]
    const locked = await repository.lockCheckoutVariants(tx, variantIds)
    const variantsById = new Map<number, LockedVariant>(
      locked.map((variant) => [variant.id, variant]),
    )

    const failures = collectCheckoutFailures(
      cart.items.map((item) => ({
        variantId: item.variantId,
        sku: item.variant.sku,
        quantity: item.quantity,
      })),
      variantsById,
    )

    if (failures.unavailable.length > 0) {
      throw conflict(
        'ITEM_UNAVAILABLE',
        'Some items are no longer available.',
        failures.unavailable,
      )
    }
    if (failures.outOfStock.length > 0) {
      throw conflict('OUT_OF_STOCK', 'Not enough stock for the requested items.', failures.outOfStock)
    }

    const costs = await repository.findWeightedAverageCosts(tx, variantIds)
    const totals = computeTotals(failures.ready, shippingFee)

    const order = await repository.createOrder(tx, {
      code: orderCode(new Date()),
      userId,
      clientOrderId: input.clientOrderId,
      paymentMethod: input.paymentMethod,
      recipientName: address.recipientName,
      recipientPhone: address.phone,
      shippingAddress: address.line,
      subtotal: totals.subtotal,
      shippingFee,
      total: totals.total,
      note: input.note === undefined || input.note === '' ? null : input.note,
      items: failures.ready.map((line) => ({
        productId: line.productId,
        variantId: line.variantId,
        productName: line.productName,
        variantSku: line.sku,
        size: line.size,
        color: line.color,
        unitPrice: line.unitPrice,
        unitCost: costs.get(line.variantId) ?? null,
        quantity: line.quantity,
      })),
    })

    await repository.createSaleMovements(tx, order.id, failures.ready)
    for (const line of failures.ready) {
      await repository.decrementStock(tx, line.variantId, line.quantity)
    }
    await repository.deleteCartItems(tx, cart.id)

    // `ONLINE` and its first attempt commit together (erd.md, "Place order" step 7).
    const payment =
      input.paymentMethod === 'ONLINE' ? await createAttemptInTx(tx, order) : null

    return { order: toOrderDto(order), payment, created: true }
  })
}

export async function createOrder(
  userId: number,
  input: CreateOrderInput,
): Promise<CreateOrderResult> {
  const existing = await repository.findOrderByClientOrderId(userId, input.clientOrderId)
  if (existing) {
    return {
      order: toOrderDto(existing),
      payment: currentAttempt(existing),
      created: false,
    }
  }

  for (let attempt = 1; ; attempt += 1) {
    try {
      return await placeOrder(userId, input)
    } catch (error) {
      if (!isUniqueViolation(error)) throw error

      const duplicate = await repository.findOrderByClientOrderId(userId, input.clientOrderId)
      if (duplicate) {
        return {
        order: toOrderDto(duplicate),
        payment: currentAttempt(duplicate),
        created: false,
      }
      }
      // Not the idempotency key, so the generated order code collided: retry with a fresh one.
      if (attempt < MAX_ORDER_CODE_ATTEMPTS) continue
      throw error
    }
  }
}

function toOrderSummary(row: repository.OrderSummaryRow): OrderSummaryDto {
  return {
    id: row.id,
    code: row.code,
    status: row.status,
    paymentMethod: row.paymentMethod,
    paymentStatus: derivePaymentStatus(row.paidAt, row.payments[0]),
    total: row.total,
    itemCount: row.items.reduce((sum, item) => sum + item.quantity, 0),
    createdAt: row.createdAt.toISOString(),
  }
}

function toAdminOrderSummary(row: repository.AdminOrderSummaryRow): AdminOrderSummaryDto {
  return {
    ...toOrderSummary(row),
    customerName: row.user.fullName,
    recipientName: row.recipientName,
    recipientPhone: row.recipientPhone,
  }
}

function toAdminOrderDetail(row: repository.AdminOrderDetailRow): AdminOrderDetailDto {
  return {
    id: row.id,
    code: row.code,
    status: row.status,
    paymentMethod: row.paymentMethod,
    paymentStatus: derivePaymentStatus(row.paidAt, row.payments[0]),
    customer: row.user,
    subtotal: row.subtotal,
    shippingFee: row.shippingFee,
    total: row.total,
    note: row.note,
    recipientName: row.recipientName,
    recipientPhone: row.recipientPhone,
    shippingAddress: row.shippingAddress,
    items: row.items.map((item) => ({
      id: item.id,
      productId: item.productId,
      variantId: item.variantId,
      productName: item.productName,
      sku: item.variantSku,
      size: item.size,
      color: item.color,
      unitPrice: item.unitPrice,
      unitCost: item.unitCost,
      quantity: item.quantity,
      lineTotal: item.unitPrice * item.quantity,
    })),
    payments: row.payments.map(toPayment),
    paidAt: row.paidAt?.toISOString() ?? null,
    createdAt: row.createdAt.toISOString(),
    updatedAt: row.updatedAt.toISOString(),
  }
}

export async function listOrders(
  userId: number,
  query: ListOrdersQuery,
): Promise<Paginated<OrderSummaryDto>> {
  const { orders, total } = await repository.listOrdersForUser(userId, query)
  return { items: orders.map(toOrderSummary), page: query.page, limit: query.limit, total }
}

export async function getOrder(userId: number, orderId: number): Promise<OrderDto> {
  const row = await repository.findOrderForUser(userId, orderId)
  if (!row) throw notFound('NOT_FOUND', 'Order not found')
  return toOrderDto(row)
}

/** `DELIVERED` and `CANCELLED` are terminal, so a cancelled order can never restock twice. */
function assertCancellable(row: LockedOrderRow): void {
  if (!canTransition(row.status, 'CANCELLED')) {
    throw conflict('INVALID_ORDER_STATE', `Cannot cancel an order in ${row.status}`, {
      current: row.status,
      target: 'CANCELLED',
    })
  }
  if (row.paidAt !== null) {
    throw conflict('ORDER_ALREADY_PAID', 'This order is already paid')
  }
}

async function cancelLocked(tx: Prisma.TransactionClient, orderId: number): Promise<void> {
  const items = await repository.orderItemsForRestock(tx, orderId)
  await repository.restock(tx, orderId, items)
  await repository.setOrderStatus(tx, orderId, { status: 'CANCELLED' })
}

/** Customer cancel: the order must be theirs, unpaid and still cancellable. */
export async function cancelOrder(userId: number, orderId: number): Promise<OrderDto> {
  await prisma.$transaction(async (tx) => {
    const row = await lockOrder(tx, orderId)
    if (!row || row.userId !== userId) throw notFound('NOT_FOUND', 'Order not found')
    assertCancellable(row)
    await cancelLocked(tx, orderId)
  })

  return getOrder(userId, orderId)
}

export async function listAdminOrders(
  query: ListAdminOrdersQuery,
): Promise<Paginated<AdminOrderSummaryDto>> {
  const { orders, total } = await repository.listAdminOrders(query)
  return { items: orders.map(toAdminOrderSummary), page: query.page, limit: query.limit, total }
}

export async function getAdminOrder(orderId: number): Promise<AdminOrderDetailDto> {
  const row = await repository.findAdminOrder(orderId)
  if (!row) throw notFound('NOT_FOUND', 'Order not found')
  return toAdminOrderDetail(row)
}

export async function changeOrderStatus(
  orderId: number,
  target: OrderStatus,
): Promise<AdminOrderDetailDto> {
  await prisma.$transaction(async (tx) => {
    const row = await lockOrder(tx, orderId)
    if (!row) throw notFound('NOT_FOUND', 'Order not found')

    if (!canTransition(row.status, target)) {
      throw conflict(
        'INVALID_ORDER_STATE',
        `Cannot move an order from ${row.status} to ${target}`,
        { current: row.status, target },
      )
    }

    if (target === 'CANCELLED') {
      assertCancellable(row)
      await cancelLocked(tx, orderId)
      return
    }

    // COD settles at delivery; an online order's `paid_at` comes from its payment attempt.
    const paidAt =
      target === 'DELIVERED' && row.paymentMethod === 'COD' && row.paidAt === null
        ? new Date()
        : undefined
    await repository.setOrderStatus(tx, orderId, { status: target, ...(paidAt ? { paidAt } : {}) })
  })

  return getAdminOrder(orderId)
}
