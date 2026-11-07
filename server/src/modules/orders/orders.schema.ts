import { z } from 'zod'
import { ORDER_STATUSES } from './orders.logic.js'

export const createOrderSchema = z.strictObject({
  clientOrderId: z.uuid(),
  addressId: z.number().int().positive(),
  paymentMethod: z.enum(['COD', 'ONLINE']),
  note: z.string().max(255).optional(),
})

export type CreateOrderInput = z.infer<typeof createOrderSchema>

const optionalText = (max: number) =>
  z.preprocess(
    (v) => (v === '' || v === undefined ? undefined : v),
    z.string().trim().max(max).optional(),
  )

const pageField = z.preprocess(
  (v) => (v === '' || v === undefined ? undefined : v),
  z.coerce.number().int().min(1).default(1),
)

const limitField = z.preprocess(
  (v) => (v === '' || v === undefined ? undefined : v),
  z.coerce.number().int().min(1).max(100).default(20),
)

const sortField = z.preprocess(
  (v) => (v === '' || v === undefined ? undefined : v),
  z.enum(['newest', 'oldest']).default('newest'),
)

const statusField = z.preprocess(
  (v) => (v === '' || v === undefined ? undefined : v),
  z.enum(ORDER_STATUSES).optional(),
)

// Unknown query params are ignored, per the contract conventions.
export const listOrdersQuerySchema = z.object({
  status: statusField,
  sort: sortField,
  page: pageField,
  limit: limitField,
})

export type ListOrdersQuery = z.infer<typeof listOrdersQuerySchema>

export const listAdminOrdersQuerySchema = z
  .object({
    status: statusField,
    q: optionalText(100),
    from: z.preprocess(
      (v) => (v === '' || v === undefined ? undefined : v),
      z.iso.date().optional(),
    ),
    to: z.preprocess(
      (v) => (v === '' || v === undefined ? undefined : v),
      z.iso.date().optional(),
    ),
    sort: sortField,
    page: pageField,
    limit: limitField,
  })
  .refine((data) => !data.from || !data.to || data.from <= data.to, {
    message: 'from cannot be later than to',
    path: ['to'],
  })

export type ListAdminOrdersQuery = z.infer<typeof listAdminOrdersQuerySchema>

export const orderParamsSchema = z.object({
  id: z.coerce.number().int().positive(),
})

export type OrderParams = z.infer<typeof orderParamsSchema>

export const changeOrderStatusSchema = z.strictObject({
  status: z.enum(ORDER_STATUSES),
})

export type ChangeOrderStatusInput = z.infer<typeof changeOrderStatusSchema>
