import { randomInt } from 'node:crypto'

export const RESET_CODE_LENGTH = 6
const RESET_CODE_SPACE = 10

export function generateResetCode(nextInt: (max: number) => number = randomInt): string {
  let code = ''
  for (let index = 0; index < RESET_CODE_LENGTH; index += 1) {
    code += String(nextInt(RESET_CODE_SPACE))
  }
  return code
}
