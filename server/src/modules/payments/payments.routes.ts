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
import { handleVnpayIpn, handleVnpayReturn } from './payments.vnpay.js'
import { vnpayResultPage } from './payments.vnpay-page.js'

export const paymentsRouter = Router()

paymentsRouter.post('/orders/:id/payments', requireAuth, async (request, response) => {
  const { id } = parseParams(orderPaymentParamsSchema, request)
  const result = await paymentsService.startPayment(currentUser(request).id, id)
  response.status(result.created ? 201 : 200).json(result.attempt)
})

// VNPay's browser redirect (`return`) and server-to-server callback (`ipn`): both verify
// the signature before touching state, and both answer in the gateway's own format.
paymentsRouter.get('/payments/vnpay/return', async (request, response) => {
  try {
    const page = await handleVnpayReturn(request.query)
    response.status(page.status).type('html').send(page.html)
  } catch (error) {
    request.log?.error({ err: error }, 'vnpay return failed')
    response.status(500).type('html').send(
      vnpayResultPage({
        ok: false,
        title: 'Không xử lý được phản hồi',
        message: 'Đã xảy ra lỗi khi xác nhận thanh toán. Vui lòng kiểm tra lại đơn hàng.',
      }),
    )
  }
})

paymentsRouter.get('/payments/vnpay/ipn', async (request, response) => {
  try {
    response.json(await handleVnpayIpn(request.query))
  } catch (error) {
    request.log?.error({ err: error }, 'vnpay ipn failed')
    response.json({ RspCode: '99', Message: 'Unknown error' })
  }
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
