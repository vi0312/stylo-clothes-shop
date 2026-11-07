export interface InventoryReceiptItemDto {
  id: number
  variantId: number
  sku: string
  productName: string
  size: string
  color: string
  quantity: number
  unitCost: number
  lineTotal: number
}

export interface InventoryReceiptCreatorDto {
  id: number
  fullName: string
}

export interface InventoryReceiptSummaryDto {
  id: number
  code: string
  supplier: string
  note: string | null
  totalCost: number
  /** Number of lines on the receipt, not the sum of their quantities. */
  itemCount: number
  createdBy: InventoryReceiptCreatorDto
  createdAt: string
}

export interface InventoryReceiptDetailDto extends InventoryReceiptSummaryDto {
  items: InventoryReceiptItemDto[]
}

export interface Paginated<T> {
  items: T[]
  page: number
  limit: number
  total: number
}
