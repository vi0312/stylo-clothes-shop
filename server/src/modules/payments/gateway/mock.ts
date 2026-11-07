import { getEnv } from '../../../config/env.js'
import type { PaymentGateway } from './PaymentGateway.js'

/** The demo gateway: the payment URL is a local page whose `txnRef` is the capability. */
export const mockGateway: PaymentGateway = {
  provider: 'MOCK',
  buildPaymentUrl({ txnRef }) {
    return `${getEnv().PUBLIC_BASE_URL.replace(/\/+$/, '')}/api/v1/payments/mock/${txnRef}`
  },
}
