import { randomInt } from 'node:crypto'
import { datedCode } from '../../lib/businessCode.js'

export function receiptCode(now: Date, nextInt: (max: number) => number = randomInt): string {
  return datedCode('PN', now, nextInt)
}

export interface ReceiptLine {
  quantity: number
  unitCost: number
}

export interface ReceiptTotals {
  totalCost: number
  itemCount: number
}

export function receiptTotals(lines: readonly ReceiptLine[]): ReceiptTotals {
  return {
    totalCost: lines.reduce((sum, line) => sum + line.quantity * line.unitCost, 0),
    itemCount: lines.length,
  }
}
