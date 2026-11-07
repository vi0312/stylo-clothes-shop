import { describe, expect, it } from 'vitest'
import {
  addDays,
  dayPeriods,
  grossProfit,
  monthPeriods,
  periodsFor,
  resolveReportRange,
  toNumber,
  vietnamDateOf,
  zeroFillPeriods,
} from '../../src/modules/reports/reports.logic.js'

describe('vietnam calendar dates', () => {
  it('rolls over at 17:00Z, not midnight UTC', () => {
    expect(vietnamDateOf(new Date('2026-10-31T16:59:59.999Z'))).toBe('2026-10-31')
    expect(vietnamDateOf(new Date('2026-10-31T17:00:00.000Z'))).toBe('2026-11-01')
  })

  it('crosses month and year boundaries in addDays', () => {
    expect(addDays('2026-11-30', 1)).toBe('2026-12-01')
    expect(addDays('2026-12-31', 1)).toBe('2027-01-01')
    expect(addDays('2026-03-01', -1)).toBe('2026-02-28')
    expect(addDays('2028-03-01', -1)).toBe('2028-02-29')
  })
})

describe('report range defaults', () => {
  const now = new Date('2026-11-07T03:00:00.000Z')

  it('defaults to the last 30 days including today in Vietnam', () => {
    expect(resolveReportRange(undefined, undefined, now)).toEqual({
      from: '2026-10-09',
      to: '2026-11-07',
    })
  })

  it('anchors the default start on the supplied end', () => {
    expect(resolveReportRange(undefined, '2026-11-03', now)).toEqual({
      from: '2026-10-05',
      to: '2026-11-03',
    })
    expect(resolveReportRange('2026-11-01', undefined, now)).toEqual({
      from: '2026-11-01',
      to: '2026-11-07',
    })
  })
})

describe('report periods', () => {
  it('fills every day of an inclusive range', () => {
    expect(dayPeriods('2026-10-30', '2026-11-02')).toEqual([
      '2026-10-30',
      '2026-10-31',
      '2026-11-01',
      '2026-11-02',
    ])
    expect(dayPeriods('2026-11-07', '2026-11-07')).toEqual(['2026-11-07'])
  })

  it('fills every Vietnamese calendar month in the range', () => {
    expect(monthPeriods('2026-09-15', '2026-11-05')).toEqual(['2026-09', '2026-10', '2026-11'])
    expect(monthPeriods('2026-11-01', '2027-02-01')).toEqual(['2026-11', '2026-12', '2027-01', '2027-02'])
  })

  it('picks the bucketing unit from groupBy', () => {
    expect(periodsFor('2026-11-01', '2026-11-02', 'day')).toEqual(['2026-11-01', '2026-11-02'])
    expect(periodsFor('2026-11-01', '2026-11-02', 'month')).toEqual(['2026-11'])
  })
})

describe('zero fill', () => {
  it('keeps returned rows and inserts empty periods in order', () => {
    expect(
      zeroFillPeriods(['2026-11-01', '2026-11-02', '2026-11-03'], [
        { period: '2026-11-03', revenue: 1_250_000, orders: 3 },
        { period: '2026-11-01', revenue: 350_000, orders: 2 },
      ]),
    ).toEqual([
      { period: '2026-11-01', revenue: 350_000, orders: 2 },
      { period: '2026-11-02', revenue: 0, orders: 0 },
      { period: '2026-11-03', revenue: 1_250_000, orders: 3 },
    ])
  })

  it('drops rows outside the requested periods', () => {
    expect(
      zeroFillPeriods(['2026-11-01'], [{ period: '2026-10-31', revenue: 99, orders: 1 }]),
    ).toEqual([{ period: '2026-11-01', revenue: 0, orders: 0 }])
  })
})

describe('aggregate conversion', () => {
  it('reads BigInt, numeric strings and null aggregates', () => {
    expect(toNumber(1_600_000n)).toBe(1_600_000)
    expect(toNumber('1250000')).toBe(1_250_000)
    expect(toNumber(null)).toBe(0)
    expect(toNumber(undefined)).toBe(0)
  })

  it('derives gross profit from revenue minus cost of goods sold', () => {
    expect(grossProfit(12_500_000, 3_200_000)).toBe(9_300_000)
    expect(grossProfit(0, 0)).toBe(0)
  })
})
