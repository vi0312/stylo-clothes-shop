import { describe, expect, it } from 'vitest'
import {
  collectCheckoutFailures,
  computeTotals,
  derivePaymentStatus,
  orderCode,
  type LockedVariant,
} from '../../src/modules/orders/orders.logic.js'

function variant(overrides: Partial<LockedVariant> = {}): LockedVariant {
  return {
    id: 1,
    sku: 'AT-DEN-M',
    price: 199_000,
    stock: 5,
    productId: 9,
    productName: 'Áo thun cotton',
    productStatus: 'ACTIVE',
    size: 'M',
    color: 'Đen',
    ...overrides,
  }
}

describe('order code', () => {
  it('dates the code in Asia/Ho_Chi_Minh, not UTC', () => {
    // 17:30Z on 2026-11-04 is 00:30 on 2026-11-05 in Ho Chi Minh City.
    expect(orderCode(new Date('2026-11-04T17:30:00.000Z'), () => 0)).toBe('STY-261105-AAAA')
    expect(orderCode(new Date('2026-11-04T03:20:00.000Z'), () => 0)).toBe('STY-261104-AAAA')
  })

  it('draws the four suffix characters from A-Z0-9', () => {
    const code = orderCode(new Date('2026-11-04T03:20:00.000Z'), (max) => max - 1)
    expect(code).toBe('STY-261104-9999')
    expect(orderCode(new Date('2026-11-04T03:20:00.000Z'))).toMatch(/^STY-\d{6}-[A-Z0-9]{4}$/)
  })
})

describe('order totals', () => {
  it('sums unit price times quantity and adds the flat shipping fee', () => {
    const totals = computeTotals(
      [
        { unitPrice: 199_000, quantity: 2 },
        { unitPrice: 89_000, quantity: 1 },
      ],
      30_000,
    )

    expect(totals).toEqual({ subtotal: 487_000, total: 517_000 })
  })

  it('treats an empty cart as zero', () => {
    expect(computeTotals([], 30_000)).toEqual({ subtotal: 0, total: 30_000 })
  })
})

describe('checkout guard', () => {
  it('resolves a passing line against the locked variant price', () => {
    const result = collectCheckoutFailures(
      [{ variantId: 1, sku: 'AT-DEN-M', quantity: 2 }],
      new Map([[1, variant()]]),
    )

    expect(result.unavailable).toEqual([])
    expect(result.outOfStock).toEqual([])
    expect(result.ready).toEqual([
      {
        variantId: 1,
        sku: 'AT-DEN-M',
        quantity: 2,
        unitPrice: 199_000,
        productId: 9,
        productName: 'Áo thun cotton',
        size: 'M',
        color: 'Đen',
      },
    ])
  })

  it('rejects an inactive product and a variant deleted since the cart was read', () => {
    const result = collectCheckoutFailures(
      [
        { variantId: 1, sku: 'AT-DEN-M', quantity: 1 },
        { variantId: 2, sku: 'AT-TRANG-L', quantity: 1 },
      ],
      new Map([[1, variant({ productStatus: 'INACTIVE' })]]),
    )

    expect(result.unavailable).toEqual([
      { sku: 'AT-DEN-M', reason: 'PRODUCT_INACTIVE' },
      { sku: 'AT-TRANG-L', reason: 'VARIANT_MISSING' },
    ])
    expect(result.ready).toEqual([])
  })

  it('reports every short line with its requested and available counts', () => {
    const result = collectCheckoutFailures(
      [
        { variantId: 1, sku: 'AT-DEN-M', quantity: 3 },
        { variantId: 2, sku: 'AT-TRANG-L', quantity: 2 },
        { variantId: 3, sku: 'QU-XANH-30', quantity: 1 },
      ],
      new Map([
        [1, variant({ stock: 2 })],
        [2, variant({ id: 2, sku: 'AT-TRANG-L', stock: 1 })],
        [3, variant({ id: 3, sku: 'QU-XANH-30', stock: 4 })],
      ]),
    )

    expect(result.outOfStock).toEqual([
      { sku: 'AT-DEN-M', requested: 3, available: 2 },
      { sku: 'AT-TRANG-L', requested: 2, available: 1 },
    ])
    expect(result.ready.map((line) => line.sku)).toEqual(['QU-XANH-30'])
  })
})

describe('derived payment status', () => {
  const paidAt = new Date('2026-11-04T03:21:12.000Z')

  it('is paid whenever paid_at is set, even if a later attempt failed', () => {
    expect(derivePaymentStatus(paidAt, { status: 'FAILED' })).toBe('PAID')
  })

  it('is failed only when unpaid and the latest attempt failed', () => {
    expect(derivePaymentStatus(null, { status: 'FAILED' })).toBe('FAILED')
  })

  it('is unpaid for cod orders and pending attempts', () => {
    expect(derivePaymentStatus(null, undefined)).toBe('UNPAID')
    expect(derivePaymentStatus(null, { status: 'PENDING' })).toBe('UNPAID')
    expect(derivePaymentStatus(null, { status: 'SUCCEEDED' })).toBe('UNPAID')
  })
})
