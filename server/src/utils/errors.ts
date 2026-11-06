export class AppError extends Error {
  constructor(
    readonly code: string,
    readonly status: number,
    message: string,
    readonly details?: unknown,
  ) {
    super(message)
    this.name = 'AppError'
  }
}

export function badRequest(code: string, message: string, details?: unknown): AppError {
  return new AppError(code, 400, message, details)
}

export function unauthorized(code: string, message: string): AppError {
  return new AppError(code, 401, message)
}

export function forbidden(code: string, message: string): AppError {
  return new AppError(code, 403, message)
}

export function notFound(code: string, message: string): AppError {
  return new AppError(code, 404, message)
}

export function conflict(code: string, message: string, details?: unknown): AppError {
  return new AppError(code, 409, message, details)
}

export function payloadTooLarge(code: string, message: string): AppError {
  return new AppError(code, 413, message)
}
