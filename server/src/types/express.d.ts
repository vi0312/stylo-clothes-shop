import type { PublicUser } from '../modules/users/user.dto.js'

declare global {
  namespace Express {
    interface Request {
      user?: PublicUser
    }
  }
}

export {}
