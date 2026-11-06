import multer from 'multer'
import { MAX_PRODUCT_IMAGE_BYTES } from '../lib/fileStorage.js'

export const productImageUpload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: MAX_PRODUCT_IMAGE_BYTES, files: 1 },
})
