# Cloudflare Worker + Hono + D1: Implementation Plan for PagoMóvil Listener

This document outlines the architecture, database schema, API contracts, and implementation roadmap for the Cloudflare Worker that ingests and processes payment notifications sent by the Android `PagoMóvil Listener` application, based on verified real-world notification captures from Venezuelan banks.

---

## 1. Real-World Bank Notification Analysis (From Captures)

### Capture Case A: BDV Receiving from BFC (The Partial Reference Trap)
* **Sender Receipt (BFC App):** `Comprobante: 23334023` (8 digits)
* **Receiver Notification (BDV Push):** `PagomovilBDV recibido: ... Ref: 334023334023 en fecha 11-11-26 hora: 22:29.` (12 digits)
* **Architectural Insight:** 
  The last 8 digits of `334023334023` are **`23334023`**. If your checkout performs an exact query (`reference = ?`), reconciliation fails immediately.
  Querying with `reference LIKE '%' || ?` matching the user-supplied 4 to 8 digits against the BDV reference solves this cleanly.

### Capture Case B: BFC Receiving Push (Missing Reference & Masked Phone)
* **Receiver Notification (BFC Push):** `Pago recibido: Ha recibido un Pago por Bs. 35,00 del Celular 0414***9152 a traves del servicio TUBFC 11/11/2026`
* **Architectural Insight:**
  1. **No Reference:** BFC push notifications omit the reference number entirely (`reference = null`).
  2. **Masked Phone:** Phone is formatted with asterisks (`0414***9152`), yielding `phone_prefix = "0414"` and `phone_last_digits = "9152"`.
  3. **Date Format:** `11/11/2026` (DD/MM/YYYY with slashes) vs BDV's `11-11-26` (DD-MM-YY with dashes).
  4. **Alternative Reconciliation:** For BFC, reconciliation cannot rely on reference. It **must** match by `phone_last_digits + amount_bs + date`.

---

## 2. Ingestion API Contract (`POST /webhooks/pagomovil`)

* **Headers:**
  * `X-Webhook-Secret: <SECRET_TOKEN>`
  * `Content-Type: application/json; charset=utf-8`
* **Payload from Android App:**
  ```json
  {
    "id": 1,
    "bank": "BDV",
    "amount_bs": 1.0,
    "raw_amount": "1,00",
    "phone_number": "0412-9897838",
    "phone_prefix": "0412",
    "phone_last_digits": "7838",
    "reference": "334023334023",
    "date": "11-11-26",
    "time": "22:29",
    "raw_title": "PagomovilBDV recibido",
    "raw_body": "Recibiste un PagomovilBDV por Bs.1,00 del 0412-9897838 Ref: 334023334023 en fecha 11-11-26 hora: 22:29.",
    "received_at": 1731382140000
  }
  ```

> **IDEMPOTENCY CONTRACT (HTTP 200 vs 409):**
> When a duplicate notification is received, the Worker **MUST return HTTP 200 OK** with `{ "ok": true, "status": "duplicate" }`.
> Returning `409` causes Android's `PaymentApiClient` to classify the delivery as failed (`!response.isSuccessful`), leaving it in `FAILED` status and triggering indefinite `WorkManager` retries.

---

## 3. Database Schema (`schema.sql`)

```sql
CREATE TABLE IF NOT EXISTS pagos (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    bank TEXT NOT NULL,
    amount_bs REAL NOT NULL,
    raw_amount TEXT NOT NULL,
    phone_number TEXT NOT NULL,
    phone_prefix TEXT,
    phone_last_digits TEXT,
    reference TEXT,
    date TEXT NOT NULL,
    time TEXT,
    raw_title TEXT,
    raw_body TEXT,
    received_at INTEGER NOT NULL,
    used INTEGER NOT NULL DEFAULT 0,
    used_at TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Index for searching references (e.g. BDV)
CREATE INDEX IF NOT EXISTS idx_pagos_reference ON pagos(reference);

-- Index for searching by phone last digits + amount (e.g. BFC)
CREATE INDEX IF NOT EXISTS idx_pagos_phone_amount ON pagos(phone_last_digits, amount_bs, used);

-- Unique index to prevent duplicate ingestion for banks with reference numbers
CREATE UNIQUE INDEX IF NOT EXISTS idx_pagos_unique_ref ON pagos(reference, amount_bs, date)
WHERE reference IS NOT NULL;

-- Unique index fallback for banks without reference (BFC) using phone, amount, and raw notification body
CREATE UNIQUE INDEX IF NOT EXISTS idx_pagos_unique_noref ON pagos(phone_number, amount_bs, date, raw_body)
WHERE reference IS NULL;
```

---

## 4. Cloudflare Worker Code (`src/index.ts`)

