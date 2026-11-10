import { prisma } from '../../lib/prisma.js'
import { shippingFeeFor } from '../../lib/shipping.js'
import { conflict, notFound } from '../../utils/errors.js'
import { emptyCart, type CartDto, type CartItemDto } from './cart.dto.js'
import * as repository from './cart.repository.js'
import type { AddCartItemInput } from './cart.schema.js'

function toCartDto(cart: repository.CartRow): CartDto {
  const items: CartItemDto[] = cart.items.map((item) => {
    const product = item.variant.product
    const unitPrice = item.variant.price
    return {
      id: item.id,
      variantId: item.variantId,
      sku: item.variant.sku,
      productId: product.id,
      productName: product.name,
      imageUrl: product.images[0]?.url ?? null,
      size: item.variant.size,
      color: item.variant.color,
      unitPrice,
      quantity: item.quantity,
      lineTotal: unitPrice * item.quantity,
      availableStock: item.variant.stock,
      // Inactive products stay in the cart as a warning line, but never count toward totals.
      available: product.status === 'ACTIVE',
    }
  })

  const subtotal = items
    .filter((item) => item.available)
    .reduce((sum, item) => sum + item.lineTotal, 0)
  // Nothing billable ships, so a cart of inactive lines is not quoted a fee.
  const billable = items.some((item) => item.available)

  return {
    id: cart.id,
    items,
    itemCount: items.reduce((sum, item) => sum + item.quantity, 0),
    subtotal,
    shippingFee: billable ? shippingFeeFor(subtotal) : 0,
  }
}

export async function getCart(userId: number): Promise<CartDto> {
  const cart = await repository.findCartByUserId(userId)
  return cart ? toCartDto(cart) : emptyCart()
}

export async function addItem(userId: number, input: AddCartItemInput): Promise<CartDto> {
  await prisma.$transaction(async (tx) => {
    const variant = await repository.findVariantForCart(tx, input.variantId)
    if (!variant) {
      throw notFound('NOT_FOUND', 'Variant not found')
    }
    if (variant.product.status !== 'ACTIVE') {
      throw conflict('ITEM_UNAVAILABLE', 'The product is no longer available.', [
        { sku: variant.sku, reason: 'PRODUCT_INACTIVE' },
      ])
    }

    const cartId = await repository.ensureCart(tx, userId)
    const existing = await repository.findCartItem(tx, cartId, variant.id)
    const quantity = (existing?.quantity ?? 0) + input.quantity
    if (quantity > variant.stock) {
      throw conflict('OUT_OF_STOCK', 'Not enough stock for the requested items.', [
        { sku: variant.sku, requested: quantity, available: variant.stock },
      ])
    }

    if (existing) {
      await repository.setCartItemQuantity(tx, existing.id, quantity)
    } else {
      await repository.createCartItem(tx, cartId, variant.id, quantity)
    }
  })

  return getCart(userId)
}

export async function updateItemQuantity(
  userId: number,
  itemId: number,
  quantity: number,
): Promise<CartDto> {
  await prisma.$transaction(async (tx) => {
    await repository.lockCart(tx, userId)
    const item = await repository.findOwnedCartItem(tx, userId, itemId)
    if (!item) {
      throw notFound('NOT_FOUND', 'Cart item not found')
    }
    if (quantity > item.variant.stock) {
      throw conflict('OUT_OF_STOCK', 'Not enough stock for the requested items.', [
        { sku: item.variant.sku, requested: quantity, available: item.variant.stock },
      ])
    }
    await repository.updateCartItemQuantity(tx, itemId, quantity)
  })

  return getCart(userId)
}

export async function removeItem(userId: number, itemId: number): Promise<CartDto> {
  const deleted = await repository.deleteOwnedCartItem(userId, itemId)
  if (deleted === 0) {
    throw notFound('NOT_FOUND', 'Cart item not found')
  }
  return getCart(userId)
}

export async function clearCart(userId: number): Promise<CartDto> {
  await repository.deleteCartByUserId(userId)
  return emptyCart()
}
