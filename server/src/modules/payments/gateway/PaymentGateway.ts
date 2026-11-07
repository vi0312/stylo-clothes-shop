import { getEnv } from '../../../config/env.js'
import type { PaymentProvider } from '../../../generated/prisma/client.js'
import { mockGateway } from './mock.js'
import { vnpayGateway } from './vnpay.js'

export interface PaymentUrlInput {
  txnRef: string
  amount: number
  orderCode: string
  createdAt: Date
}

export interface PaymentGateway {
  readonly provider: PaymentProvider
  /**
   * Rebuilt on every read from the stored row: nothing about the URL is persisted except
   * the reference the gateway itself keys on.
   */
  buildPaymentUrl(input: PaymentUrlInput): string
}

/**
 * Each provider is an adapter behind the same interface; an attempt row names the one that
 * minted it, so rebuilding its URL never depends on the current `PAYMENT_PROVIDER`.
 */
export function gatewayFor(provider: PaymentProvider): PaymentGateway {
  if (provider === 'MOCK') return mockGateway
  return vnpayGateway
}

export function paymentGateway(): PaymentGateway {
  return gatewayFor(getEnv().PAYMENT_PROVIDER)
}
