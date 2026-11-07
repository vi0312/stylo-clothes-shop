import { getEnv } from '../../config/env.js'
import { businessDayWindow } from '../../lib/businessDay.js'
import type { OrderStatus } from '../../generated/prisma/client.js'
import type {
  InventoryCostReportDto,
  LowStockReportDto,
  OrdersByStatusItemDto,
  OrdersByStatusReportDto,
  RevenueReportDto,
  TopProductsReportDto,
} from './reports.dto.js'
import {
  grossProfit,
  periodsFor,
  resolveReportRange,
  toNumber,
  zeroFillPeriods,
} from './reports.logic.js'
import type {
  InventoryCostQuery,
  LowStockQuery,
  OrdersByStatusQuery,
  RevenueQuery,
  TopProductsQuery,
} from './reports.schema.js'
import * as repository from './reports.repository.js'

/** Histogram order of the contract; every status is present in the response. */
const ORDER_STATUSES: readonly OrderStatus[] = [
  'PENDING',
  'CONFIRMED',
  'SHIPPING',
  'DELIVERED',
  'CANCELLED',
]

function utcWindow(from: string, to: string): { gte: Date; lt: Date } {
  const { gte, lt } = businessDayWindow(from, to)
  if (!gte || !lt) throw new Error('report range must be fully resolved')
  return { gte, lt }
}

export async function revenueReport(query: RevenueQuery): Promise<RevenueReportDto> {
  const range = resolveReportRange(query.from, query.to)
  const { gte, lt } = utcWindow(range.from, range.to)

  const rows = await repository.revenueByPeriod(gte, lt, query.groupBy)
  const series = zeroFillPeriods(
    periodsFor(range.from, range.to, query.groupBy),
    rows.map((row) => ({
      period: String(row.period),
      revenue: toNumber(row.revenue),
      orders: toNumber(row.orders),
    })),
  )

  return {
    ...range,
    groupBy: query.groupBy,
    totalRevenue: series.reduce((sum, point) => sum + point.revenue, 0),
    totalOrders: series.reduce((sum, point) => sum + point.orders, 0),
    series,
  }
}

export async function topProductsReport(
  query: TopProductsQuery,
): Promise<TopProductsReportDto> {
  const range = resolveReportRange(query.from, query.to)
  const { gte, lt } = utcWindow(range.from, range.to)

  const rows = await repository.topProducts(gte, lt, query.limit)
  return {
    ...range,
    items: rows.map((row) => ({
      productId: row.productId,
      name: row.name,
      quantitySold: toNumber(row.quantitySold),
      revenue: toNumber(row.revenue),
    })),
  }
}

export async function ordersByStatusReport(
  query: OrdersByStatusQuery,
): Promise<OrdersByStatusReportDto> {
  const range = resolveReportRange(query.from, query.to)
  const { gte, lt } = utcWindow(range.from, range.to)

  const rows = await repository.ordersByStatus(gte, lt)
  const byStatus = new Map(rows.map((row) => [row.status, row]))
  const items: OrdersByStatusItemDto[] = ORDER_STATUSES.map((status) => {
    const row = byStatus.get(status)
    return { status, count: toNumber(row?.count), total: toNumber(row?.total) }
  })

  return { ...range, items }
}

export async function lowStockReport(query: LowStockQuery): Promise<LowStockReportDto> {
  const threshold = query.threshold ?? getEnv().LOW_STOCK_THRESHOLD
  return { threshold, items: await repository.lowStock(threshold) }
}

export async function inventoryCostReport(
  query: InventoryCostQuery,
): Promise<InventoryCostReportDto> {
  const range = resolveReportRange(query.from, query.to)
  const { gte, lt } = utcWindow(range.from, range.to)

  const [receiptsCost, cogs, revenue] = await Promise.all([
    repository.sumReceiptsCost(gte, lt),
    repository.sumCogs(gte, lt),
    repository.sumRevenue(gte, lt),
  ])

  const cogsValue = toNumber(cogs)
  const revenueValue = toNumber(revenue)
  return {
    ...range,
    receiptsCost: toNumber(receiptsCost),
    cogs: cogsValue,
    revenue: revenueValue,
    grossProfit: grossProfit(revenueValue, cogsValue),
  }
}
