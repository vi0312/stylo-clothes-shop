import type { Prisma } from '../../generated/prisma/client.js'
import { prisma } from '../../lib/prisma.js'

export const cartInclude = {
  items: {
    orderBy: { id: 'asc' },
    include: {
      variant: {
        include: {
          product: {
            include: {
              images: {
                where: { position: 0 },
                select: { url: true },
              },
            },
          },
        },
      },
    },
  },
} satisfies Prisma.CartInclude

export type CartRow = Prisma.CartGetPayload<{ include: typeof cartInclude }>
export type CartItemRow = CartRow['items'][number]

export const variantForCartInclude = {
  product: {
    include: {
      images: {
        where: { position: 0 },
        select: { url: true },
      },
    },
  },
} satisfies Prisma.ProductVariantInclude

export type VariantForCartRow = Prisma.ProductVariantGetPayload<{
  include: typeof variantForCartInclude
}>

export function findCartByUserId(userId: number): Promise<CartRow | null> {
  return prisma.cart.findUnique({ where: { userId }, include: cartInclude })
}

export function findVariantForCart(
  tx: Prisma.TransactionClient,
  variantId: number,
): Promise<VariantForCartRow | null> {
  return tx.productVariant.findUnique({
    where: { id: variantId },
    include: variantForCartInclude,
  })
}

/**
 * The cart row is the per-customer mutex: concurrent adds by the same user serialize here,
 * so the read-modify-write of `quantity` cannot lose an increment.
 */
export async function lockCart(tx: Prisma.TransactionClient, userId: number): Promise<void> {
  await tx.$queryRaw`SELECT id FROM cart WHERE user_id = ${userId} FOR UPDATE`
}

export async function ensureCart(tx: Prisma.TransactionClient, userId: number): Promise<number> {
  const cart = await tx.cart.upsert({
    where: { userId },
    create: { userId },
    update: {},
    select: { id: true },
  })
  await lockCart(tx, userId)
  return cart.id
}

export function findCartItem(
  tx: Prisma.TransactionClient,
  cartId: number,
  variantId: number,
): Promise<{ id: number; quantity: number } | null> {
  return tx.cartItem.findUnique({
    where: { cartId_variantId: { cartId, variantId } },
    select: { id: true, quantity: true },
  })
}

export function setCartItemQuantity(
  tx: Prisma.TransactionClient,
  itemId: number,
  quantity: number,
): Promise<unknown> {
  return tx.cartItem.update({ where: { id: itemId }, data: { quantity } })
}

export function createCartItem(
  tx: Prisma.TransactionClient,
  cartId: number,
  variantId: number,
  quantity: number,
): Promise<unknown> {
  return tx.cartItem.create({ data: { cartId, variantId, quantity } })
}

export function findOwnedCartItem(
  tx: Prisma.TransactionClient,
  userId: number,
  itemId: number,
): Promise<{
  id: number
  quantity: number
  variant: { sku: string; stock: number }
} | null> {
  return tx.cartItem.findFirst({
    where: { id: itemId, cart: { userId } },
    select: {
      id: true,
      quantity: true,
      variant: { select: { sku: true, stock: true } },
    },
  })
}

export function updateCartItemQuantity(
  tx: Prisma.TransactionClient,
  itemId: number,
  quantity: number,
): Promise<unknown> {
  return tx.cartItem.update({ where: { id: itemId }, data: { quantity } })
}

export async function deleteOwnedCartItem(userId: number, itemId: number): Promise<number> {
  const result = await prisma.cartItem.deleteMany({
    where: { id: itemId, cart: { userId } },
  })
  return result.count
}

export async function deleteCartByUserId(userId: number): Promise<void> {
  await prisma.cart.deleteMany({ where: { userId } })
}
