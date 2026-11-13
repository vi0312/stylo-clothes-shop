import type { OutgoingMail } from './mailer.js'

export interface PasswordResetMailInput {
  fullName: string
  email: string
  code: string
  ttlMinutes: number
}

const INK = '#1E1C18'
const MUTED = '#6C6255'
const PAPER = '#FBF8F1'
const SAND = '#EDE5D8'
const TERRACOTTA = '#A6512B'
const OUTLINE = '#E8DFD1'

const FONT = "Verdana,Geneva,sans-serif"

/** Customer names and emails are user input; the template must never interpolate them raw. */
function escapeHtml(value: string): string {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;')
}

export function passwordResetMail({
  fullName,
  email,
  code,
  ttlMinutes,
}: PasswordResetMailInput): Omit<OutgoingMail, 'to'> {
  const text = [
    `Xin chào ${fullName},`,
    '',
    `Mã xác nhận đặt lại mật khẩu Stylo của bạn là ${code}.`,
    `Mã có hiệu lực ${ttlMinutes} phút. Nhập mã trong ứng dụng Stylo để chọn mật khẩu mới.`,
    '',
    'Nếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua email này. Mật khẩu hiện tại vẫn an toàn.',
    '',
    'Stylo · Email tự động, vui lòng không trả lời.',
  ].join('\n')

  const html = `<!doctype html>
<html lang="vi">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Mã xác nhận đặt lại mật khẩu Stylo</title>
</head>
<body style="margin:0;padding:0;background:${SAND};">
<div style="display:none;max-height:0;overflow:hidden;opacity:0;color:transparent;">Mã xác nhận của bạn là ${code}, hiệu lực ${ttlMinutes} phút.</div>
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:${SAND};padding:32px 16px;">
<tr><td align="center">
<table role="presentation" width="480" cellpadding="0" cellspacing="0" style="width:100%;max-width:480px;background:${PAPER};border:1px solid ${OUTLINE};border-radius:16px;">
<tr><td style="padding:32px 32px 0;text-align:center;">
<div style="font-family:${FONT};font-size:26px;letter-spacing:6px;color:${INK};">stylo</div>
<div style="height:1px;background:${SAND};margin:24px 0 0;font-size:0;line-height:0;">&nbsp;</div>
</td></tr>
<tr><td style="padding:28px 32px 0;font-family:${FONT};font-size:15px;line-height:24px;color:${INK};">
<div style="margin:0 0 12px;font-size:22px;line-height:30px;font-weight:bold;color:${INK};">Đặt lại mật khẩu</div>
<p style="margin:0 0 8px;">Xin chào ${escapeHtml(fullName)},</p>
<p style="margin:0 0 24px;color:${MUTED};">Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản <strong style="color:${INK};">${escapeHtml(email)}</strong>.</p>
</td></tr>
<tr><td style="padding:0 32px;">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#FFFFFF;border:1px solid ${SAND};border-radius:12px;">
<tr><td style="padding:20px 16px 8px;text-align:center;font-family:${FONT};font-size:11px;letter-spacing:2px;text-transform:uppercase;color:${MUTED};">Mã xác nhận</td></tr>
<tr><td style="padding:0 16px 20px;text-align:center;font-family:Consolas,'Courier New',monospace;font-size:32px;letter-spacing:8px;color:${TERRACOTTA};">${code}</td></tr>
</table>
</td></tr>
<tr><td style="padding:16px 32px 0;font-family:${FONT};font-size:13px;line-height:22px;color:${MUTED};">Mã có hiệu lực <strong style="color:${INK};">${ttlMinutes} phút</strong>. Nhập mã này trong ứng dụng Stylo để chọn mật khẩu mới.</td></tr>
<tr><td style="padding:24px 32px 0;">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="border-left:3px solid ${TERRACOTTA};background:${SAND};">
<tr><td style="padding:12px 16px;font-family:${FONT};font-size:12px;line-height:20px;color:${MUTED};">Nếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua email này. Mật khẩu hiện tại vẫn an toàn.</td></tr>
</table>
</td></tr>
<tr><td style="padding:28px 32px 32px;">
<div style="border-top:1px solid ${SAND};padding-top:16px;font-family:${FONT};font-size:11px;line-height:18px;color:${MUTED};">Stylo · Cửa hàng quần áo<br>Email tự động, vui lòng không trả lời.</div>
</td></tr>
</table>
</td></tr>
</table>
</body>
</html>`

  return { subject: 'Mã xác nhận đặt lại mật khẩu Stylo', text, html }
}
