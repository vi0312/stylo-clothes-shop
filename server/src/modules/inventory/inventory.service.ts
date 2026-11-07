import { prisma } from '../../lib/prisma.js'
import { isUniqueViolation } from '../../lib/prismaErrors.js'
import { notFound } from '../../utils/errors.js'
import type {
  InventoryReceiptDetailDto,
  InventoryReceiptItemDto,
  InventoryReceiptSummaryDto,
  Paginated,
} from './inventory.dto.js'
import { receiptCode, receiptTotals } from './inventory.logic.js'
import type { CreateInventoryReceiptInput, ListInventoryReceiptsQuery } from './inventory.schema.js'
import * as repository from './inventory.repository.js'

const MAX_CODE_ATTEMPTS = 3

function toItemDto(item: repository.ReceiptItemRow): InventoryReceiptItemDto {
  return {
    id: item.id,
    variantId: item.variantId,
    sku: item.variant.sku,
    productName: item.variant.product.name,
    size: item.variant.size,
    color: item.variant.color,
    quantity: item.quantity,
    unitCost: item.unitCost,
    lineTotal: item.quantity * item.unitCost,
  }
}

function toSummaryFields(row: {
  id: number
  code: string
  supplier: string
  note: string | null
  totalCost: number
  creator: { id: number; fullName: string }
  createdAt: Date
}): Omit<InventoryReceiptSummaryDto, 'itemCount'> {
  return {
    id: row.id,
    code: row.code,
    supplier: row.supplier,
    note: row.note,
    totalCost: row.totalCost,
    createdBy: { id: row.creator.id, fullName: row.creator.fullName },
    createdAt: row.createdAt.toISOString(),
  }
}

function toSummaryDto(row: repository.ReceiptSummaryRow): InventoryReceiptSummaryDto {
  return { ...toSummaryFields(row), itemCount: row._count.items }
}

function toDetailDto(row: repository.ReceiptDetailRow): InventoryReceiptDetailDto {
  return {
    ...toSummaryFields(row),
    itemCount: row.items.length,
    items: row.items.map(toItemDto),
  }
}

export async function createReceipt(
  adminId: number,
  input: CreateInventoryReceiptInput,
): Promise<InventoryReceiptDetailDto> {
  const totals = receiptTotals(input.items)

  for (let attempt = 1; ; attempt += 1) {
    try {
      return await prisma.$transaction(async (tx) => {
        const variantIds = [...new Set(input.items.map((item) => item.variantId))]
        const existing = await repository.findExistingVariantIds(tx, variantIds)
        if (existing.length !== variantIds.length) {
          const found = new Set(existing.map((variant) => variant.id))
          const missing = variantIds.find((id) => !found.has(id))
          throw notFound('NOT_FOUND', `Variant ${missing} not found`)
        }

        const receipt = await repository.createReceipt(tx, {
          code: receiptCode(new Date()),
          supplier: input.supplier,
          note: input.note === undefined || input.note === '' ? null : input.note,
          totalCost: totals.totalCost,
          createdBy: adminId,
          items: input.items,
        })
        await repository.applyReceiptStock(tx, receipt.id, input.items)
        return toDetailDto(receipt)
      })
    } catch (error) {
      // Not the generated code, so the whole transaction rolled back: retry with a fresh one.
      if (!isUniqueViolation(error) || attempt >= MAX_CODE_ATTEMPTS) throw error
    }
  }
}

export async function listReceipts(
  query: ListInventoryReceiptsQuery,
): Promise<Paginated<InventoryReceiptSummaryDto>> {
  const { receipts, total } = await repository.listInventoryReceipts(query)
  return { items: receipts.map(toSummaryDto), page: query.page, limit: query.limit, total }
}

export async function getReceipt(id: number): Promise<InventoryReceiptDetailDto> {
  const row = await repository.findReceiptById(id)
  if (!row) throw notFound('NOT_FOUND', `Inventory receipt ${id} not found`)
  return toDetailDto(row)
}
