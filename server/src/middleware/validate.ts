import type { Request } from 'express'
import type { ZodType } from 'zod'

/**
 * The ZodError raised here is the error handler's `VALIDATION_FAILED` path, so
 * routes get a typed body without repeating the parse.
 */
export function parseBody<T>(schema: ZodType<T>, request: Request): T {
  return schema.parse(request.body)
}

export function parseQuery<T>(schema: ZodType<T>, request: Request): T {
  return schema.parse(request.query)
}

export function parseParams<T>(schema: ZodType<T>, request: Request): T {
  return schema.parse(request.params)
}
