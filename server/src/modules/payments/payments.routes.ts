import express, { Router } from 'express'
import { currentUser, requireAuth } from '../../middleware/auth.js'
import { parseBody, parseParams } from '../../middleware/validate.js'
import { notFound } from '../../utils/errors.js'
import { mockPaymentPage, mockResultPage } from './payments.mock-page.js'
import {
  mockCompleteSchema,
  orderPaymentParamsSchema,
  paymentParamsSchema,
} from './payments.schema.js'
import * as paymentsService from './payments.service.js'

export const paymentsRouter = Router()

paymentsRouter.post('/orders/:id/payments', requireAuth, async (request, response) => {
  const { id } = parseParams(orderPaymentParamsSchema, request)
  const result = await paymentsService.startPayment(currentUser(request).id, id)
  response.status(result.created ? 201 : 200).json(result.attempt)
})

// Browser-facing (no auth): the reference in the URL is the capability, and the app polls
// `GET /payments/:txnRef` afterwards.
paymentsRouter.get('/payments/mock/:txnRef', async (request, response) => {
  const { txnRef } = parseParams(paymentParamsSchema, request)
  const attempt = await paymentsService.findMockAttempt(txnRef)
  if (!attempt) throw notFound('NOT_FOUND', 'Payment not found')

  response.type('html').send(
    attempt.status === 'PENDING' ? mockPaymentPage(attempt) : mockResultPage(attempt),
  )
})

paymentsRouter.post(
  '/payments/mock/:txnRef/complete',
  express.urlencoded({ extended: false }),
  async (request, response) => {
    const { txnRef } = parseParams(paymentParamsSchema, request)
    const { result } = parseBody(mockCompleteSchema, request)
    const completion = await paymentsService.completeMockPayment(txnRef, result)
    if (!completion) throw notFound('NOT_FOUND', 'Payment not found')

    response.type('html').send(mockResultPage(completion.attempt))
  },
)

paymentsRouter.get('/payments/:txnRef', requireAuth, async (request, response) => {
  const { txnRef } = parseParams(paymentParamsSchema, request)
  response.json(await paymentsService.getPayment(currentUser(request), txnRef))
})
