import type { RequestHandler } from 'express'
import { notFound } from '../utils/errors.js'

export const notFoundHandler: RequestHandler = (request, _response, next) => {
  next(notFound('NOT_FOUND', `Route ${request.method} ${request.originalUrl} does not exist`))
}
