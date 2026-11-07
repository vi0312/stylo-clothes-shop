# Stylo server

Express 5 + Prisma 7 + MySQL 8 API. Setup, migrations and endpoint docs live in
[`../docs/`](../docs/); this file records the demo accounts the seed creates.

## Seeded accounts

`npm run db:reset` drops the schema, applies migrations and runs `prisma/seed.ts`. The
seed is deterministic and idempotent; running it again (`npm run db:seed`) does not
duplicate rows.

| Role | Email | Password |
|---|---|---|
| Admin | `SEED_ADMIN_EMAIL` (default `admin@stylo.local`) | `SEED_ADMIN_PASSWORD` (default `Admin@12345`) |
| Customer 01–11 (active) | `customer01@stylo.local` … `customer11@stylo.local` | `Customer@12345` |
| Customer 12 (disabled) | `customer12@stylo.local` | `Customer@12345` |

Customer `customer11@stylo.local` deliberately has no cart and no orders.
`customer12@stylo.local` is `DISABLED` so the login and admin user screens can show the
blocked-account states. Customer `customer01@stylo.local` is the account used by the
manual demo script.

The seed also creates 5 categories, 4 brands, 20 products / 55 variants, 22 addresses,
3 inventory receipts and 20 orders across every status, payment method and payment
state. See [`../docs/testing.md`](../docs/testing.md#seed-and-demo-data) for the full
contract, including the low-stock and out-of-stock variants.

## Commands

```bash
npm install
npm run db:reset   # reset + seed
npm run dev        # API on PORT (default 8787)
npm test
```
