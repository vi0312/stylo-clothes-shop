import type { OrderStatus } from '../../generated/prisma/client.js'
import { prisma } from '../../lib/prisma.js'
import type { ReportGroupBy } from './reports.dto.js'

/** `DATE_FORMAT` returns a string, so the period key never depends on the driver's date handling. */
const REVENUE_PERIOD_FORMAT: Record<ReportGroupBy, string> = {
  day: '%Y-%m-%d',
  month: '%Y-%m',
}

export interface RevenueRow {
  period: string
  revenue: unknown
  orders: unknown
}

export async function revenueByPeriod(
  fromUtc: Date,
  toUtc: Date,
  groupBy: ReportGroupBy,
): Promise<RevenueRow[]> {
  return prisma.$queryRaw<RevenueRow[]>`
    SELECT DATE_FORMAT(paid_at + INTERVAL 7 HOUR, ${REVENUE_PERIOD_FORMAT[groupBy]}) AS period,
           SUM(total) AS revenue,
           COUNT(*) AS orders
    FROM \`order\`
    WHERE paid_at >= ${fromUtc} AND paid_at < ${toUtc}
    GROUP BY period
    ORDER BY period
  `
}

export interface TopProductRow {
  productId: number
  name: string
  quantitySold: unknown
  revenue: unknown
}

export async function topProducts(
  fromUtc: Date,
  toUtc: Date,
  limit: number,
): Promise<TopProductRow[]> {
  return prisma.$queryRaw<TopProductRow[]>`
    WITH agg AS (
      SELECT oi.product_id,
             SUM(oi.quantity) AS quantitySold,
             SUM(oi.unit_price * oi.quantity) AS revenue
      FROM order_item oi
      JOIN \`order\` o ON o.id = oi.order_id
      WHERE o.paid_at >= ${fromUtc} AND o.paid_at < ${toUtc}
      GROUP BY oi.product_id
    ), latest AS (
      SELECT oi.product_id, oi.product_name,
             ROW_NUMBER() OVER (PARTITION BY oi.product_id ORDER BY o.paid_at DESC, oi.id DESC) AS rn
      FROM order_item oi
      JOIN \`order\` o ON o.id = oi.order_id
      WHERE o.paid_at >= ${fromUtc} AND o.paid_at < ${toUtc}
    )
    SELECT a.product_id AS productId, l.product_name AS name, a.quantitySold, a.revenue
    FROM agg a
    JOIN latest l ON l.product_id = a.product_id AND l.rn = 1
    ORDER BY a.quantitySold DESC, a.revenue DESC, l.product_name ASC
    LIMIT ${limit}
  `
}

export interface OrdersByStatusRow {
  status: OrderStatus
  count: unknown
  total: unknown
}

export async function ordersByStatus(
  fromUtc: Date,
  toUtc: Date,
): Promise<OrdersByStatusRow[]> {
  return prisma.$queryRaw<OrdersByStatusRow[]>`
    SELECT status, COUNT(*) AS count, COALESCE(SUM(total), 0) AS total
    FROM \`order\`
    WHERE created_at >= ${fromUtc} AND created_at < ${toUtc}
    GROUP BY status
  `
}

export interface LowStockRow {
  variantId: number
  sku: string
  productName: string
  size: string
  color: string
  stock: number
}

export async function lowStock(threshold: number): Promise<LowStockRow[]> {
  return prisma.$queryRaw<LowStockRow[]>`
    SELECT v.id AS variantId, v.sku, p.name AS productName, v.size, v.color, v.stock
    FROM product_variant v
    JOIN product p ON p.id = v.product_id
    WHERE p.status = 'ACTIVE' AND v.stock <= ${threshold}
    ORDER BY v.stock ASC, v.sku ASC
  `
}

export async function sumReceiptsCost(fromUtc: Date, toUtc: Date): Promise<unknown> {
  const rows = await prisma.$queryRaw<{ receiptsCost: unknown }[]>`
    SELECT COALESCE(SUM(iri.quantity * iri.unit_cost), 0) AS receiptsCost
    FROM inventory_receipt_item iri
    JOIN inventory_receipt ir ON ir.id = iri.receipt_id
    WHERE ir.created_at >= ${fromUtc} AND ir.created_at < ${toUtc}
  `
  return rows[0]?.receiptsCost
}

export async function sumCogs(fromUtc: Date, toUtc: Date): Promise<unknown> {
  const rows = await prisma.$queryRaw<{ cogs: unknown }[]>`
    SELECT COALESCE(SUM(oi.quantity * COALESCE(oi.unit_cost, 0)), 0) AS cogs
    FROM order_item oi
    JOIN \`order\` o ON o.id = oi.order_id
    WHERE o.paid_at >= ${fromUtc} AND o.paid_at < ${toUtc}
  `
  return rows[0]?.cogs
}

export async function sumRevenue(fromUtc: Date, toUtc: Date): Promise<unknown> {
  const rows = await prisma.$queryRaw<{ revenue: unknown }[]>`
    SELECT COALESCE(SUM(total), 0) AS revenue
    FROM \`order\`
    WHERE paid_at >= ${fromUtc} AND paid_at < ${toUtc}
  `
  return rows[0]?.revenue
}
