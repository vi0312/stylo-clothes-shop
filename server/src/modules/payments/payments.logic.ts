import { randomInt } from 'node:crypto'

/** Api.md: a `PENDING` attempt is reused inside this window, then expired and replaced. */
export const ATTEMPT_TTL_MS = 15 * 60 * 1000

const TXN_REF_DIGITS = '0123456789'
const TXN_REF_RANDOM_LENGTH = 3

/**
 * `STY` + `YYMMDDHHmmss` (UTC) + 3 random digits — the shape in `erd.md`. VNPay only accepts
 * alphanumerics in `vnp_TxnRef`, and its uniqueness is what makes return/IPN replay safe.
 */
export function txnRef(now: Date, nextInt: (max: number) => number = randomInt): string {
  const pad = (value: number) => String(value).padStart(2, '0')
  const stamp = [
    pad(now.getUTCFullYear() % 100),
    pad(now.getUTCMonth() + 1),
    pad(now.getUTCDate()),
    pad(now.getUTCHours()),
    pad(now.getUTCMinutes()),
    pad(now.getUTCSeconds()),
  ].join('')

  let suffix = ''
  for (let index = 0; index < TXN_REF_RANDOM_LENGTH; index += 1) {
    suffix += TXN_REF_DIGITS[nextInt(TXN_REF_DIGITS.length)]
  }
  return `STY${stamp}${suffix}`
}

export function isAttemptExpired(createdAt: Date, now: Date): boolean {
  return now.getTime() - createdAt.getTime() >= ATTEMPT_TTL_MS
}
