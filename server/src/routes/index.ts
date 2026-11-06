import { Router } from 'express'
import { authRouter } from '../modules/auth/auth.routes.js'
import { catalogRouter, adminCatalogRouter } from '../modules/catalog/catalog.routes.js'

export const apiRouter = Router()

apiRouter.use(authRouter)
apiRouter.use(catalogRouter)
apiRouter.use(adminCatalogRouter)
