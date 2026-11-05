import { execFileSync } from 'node:child_process'
import { mkdirSync, rmSync } from 'node:fs'
import { createRequire } from 'node:module'
import { config } from 'dotenv'

const require = createRequire(import.meta.url)
const prismaCli = require.resolve('prisma/build/index.js')

export default function setup(): () => void {
  config({ path: '.env.test', override: true })

  const databaseUrl = process.env.DATABASE_URL
  if (!databaseUrl) {
    throw new Error('.env.test is missing DATABASE_URL')
  }

  const databaseName = decodeURIComponent(new URL(databaseUrl).pathname.replace(/^\//, ''))
  if (databaseName !== 'stylo_test') {
    throw new Error(`Integration tests refuse to run against "${databaseName}"`)
  }

  execFileSync(process.execPath, [prismaCli, 'migrate', 'deploy'], {
    stdio: 'inherit',
    env: process.env,
  })

  const uploadDir = process.env.UPLOAD_DIR
  if (!uploadDir) {
    throw new Error('.env.test is missing UPLOAD_DIR')
  }
  mkdirSync(uploadDir, { recursive: true })

  return () => {
    rmSync(uploadDir, { recursive: true, force: true })
  }
}
