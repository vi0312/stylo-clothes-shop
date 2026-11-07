/** Vietnam has no DST, so a calendar day always starts at the same UTC offset. */
const BUSINESS_UTC_OFFSET_MS = 7 * 60 * 60 * 1000
const DAY_MS = 24 * 60 * 60 * 1000

export interface BusinessDayWindow {
  gte?: Date
  lt?: Date
}

/**
 * `from`/`to` are `YYYY-MM-DD` calendar dates in `Asia/Ho_Chi_Minh`; the window is
 * half-open `[from 00:00, (to + 1) 00:00)` so both ends include the whole day.
 */
export function businessDayWindow(from?: string, to?: string): BusinessDayWindow {
  const window: BusinessDayWindow = {}
  if (from) window.gte = businessDayStart(from)
  if (to) window.lt = new Date(businessDayStart(to).getTime() + DAY_MS)
  return window
}

function businessDayStart(date: string): Date {
  const [year, month, day] = date.split('-').map(Number)
  return new Date(Date.UTC(year, month - 1, day) - BUSINESS_UTC_OFFSET_MS)
}
