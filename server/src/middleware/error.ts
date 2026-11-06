import type { ErrorRequestHandler } from 'express'
import multer from 'multer'
import { ZodError } from 'zod'
import { Prisma } from '../generated/prisma/client.js'
import { AppError } from '../utils/errors.js'

interface ErrorBody {
  error: {
    code: string
    message: string
    details?: unknown
  }
}

function isJsonParseError(error: unknown): boolean {
  return error instanceof SyntaxError && 'type' in error && error.type === 'entity.parse.failed'
}

export const errorHandler: ErrorRequestHandler = (error, request, response, next) => {
  if (response.headersSent) {
    next(error)
    return
  }

  let status = 500
  let code = 'INTERNAL'
  let message = 'Internal server error'
  let details: unknown

  if (error instanceof AppError) {
    status = error.status
    code = error.code
    message = error.message
    details = error.details
  } else if (error instanceof ZodError) {
    status = 400
    code = 'VALIDATION_FAILED'
    message = 'Request validation failed'
    details = error.issues.map((issue) => ({
      field: issue.path.length > 0 ? issue.path.join('.') : issue.code === 'unrecognized_keys' ? issue.keys.join(',') : '',
      message: issue.message,
    }))
  } else if (error instanceof SyntaxError && 'type' in error && error.type === 'entity.parse.failed') {
    status = 400
    code = 'VALIDATION_FAILED'
    message = 'Malformed JSON body'
  } else if (error instanceof multer.MulterError) {
    if (error.code === 'LIMIT_FILE_SIZE') {
      status = 413
      code = 'FILE_TOO_LARGE'
      message = 'Uploaded file exceeds the size limit'
    } else {
      status = 400
      code = 'VALIDATION_FAILED'
      message = 'Invalid file upload'
      details = [{ field: error.field ?? 'file', message: error.message }]
    }
  } else if (error instanceof Prisma.PrismaClientKnownRequestError) {
    if (error.code === 'P2025') {
      status = 404
      code = 'NOT_FOUND'
      message = 'Resource not found'
    } else if (error.code === 'P2003') {
      status = 409
      code = 'IN_USE'
      message = 'Resource is still referenced and cannot be removed'
    }
  }

  if (status === 500) {
    request.log?.error({ err: error }, 'unhandled error')
  }

  const body: ErrorBody = {
    error: {
      code,
      message,
      ...(details === undefined ? {} : { details }),
    },
  }
  response.status(status).json(body)
}
