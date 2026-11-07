import type { ReportGroupBy } from './reports.dto.js'

/** Vietnam has no DST, so a calendar day always starts at the same UTC offset. */
const BUSINESS_UTC_OFFSET_MS = 7 * 60 * 60 * 1000
const DAY_MS = 24 * 60 * 60 * 1000
const DEFAULT_RANGE_DAYS = 30

export interface ReportRange {
  from: string
  to: string
}

export interface PeriodPoint {
  period: string
  revenue: number
  orders: number
}

export function vietnamDateOf(instant: Date): string {
  return new Date(instant.getTime() + BUSINESS_UTC_OFFSET_MS).toISOString().slice(0, 10)
}

export function addDays(date: string, days: number): string {
  const [year, month, day] = date.split('-').map(Number)
  return new Date(Date.UTC(year, month - 1, day) + days * DAY_MS).toISOString().slice(0, 10)
}

/** Missing ends default to the last 30 calendar days ending at `to` (today in Vietnam). */
export function resolveReportRange(
  from: string | undefined,
  to: string | undefined,
  now: Date = new Date(),
): ReportRange {
  const end = to ?? vietnamDateOf(now)
  const start = from ?? addDays(end, -(DEFAULT_RANGE_DAYS - 1))
  return { from: start, to: end }
}

export function dayPeriods(from: string, to: string): string[] {
  const periods: string[] = []
  for (let date = from; date <= to; date = addDays(date, 1)) {
    periods.push(date)
  }
  return periods
}

export function monthPeriods(from: string, to: string): string[] {
  let year = Number(from.slice(0, 4))
  let month = Number(from.slice(5, 7))
  const endYear = Number(to.slice(0, 4))
  const endMonth = Number(to.slice(5, 7))

  const periods: string[] = []
  while (year < endYear || (year === endYear && month <= endMonth)) {
    periods.push(`${year}-${String(month).padStart(2, '0')}`)
    month += 1
    if (month === 13) {
      month = 1
      year += 1
    }
  }
  return periods
}

export function periodsFor(from: string, to: string, groupBy: ReportGroupBy): string[] {
  return groupBy === 'month' ? monthPeriods(from, to) : dayPeriods(from, to)
}

export function zeroFillPeriods(
  periods: readonly string[],
  rows: readonly PeriodPoint[],
): PeriodPoint[] {
  const byPeriod = new Map(rows.map((row) => [row.period, row]))
  return periods.map((period) => byPeriod.get(period) ?? { period, revenue: 0, orders: 0 })
}

/** SQL aggregates arrive as BigInt or string through the MariaDB adapter, and `null` when empty. */
export function toNumber(value: unknown): number {
  if (value === null || value === undefined) return 0
  return Number(value)
}

export function grossProfit(revenue: number, cogs: number): number {
  return revenue - cogs
}
