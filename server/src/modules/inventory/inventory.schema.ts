import { z } from 'zod'

const receiptItemSchema = z.strictObject({
  variantId: z.number().int().positive(),
  quantity: z.number().int().min(1),
  unitCost: z.number().int().min(0),
})

export const createInventoryReceiptSchema = z.strictObject({
  supplier: z.string().trim().min(1).max(150),
  note: z.string().max(255).optional(),
  items: z.array(receiptItemSchema).min(1).max(100),
})

export type CreateInventoryReceiptInput = z.infer<typeof createInventoryReceiptSchema>

const optionalDate = z.preprocess(
  (value) => (value === '' || value === undefined ? undefined : value),
  z.iso.date().optional(),
)

const pageField = z.preprocess(
  (value) => (value === '' || value === undefined ? undefined : value),
  z.coerce.number().int().min(1).default(1),
)

const limitField = z.preprocess(
  (value) => (value === '' || value === undefined ? undefined : value),
  z.coerce.number().int().min(1).max(100).default(20),
)

const sortField = z.preprocess(
  (value) => (value === '' || value === undefined ? undefined : value),
  z.enum(['newest', 'oldest']).default('newest'),
)

// Unknown query params are ignored, per the contract conventions.
export const listInventoryReceiptsQuerySchema = z
  .object({
    from: optionalDate,
    to: optionalDate,
    sort: sortField,
    page: pageField,
    limit: limitField,
  })
  .refine((data) => !data.from || !data.to || data.from <= data.to, {
    message: 'from cannot be later than to',
    path: ['to'],
  })

export type ListInventoryReceiptsQuery = z.infer<typeof listInventoryReceiptsQuerySchema>

export const inventoryReceiptParamsSchema = z.object({
  id: z.coerce.number().int().positive(),
})
