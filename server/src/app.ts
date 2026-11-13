import { randomUUID } from 'node:crypto'
import path from 'node:path'
import express from 'express'
import { pino } from 'pino'
import { pinoHttp } from 'pino-http'
import { getEnv } from './config/env.js'
import { createMailer, type Mailer } from './lib/mailer.js'
import { errorHandler } from './middleware/error.js'
import { notFoundHandler } from './middleware/notFound.js'
import { createApiRouter } from './routes/index.js'

export interface AppOptions {
  uploadDir?: string
  mailer?: Mailer
}

const ONE_DAY_SECONDS = 86_400

export function createApp(options: AppOptions = {}): express.Express {
  const env = getEnv()
  const uploadDir = path.resolve(options.uploadDir ?? env.UPLOAD_DIR)

  const logger = pino({
    level: env.NODE_ENV === 'test' ? 'silent' : 'info',
    ...(env.NODE_ENV === 'development' ? { transport: { target: 'pino-pretty' } } : {}),
  })

  const app = express()
  app.disable('x-powered-by')

  app.use(
    pinoHttp({
      logger,
      genReqId: (request, response) => {
        const incoming = request.headers['x-request-id']
        const id = typeof incoming === 'string' && incoming.length > 0 ? incoming : randomUUID()
        response.setHeader('x-request-id', id)
        return id
      },
      redact: ['req.headers.authorization', 'req.headers.cookie'],
    }),
  )

  app.use(express.json({ limit: '1mb' }))

  const mailer = options.mailer ?? createMailer(env, (message) => logger.info(message))

  app.use(
    '/uploads',
    express.static(uploadDir, {
      dotfiles: 'ignore',
      fallthrough: true,
      maxAge: ONE_DAY_SECONDS * 1000,
    }),
  )

  app.get('/health', (_request, response) => {
    response.json({ status: 'ok' })
  })

  app.use('/api/v1', createApiRouter({ mailer }))

  app.use(notFoundHandler)
  app.use(errorHandler)

  return app
}
