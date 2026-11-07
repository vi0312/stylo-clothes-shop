import { escapeHtml, pageLayout, paymentFacts } from './payments.page.js'

export interface VnpayResultPageInput {
  ok: boolean
  title: string
  message: string
  orderCode?: string
  amount?: number
}

export function vnpayResultPage(input: VnpayResultPageInput): string {
  const facts =
    input.orderCode === undefined || input.amount === undefined
      ? ''
      : paymentFacts(input.orderCode, input.amount)
  const status = input.ok
    ? '<p class="status ok">Thanh toán thành công</p>'
    : '<p class="status bad">Thanh toán không thành công</p>'

  return pageLayout(
    input.title,
    `<h1>${escapeHtml(input.title)}</h1>
${status}
${facts}
<p>${escapeHtml(input.message)}</p>`,
  )
}
