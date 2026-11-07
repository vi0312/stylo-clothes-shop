export interface CartItemDto {
  id: number
  variantId: number
  sku: string
  productId: number
  productName: string
  imageUrl: string | null
  size: string
  color: string
  unitPrice: number
  quantity: number
  lineTotal: number
  availableStock: number
  available: boolean
}

export interface CartDto {
  id: number | null
  items: CartItemDto[]
  itemCount: number
  subtotal: number
}

export function emptyCart(): CartDto {
  return { id: null, items: [], itemCount: 0, subtotal: 0 }
}
