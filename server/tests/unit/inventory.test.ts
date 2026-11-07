import { describe, expect, it } from 'vitest'
import { receiptCode, receiptTotals } from '../../src/modules/inventory/inventory.logic.js'

describe('receipt code', () => {
  it('formats PN-YYMMDD-XXXX with the business time zone date', () => {
    expect(receiptCode(new Date('2026-11-04T17:30:00.000Z'), () => 0)).toBe('PN-261105-AAAA')
    expect(receiptCode(new Date('2026-11-04T03:20:00.000Z'), () => 0)).toBe('PN-261104-AAAA')
  })

  it('uses the whole suffix alphabet', () => {
    expect(receiptCode(new Date('2026-11-04T03:20:00.000Z'), (max) => max - 1)).toBe(
      'PN-261104-9999',
    )
  })

  it('matches the documented shape', () => {
    expect(receiptCode(new Date(), () => 0)).toMatch(/^PN-\d{6}-[A-Z0-9]{4}$/)
  })
})

describe('receipt totals', () => {
  it('sums quantity × unit cost over the lines', () => {
    expect(
      receiptTotals([
        { quantity: 2, unitCost: 100_000 },
        { quantity: 3, unitCost: 50_000 },
      ]),
    ).toEqual({ totalCost: 350_000, itemCount: 2 })
  })

  it('counts lines, not units, when one variant appears twice', () => {
    expect(
      receiptTotals([
        { quantity: 10, unitCost: 100_000 },
        { quantity: 10, unitCost: 120_000 },
      ]),
    ).toEqual({ totalCost: 2_200_000, itemCount: 2 })
  })
})
