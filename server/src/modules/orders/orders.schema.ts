import { z } from 'zod'

export const createOrderSchema = z.strictObject({
  clientOrderId: z.uuid(),
  addressId: z.number().int().positive(),
  paymentMethod: z.enum(['COD', 'ONLINE']),
  note: z.string().max(255).optional(),
})

export type CreateOrderInput = z.infer<typeof createOrderSchema>
