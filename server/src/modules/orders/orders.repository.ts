import { Prisma, type CatalogStatus } from '../../generated/prisma/client.js'
import { prisma } from '../../lib/prisma.js'

export const orderInclude = {
  items: { orderBy: { id: 'asc' } },
  payments: { orderBy: [{ createdAt: 'desc' }, { id: 'desc' }], take: 1 },
} satisfies Prisma.OrderInclude

export type OrderRow = Prisma.OrderGetPayload<{ include: typeof orderInclude }>

const checkoutCartInclude = {
  items: {
    orderBy: { id: 'asc' },
    include: {
      variant: {
        include: { product: { select: { id: true, name: true, status: true } } },
      },
    },
  },
} satisfies Prisma.CartInclude

export type CheckoutCartRow = Prisma.CartGetPayload<{ include: typeof checkoutCartInclude }>

export interface LockedVariantRow {
  id: number
  sku: string
  price: number
  stock: number
  size: string
  color: string
  productId: number
  productName: string
  productStatus: CatalogStatus
}

/**
 * One locking read for the guard and the snapshot: `FOR UPDATE` is a *current* read, so it
 * sees stock committed by a concurrent checkout. A snapshot read here would pass a stale
 * stock and the decrement would then trip the `stock >= 0` check constraint instead of
 * returning `OUT_OF_STOCK`. Ascending `id` keeps two checkouts sharing variants from
 * deadlocking, and `OF v` avoids locking the joined product row.
 */
export async function lockCheckoutVariants(
  tx: Prisma.TransactionClient,
  variantIds: readonly number[],
): Promise<LockedVariantRow[]> {
  if (variantIds.length === 0) return []
  const ordered = [...variantIds].sort((left, right) => left - right)

  return tx.$queryRaw<LockedVariantRow[]>`
    SELECT v.id AS id,
           v.sku AS sku,
           v.price AS price,
           v.stock AS stock,
           v.size AS size,
           v.color AS color,
           v.product_id AS productId,
           p.name AS productName,
           p.status AS productStatus
    FROM product_variant v
    JOIN product p ON p.id = v.product_id
    WHERE v.id IN (${Prisma.join(ordered)})
    ORDER BY v.id
    FOR UPDATE OF v
  `
}

export function findOrderByClientOrderId(
  userId: number,
  clientOrderId: string,
): Promise<OrderRow | null> {
  return prisma.order.findUnique({
    where: { userId_clientOrderId: { userId, clientOrderId } },
    include: orderInclude,
  })
}

export function findOrderByClientOrderIdInTx(
  tx: Prisma.TransactionClient,
  userId: number,
  clientOrderId: string,
): Promise<OrderRow | null> {
  return tx.order.findUnique({
    where: { userId_clientOrderId: { userId, clientOrderId } },
    include: orderInclude,
  })
}

export function findCartForCheckout(
  tx: Prisma.TransactionClient,
  userId: number,
): Promise<CheckoutCartRow | null> {
  return tx.cart.findUnique({ where: { userId }, include: checkoutCartInclude })
}

export function findOwnedAddress(
  tx: Prisma.TransactionClient,
  userId: number,
  addressId: number,
): Promise<{ recipientName: string; phone: string; line: string } | null> {
  return tx.address.findFirst({
    where: { id: addressId, userId },
    select: { recipientName: true, phone: true, line: true },
  })
}

/**
 * Weighted average over every receipt ever booked for the variant, so the value cannot be
 * skewed by editing a receipt. The only cost definition checkout uses (see docs/erd.md).
 */
export async function findWeightedAverageCosts(
  tx: Prisma.TransactionClient,
  variantIds: readonly number[],
): Promise<Map<number, number>> {
  if (variantIds.length === 0) return new Map()

  const rows = await tx.$queryRaw<{ variantId: number | bigint; avgCost: unknown }[]>`
    SELECT variant_id AS variantId,
           ROUND(SUM(quantity * unit_cost) / SUM(quantity)) AS avgCost
    FROM inventory_receipt_item
    WHERE variant_id IN (${Prisma.join([...variantIds])})
    GROUP BY variant_id
  `

  return new Map(rows.map((row) => [Number(row.variantId), Math.round(Number(row.avgCost))]))
}

export interface NewOrderItem {
  productId: number
  variantId: number
  productName: string
  variantSku: string
  size: string
  color: string
  unitPrice: number
  unitCost: number | null
  quantity: number
}

export interface NewOrder {
  code: string
  userId: number
  clientOrderId: string
  paymentMethod: 'COD' | 'ONLINE'
  recipientName: string
  recipientPhone: string
  shippingAddress: string
  subtotal: number
  shippingFee: number
  total: number
  note: string | null
  items: NewOrderItem[]
}

export function createOrder(tx: Prisma.TransactionClient, data: NewOrder): Promise<OrderRow> {
  return tx.order.create({
    data: {
      code: data.code,
      userId: data.userId,
      clientOrderId: data.clientOrderId,
      paymentMethod: data.paymentMethod,
      recipientName: data.recipientName,
      recipientPhone: data.recipientPhone,
      shippingAddress: data.shippingAddress,
      subtotal: data.subtotal,
      shippingFee: data.shippingFee,
      total: data.total,
      note: data.note,
      items: { create: data.items },
    },
    include: orderInclude,
  })
}

export function createSaleMovements(
  tx: Prisma.TransactionClient,
  orderId: number,
  lines: readonly { variantId: number; quantity: number }[],
): Promise<unknown> {
  return tx.stockMovement.createMany({
    data: lines.map((line) => ({
      variantId: line.variantId,
      delta: -line.quantity,
      reason: 'SALE',
      refType: 'ORDER',
      refId: orderId,
      // Sales carry no receipt cost; `order_item.unit_cost` is the snapshot that reports use.
      unitCost: null,
    })),
  })
}

export function decrementStock(
  tx: Prisma.TransactionClient,
  variantId: number,
  quantity: number,
): Promise<unknown> {
  return tx.productVariant.update({
    where: { id: variantId },
    data: { stock: { decrement: quantity } },
  })
}

export function deleteCartItems(tx: Prisma.TransactionClient, cartId: number): Promise<unknown> {
  return tx.cartItem.deleteMany({ where: { cartId } })
}
