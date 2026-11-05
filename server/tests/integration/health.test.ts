import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import request from 'supertest'
import { afterAll, describe, expect, it } from 'vitest'
import { createApp } from '../../src/app.js'

const uploadDir = mkdtempSync(path.join(tmpdir(), 'stylo-uploads-'))
const app = createApp({ uploadDir })

afterAll(() => {
  rmSync(uploadDir, { recursive: true, force: true })
})

describe('GET /health', () => {
  it('answers 200 with a status payload and no auth', async () => {
    const response = await request(app).get('/health')

    expect(response.status).toBe(200)
    expect(response.body).toEqual({ status: 'ok' })
  })
})

describe('error envelope', () => {
  it('returns NOT_FOUND for an unknown API route', async () => {
    const response = await request(app).get('/api/v1/nope')

    expect(response.status).toBe(404)
    expect(response.body).toEqual({
      error: { code: 'NOT_FOUND', message: expect.any(String) },
    })
  })

  it('returns VALIDATION_FAILED for malformed JSON', async () => {
    const response = await request(app)
      .post('/api/v1/nope')
      .set('Content-Type', 'application/json')
      .send('{"broken":')

    expect(response.status).toBe(400)
    expect(response.body.error.code).toBe('VALIDATION_FAILED')
  })
})

describe('GET /uploads/**', () => {
  it('serves a stored file with a one-day cache header', async () => {
    writeFileSync(path.join(uploadDir, 'hello.txt'), 'hello')

    const response = await request(app).get('/uploads/hello.txt')

    expect(response.status).toBe(200)
    expect(response.text).toBe('hello')
    expect(response.headers['cache-control']).toContain('max-age=86400')
  })

  it('returns the error envelope for a missing file', async () => {
    const response = await request(app).get('/uploads/missing.txt')

    expect(response.status).toBe(404)
    expect(response.body.error.code).toBe('NOT_FOUND')
  })
})
