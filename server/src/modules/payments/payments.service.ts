import { getEnv } from '../../config/env.js'
import { Prisma, type Payment, type PaymentProvider, type PaymentStatus } from '../../generated/prisma/client.js'
import { prisma } from '../../lib/prisma.js'
import { isUniqueViolation } from '../../lib/prismaErrors.js'
import { conflict, notFound } from '../../utils/errors.js'
import type { PublicUser } from '../users/user.dto.js'
import type { PaymentAttemptDto, PaymentDetailDto, PaymentDto } from './payments.dto.js'
import { gatewayFor, paymentGateway } from './gateway/PaymentGateway.js'
import { isAttemptExpired, txnRef } from './payments.logic.js'
import * as repository from './payments.repository.js'

const CREATE_ATTEMPT_ATTEMPTS = 3

export interface OrderForPayment {
  id: number
  code: string
  total: number
}

/** Public so checkout can render the attempt it just created inside its own transaction. */
export function toPaymentAttempt(row: Payment, orderCode: string): PaymentAttemptDto {
  return {
    id: row.id,
    txnRef: row.txnRef,
    provider: row.provider,
    status: row.status,
    amount: row.amount,
    paymentUrl: gatewayFor(row.provider).buildPaymentUrl({
      txnRef: row.txnRef,
      amount: row.amount,
      orderCode,
      createdAt: row.createdAt,
    }),
    failureReason: row.failureReason,
    createdAt: row.createdAt.toISOString(),
  }
}

/** Admin order detail lists every attempt; the URL is only rebuilt for the payment endpoints. */
export function toPayment(row: Payment): PaymentDto {
  return {
    id: row.id,
    txnRef: row.txnRef,
    provider: row.provider,
    status: row.status,
    amount: row.amount,
    failureReason: row.failureReason,
    createdAt: row.createdAt.toISOString(),
  }
}

function toDetailDto(row: repository.PaymentWithOrderRow): PaymentDetailDto {
  return {
    txnRef: row.txnRef,
    provider: row.provider,
    status: row.status,
    amount: row.amount,
    orderId: row.orderId,
    orderCode: row.order.code,
    failureReason: row.failureReason,
    createdAt: row.createdAt.toISOString(),
    updatedAt: row.updatedAt.toISOString(),
  }
}

async function insertAttempt(
  tx: Prisma.TransactionClient,
  order: OrderForPayment,
  now: Date,
): Promise<PaymentAttemptDto> {
  const provider = paymentGateway().provider
  for (let attempt = 1; ; attempt += 1) {
    try {
      const row = await repository.createAttempt(tx, {
        orderId: order.id,
        provider,
        amount: order.total,
        txnRef: txnRef(now),
      })
      return toPaymentAttempt(row, order.code)
    } catch (error) {
      // Two attempts stamped in the same second can collide on the 3 random digits.
      if (!isUniqueViolation(error) || attempt >= CREATE_ATTEMPT_ATTEMPTS) throw error
    }
  }
}

/** Called by checkout inside its own transaction: `ONLINE` gets exactly one attempt. */
export async function createAttemptInTx(
  tx: Prisma.TransactionClient,
  order: OrderForPayment,
  now: Date = new Date(),
): Promise<PaymentAttemptDto> {
  return insertAttempt(tx, order, now)
}

export interface StartPaymentResult {
  attempt: PaymentAttemptDto
  created: boolean
}

export async function startPayment(
  userId: number,
  orderId: number,
): Promise<StartPaymentResult> {
  return prisma.$transaction(async (tx) => {
    const order = await repository.lockOrder(tx, orderId)
    if (!order || order.userId !== userId) throw notFound('NOT_FOUND', 'Order not found')
    if (order.paymentMethod !== 'ONLINE') {
      throw conflict('PAYMENT_NOT_ALLOWED', 'This order does not use online payment')
    }
    if (order.paidAt !== null) throw conflict('ORDER_ALREADY_PAID', 'This order is already paid')
    if (order.status !== 'PENDING' && order.status !== 'CONFIRMED') {
      throw conflict('INVALID_ORDER_STATE', 'Payment is not allowed from the current status', {
        current: order.status,
      })
    }

    const now = new Date()
    const latest = await repository.findLatestAttempt(tx, order.id)
    if (latest?.status === 'PENDING') {
      if (!isAttemptExpired(latest.createdAt, now)) {
        return { attempt: toPaymentAttempt(latest, order.code), created: false }
      }
      await repository.expireAttempt(tx, latest.id)
    }

    return { attempt: await insertAttempt(tx, order, now), created: true }
  })
}

export async function getPayment(user: PublicUser, txnRef: string): Promise<PaymentDetailDto> {
  const row = await repository.findByTxnRef(txnRef)
  if (!row || (user.role !== 'ADMIN' && row.order.userId !== user.id)) {
    throw notFound('NOT_FOUND', 'Payment not found')
  }
  return toDetailDto(row)
}

