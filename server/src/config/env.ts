import 'dotenv/config'
import { z } from 'zod'

const envSchema = z
  .object({
    NODE_ENV: z.enum(['development', 'test', 'production']).default('development'),
    PORT: z.coerce.number().int().min(1).max(65535).default(8787),
    DATABASE_URL: z.string().min(1),
    PUBLIC_BASE_URL: z.url().default('http://127.0.0.1:8787'),
    JWT_ACCESS_SECRET: z.string().min(1),
    ACCESS_TOKEN_TTL_SECONDS: z.coerce.number().int().positive().default(900),
    REFRESH_TOKEN_TTL_DAYS: z.coerce.number().int().positive().default(30),
    PAYMENT_PROVIDER: z.enum(['MOCK', 'VNPAY']).default('MOCK'),
    SHIPPING_FEE: z.coerce.number().int().nonnegative().default(30000),
    LOW_STOCK_THRESHOLD: z.coerce.number().int().nonnegative().default(5),
    UPLOAD_DIR: z.string().min(1).default('uploads'),
    SEED_ADMIN_EMAIL: z.email().default('admin@stylo.local'),
    SEED_ADMIN_PASSWORD: z.string().min(8).default('Admin@12345'),
    SEED_ADMIN_NAME: z.string().min(1).default('Stylo Admin'),
    VNPAY_TMN_CODE: z.string().default(''),
    VNPAY_HASH_SECRET: z.string().default(''),
    VNPAY_URL: z.url().default('https://sandbox.vnpayment.vn/paymentv2/vpcpay.html'),
    // VNPay requires the customer IP in the request; the local demo has no public one.
    VNPAY_IP_ADDR: z.string().min(1).default('127.0.0.1'),
  })
  .superRefine((value, ctx) => {
    if (value.PAYMENT_PROVIDER !== 'VNPAY') return
    if (!value.VNPAY_TMN_CODE) {
      ctx.addIssue({ code: 'custom', path: ['VNPAY_TMN_CODE'], message: 'required when PAYMENT_PROVIDER=VNPAY' })
    }
    if (!value.VNPAY_HASH_SECRET) {
      ctx.addIssue({ code: 'custom', path: ['VNPAY_HASH_SECRET'], message: 'required when PAYMENT_PROVIDER=VNPAY' })
    }
  })

export type Env = z.infer<typeof envSchema>

export function parseEnv(source: NodeJS.ProcessEnv = process.env): Env {
  const result = envSchema.safeParse(source)
  if (!result.success) {
    const problems = result.error.issues
      .map((issue) => `${issue.path.join('.') || '(root)'}: ${issue.message}`)
      .join('; ')
    throw new Error(`Invalid environment configuration: ${problems}`)
  }
  return result.data
}

export interface DatabaseConfig {
  host: string
  port: number
  user: string
  password: string
  database: string
}

export function parseDatabaseUrl(raw: string): DatabaseConfig {
  const url = new URL(raw)
  if (url.protocol !== 'mysql:' && url.protocol !== 'mariadb:') {
    throw new Error(`DATABASE_URL must use the mysql:// protocol, got ${url.protocol}//`)
  }
  const database = decodeURIComponent(url.pathname.replace(/^\//, ''))
  if (!database) {
    throw new Error('DATABASE_URL is missing a database name')
  }
  return {
    host: url.hostname,
    port: url.port ? Number(url.port) : 3306,
    user: decodeURIComponent(url.username),
    password: decodeURIComponent(url.password),
    database,
  }
}

let cachedEnv: Env | undefined

export function getEnv(): Env {
  cachedEnv ??= parseEnv()
  return cachedEnv
}
