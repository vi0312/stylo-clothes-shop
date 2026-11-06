import { z } from 'zod'

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