```typescript
import { Hono } from 'hono'

type Bindings = {
  DB: D1Database
  WEBHOOK_SECRET: string
  FORWARD_WEBHOOK_URL?: string
}

type PaymentPayload = {
  id: number
  bank: string
  amount_bs: number
  raw_amount: string
  phone_number: string
  phone_prefix?: string | null
  phone_last_digits?: string | null
  reference?: string | null
  date: string
  time?: string | null
  raw_title: string
  raw_body: string
  received_at: number
}

const app = new Hono<{ Bindings: Bindings }>()

// 1. Security Middleware
app.use('*', async (c, next) => {
  // Support both X-Webhook-Secret (sent by Android app) and x-api-key / Bearer
  const secret = c.req.header('x-webhook-secret') 
    || c.req.header('x-api-key') 
    || c.req.header('authorization')?.replace('Bearer ', '')

  if (!secret || secret !== c.env.WEBHOOK_SECRET) {
    return c.json({ ok: false, error: 'Unauthorized' }, 401)
  }
  await next()
})

// 2. Ingestion Webhook (Called by Android App)
app.post('/webhooks/pagomovil', async (c) => {
  const data = await c.req.json<PaymentPayload>()

  try {
    await c.env.DB.prepare(`
      INSERT INTO pagos (
        bank, amount_bs, raw_amount, phone_number, phone_prefix,
        phone_last_digits, reference, date, time, raw_title,
        raw_body, received_at, used
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
    `).bind(
      data.bank,
      data.amount_bs,
      data.raw_amount,
      data.phone_number,
      data.phone_prefix || null,
      data.phone_last_digits || null,
      data.reference || null,
      data.date,
      data.time || null,
      data.raw_title,
      data.raw_body,
      data.received_at
    ).run()

    // Optional: Push pattern to forward payment in real-time to your main app
    if (c.env.FORWARD_WEBHOOK_URL) {
      c.executionCtx.waitUntil(
        fetch(c.env.FORWARD_WEBHOOK_URL, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(data)
        }).catch((err) => console.error('Push forwarding error:', err))
      )
    }

    return c.json({ ok: true, status: 'created', message: 'Payment recorded' }, 201)
  } catch (error: any) {
    // Detect SQLite unique constraint violation (D1 / SQLite error code 19 / SQLITE_CONSTRAINT)
    const errorMsg = String(error?.message || error)
    if (errorMsg.includes('UNIQUE') || errorMsg.includes('constraint')) {
      // Return 200 OK so Android stops retrying via WorkManager
      return c.json({ ok: true, status: 'duplicate', message: 'Payment already exists' }, 200)
    }
    return c.json({ ok: false, error: errorMsg }, 500)
  }
})

// 3. Dual-Strategy Verification Endpoint (Called by Checkout / POS)
app.post('/api/pagos/verificar', async (c) => {
  const body = await c.req.json<{
    referencia?: string
    telefonoUltimos4?: string
    montoEsperado: number
  }>()

  const { referencia, telefonoUltimos4, montoEsperado } = body

  if (!montoEsperado) {
    return c.json({ verificado: false, motivo: 'montoEsperado es requerido' }, 400)
  }

  let pago: any = null

  if (referencia && referencia.trim().length >= 4) {
    // Strategy A (Reference-based, e.g. BDV): Match suffix
    pago = await c.env.DB.prepare(`
      SELECT * FROM pagos 
      WHERE reference LIKE ? AND used = 0 
      ORDER BY received_at DESC 
      LIMIT 1
    `).bind(`%${referencia.trim()}`).first()
  } else if (telefonoUltimos4 && telefonoUltimos4.trim().length === 4) {
    // Strategy B (Phone-based, e.g. BFC): Match last 4 phone digits
    pago = await c.env.DB.prepare(`
      SELECT * FROM pagos 
      WHERE phone_last_digits = ? AND used = 0 
      ORDER BY received_at DESC 
      LIMIT 1
    `).bind(telefonoUltimos4.trim()).first()
  } else {
    return c.json({ 
      verificado: false, 
      motivo: 'Debe enviar al menos referencia (min 4 dígitos) o telefonoUltimos4 (4 dígitos)' 
    }, 400)
  }

  if (!pago) {
    return c.json({ verificado: false, motivo: 'Pago no encontrado o ya utilizado' }, 404)
  }

  // Exact amount check (allowing 0.01 tolerance for floating point rounding)
  if (Math.abs(Number(pago.amount_bs) - Number(montoEsperado)) > 0.01) {
    return c.json({ 
      verificado: false, 
      motivo: 'El monto no coincide', 
      montoRecibido: pago.amount_bs,
      montoEsperado 
    }, 400)
  }

  // Mark as used atomically
  await c.env.DB.prepare(`
    UPDATE pagos 
    SET used = 1, used_at = datetime('now') 
    WHERE id = ?
  `).bind(pago.id).run()

  return c.json({ verificado: true, pago })
})

export default app
```

---

## 5. Deployment Commands

```bash
# 1. Create database
npx wrangler d1 create pagomovil-db

# 2. Apply migrations
npx wrangler d1 execute pagomovil-db --remote --file=./schema.sql

# 3. Set your secret
npx wrangler secret put WEBHOOK_SECRET

# 4. Deploy worker
npx wrangler deploy
```
