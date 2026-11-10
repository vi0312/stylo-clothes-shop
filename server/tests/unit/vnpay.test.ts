import { describe, expect, it } from 'vitest'
import {
  buildVnpayParams,
  buildVnpayUrl,
  signVnpParams,
  verifyVnpSignature,
  vnpEncode,
  type VnpayConfig,
} from '../../src/modules/payments/gateway/vnpay.js'

const config: VnpayConfig = {
  tmnCode: 'TESTTMN01',
  hashSecret: 'test-hash-secret',
  paymentUrl: 'https://sandbox.vnpayment.vn/paymentv2/vpcpay.html',
  returnUrl: 'http://127.0.0.1:8787/api/v1/payments/vnpay/return',
  ipAddress: '127.0.0.1',
}

const input = {
  txnRef: 'STY261104032000123',
  amount: 428_000,
  orderCode: 'STY-261104-A7K2',
  createdAt: new Date('2026-11-04T03:20:00.000Z'),
}

describe('VNPay request parameters', () => {
  it('sends the amount in minor units and the create date on the GMT+7 clock', () => {
    expect(buildVnpayParams(input, config)).toEqual({
      vnp_Version: '2.1.0',
      vnp_Command: 'pay',
      vnp_TmnCode: 'TESTTMN01',
      vnp_Amount: '42800000',
      vnp_CurrCode: 'VND',
      vnp_TxnRef: 'STY261104032000123',
      vnp_OrderInfo: 'Thanh toan don hang STY-261104-A7K2',
      vnp_OrderType: 'other',
      vnp_Locale: 'vn',
      vnp_ReturnUrl: 'http://127.0.0.1:8787/api/v1/payments/vnpay/return',
      vnp_IpAddr: '127.0.0.1',
      vnp_CreateDate: '20261104102000',
      // ATTEMPT_TTL_MS after createdAt: the attempt and the gateway URL expire together.
      vnp_ExpireDate: '20261104103500',
    })
  })

  it('rolls the timestamp over when UTC and Vietnam are on different days', () => {
    const params = buildVnpayParams(
      { ...input, createdAt: new Date('2026-11-03T17:30:00.000Z') },
      config,
    )
    expect(params.vnp_CreateDate).toBe('20261104003000')
  })

  it('does not put the hash secret into the URL', () => {
    expect(buildVnpayUrl(input, config)).not.toContain(config.hashSecret)
  })
})

describe('VNPay signature', () => {
  it('encodes like PHP urlencode, the gateway reference implementation', () => {
    expect(vnpEncode('Thanh toan (test) ~!')).toBe('Thanh+toan+%28test%29+%7E%21')
  })

  // The expected value is an HMAC over a literal canonical string, so this fails if the
  // sort order or the encoding drifts, not merely if the code disagrees with itself.
  it('signs the sorted, encoded key=value pairs with HMAC-SHA512', () => {
    expect(signVnpParams({ vnp_TxnRef: 'STY1', vnp_Amount: '100' }, 'secret')).toBe(
      'd1068aebf88373d16995bb8d20a40689ef04145cfc33694028cd162ed499e3c163f23a4a86d0e809c9cf42574e3ddb8c1bc4aa400682d4c76a087212a26eeb58',
    )
    expect(signVnpParams({ vnp_OrderInfo: 'Thanh toan (test) ~!' }, 'secret')).toBe(
      'c733b4cee4219526a81e3fc6bff31d8bb92df3e2b6f4bf98442fcb9981f4d62af46658bf178ea4bb12249a463b44fb58c818fc198e4e77611db9bc11711c5f2d',
    )
  })

  it('builds a URL whose signature covers every parameter', () => {
    const url = buildVnpayUrl(input, config)
    const parsed = new URL(url)
    expect(`${parsed.origin}${parsed.pathname}`).toBe(config.paymentUrl)

    const params = Object.fromEntries(parsed.searchParams) as Record<string, string>
    const provided = params.vnp_SecureHash
    expect(provided).toMatch(/^[0-9a-f]{128}$/)
    expect(params.vnp_OrderInfo).toBe('Thanh toan don hang STY-261104-A7K2')

    delete params.vnp_SecureHash
    expect(signVnpParams(params, config.hashSecret)).toBe(provided)
  })

  it('verifies a genuine callback and rejects every tampering', () => {
    const signed = { vnp_TxnRef: input.txnRef, vnp_Amount: '42800000', vnp_ResponseCode: '00' }
    const params = { ...signed, vnp_SecureHash: signVnpParams(signed, config.hashSecret) }

    expect(verifyVnpSignature(params, config.hashSecret)).toBe(true)
    expect(
      verifyVnpSignature(
        { ...params, vnp_SecureHash: params.vnp_SecureHash.toUpperCase() },
        config.hashSecret,
      ),
    ).toBe(true)
    expect(verifyVnpSignature({ ...params, vnp_Amount: '100' }, config.hashSecret)).toBe(false)
    expect(verifyVnpSignature(params, 'other-secret')).toBe(false)
    expect(verifyVnpSignature({ ...signed }, config.hashSecret)).toBe(false)
    expect(verifyVnpSignature({ ...params, vnp_SecureHash: 'abc' }, config.hashSecret)).toBe(false)
  })

  it('ignores the deprecated secure hash type when verifying', () => {
    const signed = { vnp_TxnRef: input.txnRef, vnp_Amount: '42800000' }
    const params = {
      ...signed,
      vnp_SecureHashType: 'HmacSHA512',
      vnp_SecureHash: signVnpParams(signed, config.hashSecret),
    }
    expect(verifyVnpSignature(params, config.hashSecret)).toBe(true)
  })
})
