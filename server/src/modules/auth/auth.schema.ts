import { z } from 'zod'

const emailSchema = z.email().max(255)
const passwordSchema = z.string().min(8).max(100)

export const registerSchema = z.strictObject({
  email: emailSchema,
  password: passwordSchema,
  fullName: z.string().min(1).max(100),
  phone: z.string().max(20).optional(),
})

export const loginSchema = z.strictObject({
  email: emailSchema,
  password: z.string().min(1).max(100),
})

export const refreshTokenSchema = z.strictObject({
  refreshToken: z.string().min(1).max(200),
})

export const forgotPasswordSchema = z.strictObject({
  email: emailSchema,
})

export const resetPasswordSchema = z.strictObject({
  email: emailSchema,
  code: z.string().regex(/^\d{6}$/),
  password: passwordSchema,
})

export type RegisterInput = z.infer<typeof registerSchema>
export type LoginInput = z.infer<typeof loginSchema>
export type ForgotPasswordInput = z.infer<typeof forgotPasswordSchema>
export type ResetPasswordInput = z.infer<typeof resetPasswordSchema>
