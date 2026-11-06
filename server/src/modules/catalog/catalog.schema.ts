import { z } from 'zod'

const catalogStatusValues = ['ACTIVE', 'INACTIVE'] as const

const optionalInt = (min = 0, max = Number.MAX_SAFE_INTEGER) =>
  z.preprocess(
    (v) => (v === '' || v === undefined ? undefined : v),
    z.coerce.number().int().min(min).max(max).optional(),
  )

export const listProductsQuerySchema = z
  .object({
    q: z.preprocess(
      (v) => (v === '' || v === undefined ? undefined : v),
      z.string().max(100).optional(),
    ),
    categoryId: optionalInt(1),
    brandId: optionalInt(1),
    color: z.preprocess(
      (v) => (v === '' || v === undefined ? undefined : v),
      z.string().min(1).max(40).optional(),
    ),
    size: z.preprocess(
      (v) => (v === '' || v === undefined ? undefined : v),
      z.string().min(1).max(20).optional(),
    ),
    minPrice: optionalInt(0),
    maxPrice: optionalInt(0),
    sort: z.preprocess(
      (v) => (v === '' || v === undefined ? undefined : v),
      z.enum(['newest', 'price_asc', 'price_desc', 'name_asc']).default('newest'),
    ),
    page: z.preprocess(
      (v) => (v === '' || v === undefined ? undefined : v),
      z.coerce.number().int().min(1).default(1),
    ),
    limit: z.preprocess(
      (v) => (v === '' || v === undefined ? undefined : v),
      z.coerce.number().int().min(1).max(100).default(20),
    ),
  })
  .refine(
    (data) => {
      if (data.minPrice !== undefined && data.maxPrice !== undefined) {
        return data.minPrice <= data.maxPrice
      }
      return true
    },
    {
      message: 'minPrice cannot be greater than maxPrice',
      path: ['minPrice'],
    },
  )

export type ListProductsQuery = z.infer<typeof listProductsQuerySchema>

export const productParamsSchema = z.object({
  id: z.coerce.number().int().positive(),
})

export type ProductParams = z.infer<typeof productParamsSchema>

// ─── Admin ───────────────────────────────────────────────────────────────

export const adminListQuerySchema = z
  .object({
    q: z.preprocess(
      (v) => (v === '' || v === undefined ? undefined : v),
      z.string().max(100).optional(),
    ),
    status: z.preprocess(
      (v) => (v === '' || v === undefined ? undefined : v),
      z.enum(catalogStatusValues).optional(),
    ),
    categoryId: optionalInt(1),
    brandId: optionalInt(1),
    sort: z.preprocess(
      (v) => (v === '' || v === undefined ? undefined : v),
      z.enum(['newest', 'oldest', 'name_asc']).default('newest'),
    ),
    page: z.preprocess(
      (v) => (v === '' || v === undefined ? undefined : v),
      z.coerce.number().int().min(1).default(1),
    ),
    limit: z.preprocess(
      (v) => (v === '' || v === undefined ? undefined : v),
      z.coerce.number().int().min(1).max(100).default(20),
    ),
  })
  .strict()

export type AdminListQuery = z.infer<typeof adminListQuerySchema>

export const adminProductListQuerySchema = adminListQuerySchema

const nameInputSchema = z.string().trim().min(1).max(100)

export const createCategorySchema = z.strictObject({
  name: nameInputSchema,
})

export type CreateCategoryInput = z.infer<typeof createCategorySchema>

export const patchCategorySchema = z
  .strictObject({
    name: nameInputSchema.optional(),
    status: z.enum(catalogStatusValues).optional(),
  })
  .refine((data) => data.name !== undefined || data.status !== undefined, {
    message: 'At least one of name, status is required',
  })

export type PatchCategoryInput = z.infer<typeof patchCategorySchema>

export const createBrandSchema = z.strictObject({
  name: nameInputSchema,
})

export type CreateBrandInput = z.infer<typeof createBrandSchema>

export const patchBrandSchema = patchCategorySchema

export type PatchBrandInput = PatchCategoryInput

export const idParamsSchema = z.object({
  id: z.coerce.number().int().positive(),
})

export type IdParams = z.infer<typeof idParamsSchema>

export const createProductSchema = z.strictObject({
  name: z.string().trim().min(1).max(200),
  description: z.string().max(5000).optional(),
  categoryId: z.coerce.number().int().positive(),
  brandId: z.coerce.number().int().positive(),
  status: z.enum(catalogStatusValues).default('ACTIVE'),
})

export type CreateProductInput = z.infer<typeof createProductSchema>

export const patchProductSchema = z
  .strictObject({
    name: z.string().trim().min(1).max(200).optional(),
    description: z.string().max(5000).nullish(),
    categoryId: z.coerce.number().int().positive().optional(),
    brandId: z.coerce.number().int().positive().optional(),
    status: z.enum(catalogStatusValues).optional(),
  })
  .refine(
    (data) =>
      data.name !== undefined ||
      data.description !== undefined ||
      data.categoryId !== undefined ||
      data.brandId !== undefined ||
      data.status !== undefined,
    { message: 'At least one field is required' },
  )

export type PatchProductInput = z.infer<typeof patchProductSchema>

export const createVariantSchema = z.strictObject({
  sku: z.string().trim().min(1).max(50),
  size: z.string().trim().min(1).max(20),
  color: z.string().trim().min(1).max(40),
  price: z.coerce.number().int().positive(),
})

export type CreateVariantInput = z.infer<typeof createVariantSchema>

export const patchVariantSchema = z
  .strictObject({
    sku: z.string().trim().min(1).max(50).optional(),
    size: z.string().trim().min(1).max(20).optional(),
    color: z.string().trim().min(1).max(40).optional(),
    price: z.coerce.number().int().positive().optional(),
    stock: z.number().optional(), // rejected: stock changes only via checkout/receipts
  })
  .superRefine((data, ctx) => {
    if (data.stock !== undefined) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['stock'],
        message: 'stock is not editable; it changes only through checkout and inventory receipts',
      })
    }
    if (
      data.sku === undefined &&
      data.size === undefined &&
      data.color === undefined &&
      data.price === undefined
    ) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: [],
        message: 'At least one of sku, size, color, price is required',
      })
    }
  })
  .transform((data) => ({
    sku: data.sku,
    size: data.size,
    color: data.color,
    price: data.price,
  }))

export type PatchVariantInput = z.infer<typeof patchVariantSchema>
