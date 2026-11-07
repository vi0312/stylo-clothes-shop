import { z } from 'zod'

export const addCartItemSchema = z.strictObject({
  variantId: z.number().int().positive(),
  quantity: z.number().int().min(1),
})

export type AddCartItemInput = z.infer<typeof addCartItemSchema>

export const patchCartItemSchema = z.strictObject({
  quantity: z.number().int().min(1),
})

export type PatchCartItemInput = z.infer<typeof patchCartItemSchema>

export const cartItemParamsSchema = z.object({
  itemId: z.coerce.number().int().positive(),
})

export type CartItemParams = z.infer<typeof cartItemParamsSchema>
