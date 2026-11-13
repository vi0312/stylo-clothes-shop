import nodemailer from 'nodemailer'
import type { Env } from '../config/env.js'

export interface OutgoingMail {
  to: string
  subject: string
  text: string
  html: string
}

export interface Mailer {
  send(mail: OutgoingMail): Promise<void>
}

/**
 * `log` prints the whole message - reset code included - to the server log so a developer
 * without SMTP credentials can finish the flow. The code still never leaves the process
 * through an API response.
 */
export function createMailer(
  env: Env,
  log: (message: string) => void = (message) => console.info(message),
): Mailer {
  if (env.MAIL_TRANSPORT === 'log') {
    return {
      async send(mail) {
        log(`[mail] to=${mail.to} subject=${mail.subject}\n${mail.text}`)
      },
    }
  }

  const transport = nodemailer.createTransport({
    host: env.SMTP_HOST,
    port: env.SMTP_PORT,
    secure: env.SMTP_SECURE,
    auth: { user: env.SMTP_USER, pass: env.SMTP_PASS },
  })

  return {
    async send(mail) {
      await transport.sendMail({
        from: env.MAIL_FROM,
        to: mail.to,
        subject: mail.subject,
        text: mail.text,
        html: mail.html,
      })
    },
  }
}
