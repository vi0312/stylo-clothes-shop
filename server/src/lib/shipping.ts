import { getEnv } from '../config/env.js'

/**
 * Fee for a cart whose billable subtotal is `subtotal`: the flat `SHIPPING_FEE`, waived once
 * the subtotal reaches `FREE_SHIPPING_THRESHOLD`. A threshold of 0 disables the waiver.
 *
 * Read from env per call rather than at module load so a test file can set its own numbers.
 */
export function shippingFeeFor(subtotal: number): number {
  const { FREE_SHIPPING_THRESHOLD, SHIPPING_FEE } = getEnv()
  const waived = FREE_SHIPPING_THRESHOLD > 0 && subtotal >= FREE_SHIPPING_THRESHOLD
  return waived ? 0 : SHIPPING_FEE
}
