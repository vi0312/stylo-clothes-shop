/**
 * The browser, not the app, opens these pages (`api.md`), so they are plain HTML in
 * Vietnamese and never use the JSON error envelope.
 */
export function escapeHtml(value: string): string {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;')
}

export const money = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' })

export function pageLayout(title: string, body: string): string {
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

export function paymentFacts(orderCode: string, amount: number): string {
  return `<dl>
  <dt>Mã đơn</dt><dd>${escapeHtml(orderCode)}</dd>
  <dt>Số tiền</dt><dd class="amount">${escapeHtml(money.format(amount))}</dd>
</dl>`
}
