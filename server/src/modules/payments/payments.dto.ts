import type { PaymentProvider, PaymentStatus } from '../../generated/prisma/client.js'

/** `PaymentAttempt` from `api.md`: created by checkout or by `POST /orders/:id/payments`. */
export interface PaymentAttemptDto {
  id: number
  txnRef: string
  provider: PaymentProvider
  status: PaymentStatus
  amount: number
  paymentUrl: string
  failureReason: string | null
  createdAt: string
}

/** `GET /payments/:txnRef`. */
export interface PaymentDetailDto {
  txnRef: string
  provider: PaymentProvider
  status: PaymentStatus
  amount: number
  orderId: number
  orderCode: string
  failureReason: string | null
  createdAt: string
  updatedAt: string
}

/** `Payment` from `api.md`: the attempt without the rebuildable payment URL. */
export type PaymentDto = Omit<PaymentAttemptDto, 'paymentUrl'>
