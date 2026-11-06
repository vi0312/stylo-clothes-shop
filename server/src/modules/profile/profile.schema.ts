import { z } from 'zod'

const fullNameSchema = z.string().trim().min(1).max(100)

/** An absent phone is unchanged; `null` clears it. */
const phoneSchema = z.preprocess(
  (value) => (typeof value === 'string' && value.trim() === '' ? null : value),
  z.string().trim().max(20).nullable(),
)

export const patchMeSchema = z
  .strictObject({
    fullName: fullNameSchema.optional(),
    phone: phoneSchema.optional(),
  })
  .refine((data) => data.fullName !== undefined || data.phone !== undefined, {
    message: 'At least one of fullName, phone is required',
  })

export type PatchMeInput = z.infer<typeof patchMeSchema>

export const createAddressSchema = z.strictObject({
  recipientName: z.string().trim().min(1).max(100),
  phone: z.string().trim().min(1).max(20),
  line: z.string().trim().min(1).max(255),
  isDefault: z.boolean().optional(),
})

export type CreateAddressInput = z.infer<typeof createAddressSchema>

export const patchAddressSchema = z
  .strictObject({
    recipientName: z.string().trim().min(1).max(100).optional(),
    phone: z.string().trim().min(1).max(20).optional(),
    line: z.string().trim().min(1).max(255).optional(),
    // `false` is rejected: the default moves by making another address the default.
    isDefault: z.literal(true).optional(),
  })
  .refine((data) => Object.values(data).some((value) => value !== undefined), {
    message: 'At least one address field is required',
  })

export type PatchAddressInput = z.infer<typeof patchAddressSchema>

export const addressParamsSchema = z.object({
  id: z.coerce.number().int().positive(),
})

export type AddressParams = z.infer<typeof addressParamsSchema>
