/** The MariaDB adapter surfaces a unique violation as the driver code, not only P2002. */
export function isUniqueViolation(error: unknown): boolean {
  if (error === null || typeof error !== 'object' || !('code' in error)) return false
  const { code } = error
  return code === 'P2002' || code === 'ER_DUP_ENTRY'
}
