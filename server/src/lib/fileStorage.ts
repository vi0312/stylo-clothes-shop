import { randomUUID } from 'node:crypto'
import { mkdirSync, rmSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { getEnv } from '../config/env.js'

const PRODUCT_IMAGE_DIR = 'products'
export const MAX_PRODUCT_IMAGE_BYTES = 5 * 1024 * 1024

export type ImageExtension = 'jpg' | 'png' | 'webp'

const PNG_SIGNATURE = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]

/**
 * The contract verifies the bytes, not the multipart `Content-Type`: a text
 * file renamed to `.jpg` must be rejected.
 */
export function detectImageExtension(buffer: Buffer): ImageExtension | null {
  if (buffer.length >= 3 && buffer[0] === 0xff && buffer[1] === 0xd8 && buffer[2] === 0xff) {
    return 'jpg'
  }

  if (buffer.length >= PNG_SIGNATURE.length && PNG_SIGNATURE.every((byte, i) => buffer[i] === byte)) {
    return 'png'
  }

  if (
    buffer.length >= 12 &&
    buffer.toString('ascii', 0, 4) === 'RIFF' &&
    buffer.toString('ascii', 8, 12) === 'WEBP'
  ) {
    return 'webp'
  }

  return null
}

function uploadRoot(): string {
  return path.resolve(getEnv().UPLOAD_DIR)
}

export function writeProductImageFile(buffer: Buffer, extension: ImageExtension): string {
  const directory = path.join(uploadRoot(), PRODUCT_IMAGE_DIR)
  mkdirSync(directory, { recursive: true })

  const filename = `${randomUUID()}.${extension}`
  writeFileSync(path.join(directory, filename), buffer)

  return `/uploads/${PRODUCT_IMAGE_DIR}/${filename}`
}

/** Best-effort removal of files the API itself wrote, never used for validation. */
export function deleteUploadedFiles(urls: readonly string[]): void {
  const root = uploadRoot()

  for (const url of urls) {
    if (!url.startsWith('/uploads/')) {
      continue
    }

    const target = path.resolve(root, url.slice('/uploads/'.length))
    const relative = path.relative(root, target)
    if (relative.startsWith('..') || path.isAbsolute(relative)) {
      continue
    }

    try {
      rmSync(target, { force: true })
    } catch {
      // The row is already gone; a failed unlink must not fail the request.
    }
  }
}
