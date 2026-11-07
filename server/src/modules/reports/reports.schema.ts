import { z } from 'zod'

const optionalDate = z.preprocess(
  (value) => (value === '' || value === undefined ? undefined : value),
  z.iso.date().optional(),
)

const groupByField = z.preprocess(
  (value) => (value === '' || value === undefined ? undefined : value),
  z.enum(['day', 'month']).default('day'),
)

const limitField = z.preprocess(
  (value) => (value === '' || value === undefined ? undefined : value),
  z.coerce.number().int().min(1).max(50).default(10),
)

const thresholdField = z.preprocess(
  (value) => (value === '' || value === undefined ? undefined : value),
  z.coerce.number().int().min(0).max(1000).optional(),
)

// Unknown query params are ignored, per the contract conventions.
export const revenueQuerySchema = z
  .object({ from: optionalDate, to: optionalDate, groupBy: groupByField })
  .refine((data) => !data.from || !data.to || data.from <= data.to, {
    message: '`from` must be on or before `to`',
    path: ['from'],
  })

export const topProductsQuerySchema = z
  .object({ from: optionalDate, to: optionalDate, limit: limitField })
  .refine((data) => !data.from || !data.to || data.from <= data.to, {
    message: '`from` must be on or before `to`',
    path: ['from'],
  })

export const ordersByStatusQuerySchema = z
  .object({ from: optionalDate, to: optionalDate })
  .refine((data) => !data.from || !data.to || data.from <= data.to, {
    message: '`from` must be on or before `to`',
    path: ['from'],
  })

export const lowStockQuerySchema = z.object({ threshold: thresholdField })

export const inventoryCostQuerySchema = z
  .object({ from: optionalDate, to: optionalDate })
  .refine((data) => !data.from || !data.to || data.from <= data.to, {
    message: '`from` must be on or before `to`',
    path: ['from'],
  })

export type RevenueQuery = z.infer<typeof revenueQuerySchema>
export type TopProductsQuery = z.infer<typeof topProductsQuerySchema>
export type OrdersByStatusQuery = z.infer<typeof ordersByStatusQuerySchema>
export type LowStockQuery = z.infer<typeof lowStockQuerySchema>
export type InventoryCostQuery = z.infer<typeof inventoryCostQuerySchema>
