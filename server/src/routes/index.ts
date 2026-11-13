import { Router } from 'express'
import type { Mailer } from '../lib/mailer.js'
import { createAuthRouter } from '../modules/auth/auth.routes.js'
import { cartRouter } from '../modules/cart/cart.routes.js'
import { catalogRouter, adminCatalogRouter } from '../modules/catalog/catalog.routes.js'
import { adminInventoryRouter } from '../modules/inventory/inventory.routes.js'
import { ordersRouter, adminOrdersRouter } from '../modules/orders/orders.routes.js'
import { paymentsRouter } from '../modules/payments/payments.routes.js'
import { profileRouter } from '../modules/profile/profile.routes.js'
import { adminReportsRouter } from '../modules/reports/reports.routes.js'
import { adminUsersRouter } from '../modules/users/users.routes.js'

export interface ApiDependencies {
  mailer: Mailer
}

export function createApiRouter({ mailer }: ApiDependencies): Router {
  const apiRouter = Router()

  apiRouter.use(createAuthRouter(mailer))
  apiRouter.use(profileRouter)
  apiRouter.use(cartRouter)
  apiRouter.use(ordersRouter)
  apiRouter.use(adminOrdersRouter)
  apiRouter.use(paymentsRouter)
  apiRouter.use(catalogRouter)
  apiRouter.use(adminCatalogRouter)
  apiRouter.use(adminInventoryRouter)
  apiRouter.use(adminReportsRouter)
  apiRouter.use(adminUsersRouter)

  return apiRouter
}
