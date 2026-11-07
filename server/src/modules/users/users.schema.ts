import { z } from 'zod'

const userStatusValues = ['ACTIVE', 'DISABLED'] as const
const roleValues = ['CUSTOMER', 'ADMIN'] as const

export const adminUserListQuerySchema = z
  .object({
    q: z.preprocess(
      (v) => (v === '' || v === undefined ? undefined : v),
      z.string().trim().min(1).max(100).optional(),
    ),
    status: z.preprocess(
      (v) => (v === '' || v === undefined ? undefined : v),
      z.enum(userStatusValues).optional(),
    ),
    role: z.preprocess(
      (v) => (v === '' || v === undefined ? undefined : v),
      z.enum(roleValues).optional(),
    ),
    sort: z.preprocess(
      (v) => (v === '' || v === undefined ? undefined : v),
      z.enum(['newest', 'name_asc']).default('newest'),
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

export type AdminUserListQuery = z.infer<typeof adminUserListQuerySchema>

export const userIdParamsSchema = z.object({
  id: z.coerce.number().int().positive(),
})

export type UserIdParams = z.infer<typeof userIdParamsSchema>

export const patchUserStatusSchema = z.strictObject({
  status: z.enum(userStatusValues),
})

export type PatchUserStatusInput = z.infer<typeof patchUserStatusSchema>
