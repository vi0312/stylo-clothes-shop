import mariadb from 'mariadb'
import { getEnv, parseDatabaseUrl } from '../../src/config/env.js'
import { prisma } from '../../src/lib/prisma.js'

interface TableRow {
  TABLE_NAME: string
}

/**
 * TRUNCATE refuses to touch a table referenced by any FK, even when the child is
 * empty, so the flags must run on the same session as the truncates.
 */
export async function resetDb(): Promise<void> {
  const tables = await prisma.$queryRaw<TableRow[]>`
    SELECT TABLE_NAME
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_TYPE = 'BASE TABLE'
      AND TABLE_NAME <> '_prisma_migrations'
  `

  const config = parseDatabaseUrl(getEnv().DATABASE_URL)
  const connection = await mariadb.createConnection({
    host: config.host,
    port: config.port,
    user: config.user,
    password: config.password,
    database: config.database,
  })

  try {
    await connection.query('SET FOREIGN_KEY_CHECKS = 0')
    for (const table of tables) {
      await connection.query(`TRUNCATE TABLE \`${table.TABLE_NAME}\``)
    }
    await connection.query('SET FOREIGN_KEY_CHECKS = 1')
  } finally {
    await connection.end()
  }
}
