import { Router } from 'express'
import { authRouter } from '../modules/auth/auth.routes.js'
import { cartRouter } from '../modules/cart/cart.routes.js'
import { catalogRouter, adminCatalogRouter } from '../modules/catalog/catalog.routes.js'
import { ordersRouter } from '../modules/orders/orders.routes.js'
import { paymentsRouter } from '../modules/payments/payments.routes.js'
import { profileRouter } from '../modules/profile/profile.routes.js'

export const apiRouter = Router()

apiRouter.use(authRouter)
apiRouter.use(profileRouter)
apiRouter.use(cartRouter)
apiRouter.use(ordersRouter)
apiRouter.use(paymentsRouter)
apiRouter.use(catalogRouter)
apiRouter.use(adminCatalogRouter)
