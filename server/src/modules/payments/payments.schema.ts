import { z } from 'zod'

export const orderPaymentParamsSchema = z.object({
  id: z.coerce.number().int().positive(),
})

export const paymentParamsSchema = z.object({
  txnRef: z.string().trim().min(1).max(50),
})

export const mockCompleteSchema = z.object({
  result: z.enum(['success', 'failed']),
})
