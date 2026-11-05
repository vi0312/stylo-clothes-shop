import { PrismaMariaDb } from '@prisma/adapter-mariadb'
import { getEnv, parseDatabaseUrl } from '../config/env.js'
import { PrismaClient } from '../generated/prisma/client.js'

const { host, port, user, password, database } = parseDatabaseUrl(getEnv().DATABASE_URL)

const adapter = new PrismaMariaDb({
  host,
  port,
  user,
  password,
  database,
  connectionLimit: 10,
})

export const prisma = new PrismaClient({ adapter })