export interface ConfirmPaymentInput {
  txnRef: string
  amount: number
  providerTxnNo?: string | null
  bankCode?: string | null
  payDate?: Date | null
  rawResponse?: Prisma.InputJsonValue | null
}

export type ConfirmOutcome = 'SUCCEEDED' | 'ALREADY_SUCCEEDED' | 'AMOUNT_MISMATCH' | 'NOT_FOUND'

export interface ConfirmResult {
  outcome: ConfirmOutcome
  /** The attempt's amount; `null` when the reference is unknown. */
  amount: number | null
}

/**
 * The one place a payment settles — mock completion today, VNPay return/IPN later. The
 * attempt row is locked first, so concurrent callbacks change state once, and a `SUCCEEDED`
 * attempt stays terminal.
 */
export async function confirmPayment(input: ConfirmPaymentInput): Promise<ConfirmResult> {
  return prisma.$transaction(async (tx) => {
    const attempt = await repository.lockAttempt(tx, input.txnRef)
    if (!attempt) return { outcome: 'NOT_FOUND', amount: null }

    if (attempt.status === 'SUCCEEDED') {
      return { outcome: 'ALREADY_SUCCEEDED', amount: attempt.amount }
    }

    if (input.amount !== attempt.amount) {
      await repository.failAttempt(tx, attempt.id, 'AMOUNT_MISMATCH')
      return { outcome: 'AMOUNT_MISMATCH', amount: attempt.amount }
    }

    const order = await repository.lockOrder(tx, attempt.orderId)
    if (!order) throw new Error(`Payment ${attempt.id} references a missing order`)

    await repository.succeedAttempt(tx, attempt.id, {
      providerTxnNo: input.providerTxnNo ?? null,
      bankCode: input.bankCode ?? null,
      payDate: input.payDate ?? null,
      rawResponse: input.rawResponse ?? null,
    })
    await repository.markOrderPaid(tx, order, new Date())

    return { outcome: 'SUCCEEDED', amount: attempt.amount }
  })
}

export interface MockAttempt {
  txnRef: string
  amount: number
  status: PaymentStatus
  orderCode: string
  createdAt: Date
}

/** `null` also under `PAYMENT_PROVIDER=VNPAY`: the mock routes must not exist there. */
export async function findMockAttempt(txnRef: string): Promise<MockAttempt | null> {
  if (getEnv().PAYMENT_PROVIDER !== 'MOCK') return null
  const row = await repository.findByTxnRef(txnRef)
  if (!row || row.provider !== 'MOCK') return null
  return {
    txnRef: row.txnRef,
    amount: row.amount,
    status: row.status,
    orderCode: row.order.code,
    createdAt: row.createdAt,
  }
}

export interface GatewayAttempt {
  txnRef: string
  provider: PaymentProvider
  status: PaymentStatus
  amount: number
  orderCode: string
}

/** A callback's own provider must match: a VNPay callback can never settle a mock attempt. */
export async function findGatewayAttempt(
  txnRef: string,
  provider: PaymentProvider,
): Promise<GatewayAttempt | null> {
  const row = await repository.findByTxnRef(txnRef)
  if (!row || row.provider !== provider) return null
  return {
    txnRef: row.txnRef,
    provider: row.provider,
    status: row.status,
    amount: row.amount,
    orderCode: row.order.code,
  }
}

/**
 * A gateway callback that reports a failed payment. `SUCCEEDED` stays terminal, so a late
 * failure callback cannot un-pay an order.
 */
export async function failPayment(txnRef: string, reason: string): Promise<void> {
  await prisma.$transaction(async (tx) => {
    const attempt = await repository.lockAttempt(tx, txnRef)
    if (!attempt || attempt.status === 'SUCCEEDED') return
    await repository.failAttempt(tx, attempt.id, reason)
  })
}

export type MockResult = 'success' | 'failed'

export interface MockCompletion {
  attempt: MockAttempt
  outcome: ConfirmOutcome | 'FAILED'
}

/**
 * A repeated submit is idempotent: the page always renders the attempt's final state, so a
 * second `failed` after a success still shows the success.
 */
export async function completeMockPayment(
  txnRef: string,
  result: MockResult,
): Promise<MockCompletion | null> {
  const attempt = await findMockAttempt(txnRef)
  if (!attempt) return null

  if (attempt.status === 'SUCCEEDED') {
    return { attempt, outcome: 'ALREADY_SUCCEEDED' }
  }

  if (result === 'failed') {
    await prisma.$transaction(async (tx) => {
      const locked = await repository.lockAttempt(tx, txnRef)
      if (!locked || locked.status === 'SUCCEEDED') return
      await repository.failAttempt(tx, locked.id, 'MOCK_FAILED')
    })
  } else {
    await confirmPayment({ txnRef, amount: attempt.amount })
  }

  const settled = await findMockAttempt(txnRef)
  if (!settled) throw new Error(`Mock attempt ${txnRef} disappeared while settling`)
  return {
    attempt: settled,
    outcome: settled.status === 'SUCCEEDED' ? 'SUCCEEDED' : 'FAILED',
  }
}
