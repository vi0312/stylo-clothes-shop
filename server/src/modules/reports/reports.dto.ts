import type { OrderStatus } from '../../generated/prisma/client.js'

export type ReportGroupBy = 'day' | 'month'

export interface RevenuePointDto {
  period: string
  revenue: number
  orders: number
}

export interface RevenueReportDto {
  from: string
  to: string
  groupBy: ReportGroupBy
  totalRevenue: number
  totalOrders: number
  series: RevenuePointDto[]
}

export interface TopProductDto {
  productId: number
  name: string
  quantitySold: number
  revenue: number
}

export interface TopProductsReportDto {
  from: string
  to: string
  items: TopProductDto[]
}

export interface OrdersByStatusItemDto {
  status: OrderStatus
  count: number
  total: number
}

export interface OrdersByStatusReportDto {
  from: string
  to: string
  items: OrdersByStatusItemDto[]
}

export interface LowStockItemDto {
  variantId: number
  sku: string
  productName: string
  size: string
  color: string
  stock: number
}

export interface LowStockReportDto {
  threshold: number
  items: LowStockItemDto[]
}

export interface InventoryCostReportDto {
  from: string
  to: string
  receiptsCost: number
  cogs: number
  revenue: number
  grossProfit: number
}
