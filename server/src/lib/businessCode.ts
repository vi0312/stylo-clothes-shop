import { randomInt } from 'node:crypto'

const CODE_ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789'
const CODE_SUFFIX_LENGTH = 4

/** Display/reconciliation codes are dated in the business time zone, never UTC. */
const CODE_TIME_ZONE = 'Asia/Ho_Chi_Minh'

export function datedCode(
  prefix: string,
  now: Date,
  nextInt: (max: number) => number = randomInt,
): string {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: CODE_TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(now)

  const part = (type: Intl.DateTimeFormatPartTypes): string =>
    parts.find((entry) => entry.type === type)?.value ?? ''

  let suffix = ''
  for (let index = 0; index < CODE_SUFFIX_LENGTH; index += 1) {
    suffix += CODE_ALPHABET[nextInt(CODE_ALPHABET.length)]
  }

  return `${prefix}-${part('year').slice(-2)}${part('month')}${part('day')}-${suffix}`
}
