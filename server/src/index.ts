import { createApp } from './app.js'
import { getEnv } from './config/env.js'
import { prisma } from './lib/prisma.js'

const env = getEnv()
const app = createApp()

const server = app.listen(env.PORT, '0.0.0.0', () => {
  console.log(`Stylo API listening on http://127.0.0.1:${env.PORT}`)
})

let shuttingDown = false

async function shutdown(signal: NodeJS.Signals): Promise<void> {
  if (shuttingDown) return
  shuttingDown = true
  console.log(`${signal} received, shutting down`)
  server.close()
  await prisma.$disconnect()
  process.exit(0)
}

process.on('SIGINT', () => void shutdown('SIGINT'))
process.on('SIGTERM', () => void shutdown('SIGTERM'))
