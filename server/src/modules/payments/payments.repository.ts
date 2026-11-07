import {
  Prisma,
  type OrderStatus,
  type PaymentMethod,
  type PaymentProvider,
  type PaymentStatus,
} from '../../generated/prisma/client.js'
import { prisma } from '../../lib/prisma.js'

export interface LockedOrderRow {
  id: number
  code: string
  userId: number
  status: OrderStatus
  paymentMethod: PaymentMethod
  total: number
  paidAt: Date | null
}

export interface LockedAttemptRow {
  id: number
  orderId: number
  provider: PaymentProvider
  status: PaymentStatus
  amount: number
  failureReason: string | null
  createdAt: Date
}

export const paymentWithOrderInclude = {
  order: { select: { id: true, code: true, userId: true } },
} satisfies Prisma.PaymentInclude

export type PaymentWithOrderRow = Prisma.PaymentGetPayload<{
  include: typeof paymentWithOrderInclude
}>

/**
 * A current (`FOR UPDATE`) read instead of a plain one: every guard below — ownership,
 * `payment_method`, `paid_at`, status — must see the committed truth, and a snapshot read
 * would let two attempts slip past the same guard.
 */
export async function lockOrder(
  tx: Prisma.TransactionClient,
  orderId: number,
): Promise<LockedOrderRow | null> {
  const rows = await tx.$queryRaw<LockedOrderRow[]>`
    SELECT id AS id,
           code AS code,
           user_id AS userId,
           status AS status,
           payment_method AS paymentMethod,
           total AS total,
           paid_at AS paidAt
    FROM \`order\`
    WHERE id = ${orderId}
    FOR UPDATE
  `
  return rows[0] ?? null
}

export async function findLatestAttempt(tx: Prisma.TransactionClient, orderId: number) {
  return tx.payment.findFirst({
    where: { orderId },
    orderBy: [{ createdAt: 'desc' }, { id: 'desc' }],
  })
}

export async function createAttempt(
  tx: Prisma.TransactionClient,
  data: { orderId: number; provider: PaymentProvider; amount: number; txnRef: string },
) {
  return tx.payment.create({ data: { ...data, status: 'PENDING' } })
}

export async function expireAttempt(tx: Prisma.TransactionClient, id: number): Promise<void> {
  await tx.payment.update({
    where: { id },
    data: { status: 'FAILED', failureReason: 'EXPIRED' },
  })
}

export async function lockAttempt(
  tx: Prisma.TransactionClient,
  txnRef: string,
): Promise<LockedAttemptRow | null> {
  const rows = await tx.$queryRaw<LockedAttemptRow[]>`
    SELECT id AS id,
           order_id AS orderId,
           provider AS provider,
           status AS status,
           amount AS amount,
           failure_reason AS failureReason,
           created_at AS createdAt
    FROM payment
    WHERE txn_ref = ${txnRef}
    FOR UPDATE
  `
  return rows[0] ?? null
}

export async function findByTxnRef(txnRef: string): Promise<PaymentWithOrderRow | null> {
  return prisma.payment.findUnique({ where: { txnRef }, include: paymentWithOrderInclude })
}

export async function succeedAttempt(
  tx: Prisma.TransactionClient,
  id: number,
  data: {
    providerTxnNo: string | null
    bankCode: string | null
    payDate: Date | null
    rawResponse: Prisma.InputJsonValue | null
  },
): Promise<void> {
  await tx.payment.update({
    where: { id },
    data: {
      status: 'SUCCEEDED',
      failureReason: null,
      ...(data.rawResponse === null ? {} : { rawResponse: data.rawResponse }),
      providerTxnNo: data.providerTxnNo,
      bankCode: data.bankCode,
      payDate: data.payDate,
    },
  })
}

export async function failAttempt(
  tx: Prisma.TransactionClient,
  id: number,
  reason: string,
): Promise<void> {
  await tx.payment.update({ where: { id }, data: { status: 'FAILED', failureReason: reason } })
}

/**
 * `paid_at` is written exactly once and the order only advances from `PENDING`; a late
 * success on an already cancelled order still records the money (no refund flow in scope).
 */
export async function markOrderPaid(
  tx: Prisma.TransactionClient,
  order: LockedOrderRow,
  paidAt: Date,
): Promise<void> {
  const data: Prisma.OrderUpdateInput = {}
  if (order.paidAt === null) data.paidAt = paidAt
  if (order.status === 'PENDING') data.status = 'CONFIRMED'
  if (Object.keys(data).length === 0) return
  await tx.order.update({ where: { id: order.id }, data })
}
