import { Prisma } from '../../generated/prisma/client.js'
import { businessDayWindow } from '../../lib/businessDay.js'
import { prisma } from '../../lib/prisma.js'
import type { ListInventoryReceiptsQuery } from './inventory.schema.js'

const creatorSelect = { id: true, fullName: true } satisfies Prisma.UserSelect

const receiptItemInclude = {
  variant: {
    select: {
      sku: true,
      size: true,
      color: true,
      product: { select: { name: true } },
    },
  },
} satisfies Prisma.InventoryReceiptItemInclude

const detailInclude = {
  creator: { select: creatorSelect },
  items: { orderBy: { id: 'asc' }, include: receiptItemInclude },
} satisfies Prisma.InventoryReceiptInclude

const summaryInclude = {
  creator: { select: creatorSelect },
  _count: { select: { items: true } },
} satisfies Prisma.InventoryReceiptInclude

export type ReceiptDetailRow = Prisma.InventoryReceiptGetPayload<{ include: typeof detailInclude }>

export type ReceiptSummaryRow = Prisma.InventoryReceiptGetPayload<{
  include: typeof summaryInclude
}>

export type ReceiptItemRow = ReceiptDetailRow['items'][number]

export interface NewReceiptLine {
  variantId: number
  quantity: number
  unitCost: number
}

export interface NewReceipt {
  code: string
  supplier: string
  note: string | null
  totalCost: number
  createdBy: number
  items: NewReceiptLine[]
}

export function listInventoryReceipts(
  query: ListInventoryReceiptsQuery,
): Promise<{ receipts: ReceiptSummaryRow[]; total: number }> {
  const dayWindow = businessDayWindow(query.from, query.to)
  const where: Prisma.InventoryReceiptWhereInput =
    dayWindow.gte || dayWindow.lt
      ? {
          createdAt: {
            ...(dayWindow.gte ? { gte: dayWindow.gte } : {}),
            ...(dayWindow.lt ? { lt: dayWindow.lt } : {}),
          },
        }
      : {}

  const orderBy =
    query.sort === 'oldest'
      ? [{ createdAt: 'asc' as const }, { id: 'asc' as const }]
      : [{ createdAt: 'desc' as const }, { id: 'desc' as const }]

  return Promise.all([
    prisma.inventoryReceipt.count({ where }),
    prisma.inventoryReceipt.findMany({
      where,
      include: summaryInclude,
      orderBy,
      skip: (query.page - 1) * query.limit,
      take: query.limit,
    }),
  ]).then(([total, receipts]) => ({ receipts, total }))
}

export function findReceiptById(id: number): Promise<ReceiptDetailRow | null> {
  return prisma.inventoryReceipt.findUnique({ where: { id }, include: detailInclude })
}

export function findExistingVariantIds(
  tx: Prisma.TransactionClient,
  variantIds: readonly number[],
): Promise<{ id: number }[]> {
  return tx.productVariant.findMany({
    where: { id: { in: [...variantIds] } },
    select: { id: true },
  })
}

export function createReceipt(
  tx: Prisma.TransactionClient,
  receipt: NewReceipt,
): Promise<ReceiptDetailRow> {
  return tx.inventoryReceipt.create({
    data: {
      code: receipt.code,
      supplier: receipt.supplier,
      note: receipt.note,
      totalCost: receipt.totalCost,
      createdBy: receipt.createdBy,
      items: { create: receipt.items },
    },
    include: detailInclude,
  })
}

/**
 * Stock and ledger move together with the receipt row. Lines are applied in ascending
 * variant id, the same order checkout locks variants, so concurrent receipts cannot
 * deadlock on the stock rows.
 */
export async function applyReceiptStock(
  tx: Prisma.TransactionClient,
  receiptId: number,
  items: readonly NewReceiptLine[],
): Promise<void> {
  await tx.stockMovement.createMany({
    data: items.map((item) => ({
      variantId: item.variantId,
      delta: item.quantity,
      reason: 'RECEIPT' as const,
      refType: 'RECEIPT' as const,
      refId: receiptId,
      unitCost: item.unitCost,
    })),
  })

  for (const item of [...items].sort((left, right) => left.variantId - right.variantId)) {
    await tx.productVariant.update({
      where: { id: item.variantId },
      data: { stock: { increment: item.quantity } },
    })
  }
}
