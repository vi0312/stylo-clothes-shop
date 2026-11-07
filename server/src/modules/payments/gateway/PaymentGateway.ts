import { getEnv } from '../../../config/env.js'
import type { PaymentProvider } from '../../../generated/prisma/client.js'
import { mockGateway } from './mock.js'

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
 * Only the mock adapter exists (the VNPay one is its own ticket). Throwing beats minting a
 * mock URL for an attempt the configured gateway would never settle.
 */
export function gatewayFor(provider: PaymentProvider): PaymentGateway {
  if (provider === 'MOCK') return mockGateway
  throw new Error('PAYMENT_PROVIDER=VNPAY requires the VNPay adapter, which is not wired yet')
}

export function paymentGateway(): PaymentGateway {
  return gatewayFor(getEnv().PAYMENT_PROVIDER)
}
