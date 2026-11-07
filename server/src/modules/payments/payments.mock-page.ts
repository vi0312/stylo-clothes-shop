import type { MockAttempt } from './payments.service.js'
import { escapeHtml, pageLayout, paymentFacts } from './payments.page.js'

/** The two-button page the customer sees before choosing an outcome. */
export function mockPaymentPage(attempt: MockAttempt): string {
  const action = `/api/v1/payments/mock/${encodeURIComponent(attempt.txnRef)}/complete`
  return pageLayout(
    'Thanh toán đơn hàng',
    `<h1>Thanh toán đơn hàng</h1>
${paymentFacts(attempt.orderCode, attempt.amount)}
<p>Đây là cổng thanh toán giả lập của môi trường demo. Chọn kết quả để tiếp tục.</p>
<form method="post" action="${escapeHtml(action)}">
  <button type="submit" name="result" value="success">Thanh toán thành công</button>
  <button type="submit" name="result" value="failed">Thanh toán thất bại</button>
</form>`,
  )
}

export function mockResultPage(attempt: MockAttempt): string {
  const paid = attempt.status === 'SUCCEEDED'
  const status = paid
    ? '<p class="status ok">Thanh toán thành công</p>'
    : '<p class="status bad">Thanh toán chưa hoàn tất</p>'
  const hint = paid
    ? '<p>Bạn có thể quay lại ứng dụng Stylo. Đơn hàng đã được ghi nhận thanh toán.</p>'
    : '<p>Bạn có thể quay lại ứng dụng Stylo và thử thanh toán lại.</p>'

  return pageLayout(
    paid ? 'Thanh toán thành công' : 'Thanh toán thất bại',
    `<h1>${paid ? 'Thanh toán thành công' : 'Thanh toán thất bại'}</h1>
${status}
${paymentFacts(attempt.orderCode, attempt.amount)}
${hint}`,
  )
}
