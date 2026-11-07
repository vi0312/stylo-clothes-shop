import type { MockAttempt } from './payments.service.js'

/**
 * The browser, not the app, opens these pages (`api.md`), so they are plain HTML in
 * Vietnamese and never use the JSON error envelope. `txnRef` is the capability.
 */
function escapeHtml(value: string): string {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;')
}

const money = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' })

function layout(title: string, body: string): string {
  return `<!doctype html>
<html lang="vi">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${escapeHtml(title)}</title>
<style>
  :root { color-scheme: light; }
  body {
    margin: 0; min-height: 100vh; display: grid; place-items: center;
    background: #faf7f2; color: #2b2622;
    font: 16px/1.55 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif;
  }
  main {
    width: min(420px, calc(100vw - 2rem)); background: #fff;
    border: 1px solid #e6ddd2; border-radius: 16px; padding: 1.5rem;
  }
  h1 { font-size: 1.15rem; margin: 0 0 1rem; }
  dl { display: grid; grid-template-columns: auto 1fr; gap: .4rem .9rem; margin: 0 0 1.25rem; }
  dt { color: #6c6157; }
  dd { margin: 0; text-align: right; font-variant-numeric: tabular-nums; }
  .amount { font-size: 1.35rem; font-weight: 600; }
  form { display: grid; gap: .6rem; }
  button {
    font: inherit; padding: .8rem 1rem; border-radius: 10px; cursor: pointer;
    border: 1px solid transparent;
  }
  button[value="success"] { background: #2f6b46; color: #fff; }
  button[value="failed"] { background: #fff; color: #8b2f2f; border-color: #d9b8b8; }
  button:focus-visible { outline: 3px solid #2b2622; outline-offset: 2px; }
  .status { font-weight: 600; margin: 0 0 1rem; }
  .ok { color: #2f6b46; }
  .bad { color: #8b2f2f; }
</style>
</head>
<body>
<main>${body}</main>
</body>
</html>`
}

function facts(attempt: MockAttempt): string {
  return `<dl>
  <dt>Mã đơn</dt><dd>${escapeHtml(attempt.orderCode)}</dd>
  <dt>Số tiền</dt><dd class="amount">${escapeHtml(money.format(attempt.amount))}</dd>
</dl>`
}

/** The two-button page the customer sees before choosing an outcome. */
export function mockPaymentPage(attempt: MockAttempt): string {
  const action = `/api/v1/payments/mock/${encodeURIComponent(attempt.txnRef)}/complete`
  return layout(
    'Thanh toán đơn hàng',
    `<h1>Thanh toán đơn hàng</h1>
${facts(attempt)}
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

  return layout(
    paid ? 'Thanh toán thành công' : 'Thanh toán thất bại',
    `<h1>${paid ? 'Thanh toán thành công' : 'Thanh toán thất bại'}</h1>
${status}
${facts(attempt)}
${hint}`,
  )
}
