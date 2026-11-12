# Cloudflare Worker + Hono + D1: Implementation Plan for PagoMóvil Listener

This document outlines the architecture, database schema, API contracts, and implementation roadmap for the Cloudflare Worker that ingests and processes payment notifications sent by the Android `PagoMóvil Listener` application.

---

## 1. System Architecture

```
[ Android Device ]
  │
  │ (HTTP POST with X-Webhook-Secret)
  ▼
[ Cloudflare Worker (Hono) ]
  ├── 1. Authentication Middleware (Validates X-Webhook-Secret)
  ├── 2. Payload Validation & Normalization
  ├── 3. D1 Persistence (SQLite: Idempotent INSERT)
  └── 4. Real-time Forwarding (Optional Push to target ERP/Web App)
  │
  ▼
[ Cloudflare D1 Database ]
  (Stores payments, references, amounts, and reconciliation status)
  │
  ▲
  │ (HTTP POST /verificar with reference & expected amount)
[ Merchant E-Commerce / Point of Sale ]
```

---

## 2. API Contract Alignment with Android Client

### Ingestion Endpoint (`POST /webhooks/pagomovil`)

The Android application sends the following JSON payload via `PaymentApiClient`:

* **Header:** `X-Webhook-Secret: <SECRET_TOKEN>`
* **Content-Type:** `application/json; charset=utf-8`
* **User-Agent:** `PagoMovil-Listener/1.0`

#### Incoming JSON Body Schema:
```json
{
  "id": 1,
  "bank": "BDV",
  "amount_bs": 150.50,
  "raw_amount": "150,50",
  "phone_number": "0412-9897838",
  "phone_prefix": "0412",
  "phone_last_digits": "7838",
  "reference": "334023334023",
  "date": "11-11-26",
  "time": "22:29",
  "raw_title": "PagomovilBDV recibido",
  "raw_body": "Recibiste un PagomovilBDV por Bs.150,50 del 0412-9897838 Ref: 334023334023...",
  "received_at": 1731382140000
}
```

> **CRITICAL ARCHITECTURAL REQUIREMENT (Idempotency Response):**
> When a duplicate payment is detected, the Worker **MUST return HTTP `200 OK`** (with `{ "ok": true, "status": "duplicate" }`) instead of `409 Conflict`.
> If the Worker returns `409`, Android's `PaymentApiClient` treats it as a failed delivery (`!response.isSuccessful`), keeps the status as `FAILED`, and `WorkManager` will retry the request indefinitely with exponential backoff. Returning `200 OK` allows the Android client to mark the payment as `SYNCED` locally and terminate retries.

---

## 3. Database Schema (`schema.sql`)

Cloudflare D1 is SQLite. Note that banks like **BFC do not provide reference numbers in push notifications** (`reference` can be `NULL`). Therefore, idempotency must account for both reference-based and phone/date-based payments.

```sql
-- D1 SQLite Migration
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

-- Index for fast reconciliation by reference (last digits matching)
CREATE INDEX IF NOT EXISTS idx_pagos_reference ON pagos(reference);

-- Index for phone + date + amount reconciliation (essential for BFC or missing references)
CREATE INDEX IF NOT EXISTS idx_pagos_reconciliation ON pagos(phone_last_digits, amount_bs, date);

-- Unique index to prevent duplicate ingestion for banks with reference numbers
CREATE UNIQUE INDEX IF NOT EXISTS idx_pagos_unique_ref ON pagos(reference, amount_bs, date)
WHERE reference IS NOT NULL;

-- Unique index fallback for banks without reference (e.g. BFC) using phone, amount, and exact timestamp
CREATE UNIQUE INDEX IF NOT EXISTS idx_pagos_unique_noref ON pagos(phone_number, amount_bs, date, raw_body)
WHERE reference IS NULL;
```

---

## 4. Worker Code Architecture (Hono + TypeScript)

### Project Structure
```
pagomovil-worker/
├── package.json
├── tsconfig.json
├── wrangler.toml
├── schema.sql
└── src/
    ├── index.ts              # Hono app entrypoint & routing
    ├── middleware/
    │   └── auth.ts           # Validates X-Webhook-Secret / API Key
    ├── routes/
    │   ├── webhook.ts        # POST /webhooks/pagomovil
    │   ├── verify.ts         # POST /api/pagos/verificar
    │   └── list.ts           # GET /api/pagos (Internal admin/audit)
    └── types/
        └── payment.ts        # TypeScript interfaces for request & D1
```

### Proposed Endpoint Implementations

#### 1. Ingestion (`POST /webhooks/pagomovil`)
- Reads headers and validates against `env.WEBHOOK_SECRET`.
- Extracts `PaymentPayload`.
- Attempts `INSERT INTO pagos`.
- If SQLite throws a constraint violation (unique error):
  - Catches the error and returns `{ ok: true, status: "duplicate", message: "Payment already recorded" }` with **HTTP 200**.
- Optional: If `env.FORWARD_WEBHOOK_URL` is configured, asynchronously dispatches a `fetch(FORWARD_WEBHOOK_URL)` with the parsed payment (Push pattern).

#### 2. Verification (`POST /api/pagos/verificar`)
Used by your checkout, POS, or web application to reconcile a payment:
- **Request Body:**
  ```json
  {
    "referencia": "334023",
    "montoEsperado": 150.50,
    "fecha": "11-11-26" 
  }
  ```
- **Matching Logic:**
  - Query using `LIKE '%' || ?` on the reference column to match whatever number of digits the user provided (e.g., last 4, 6, or full 12).
  - Verifies `used = 0`.
  - Verifies exact amount (`ABS(amount_bs - montoEsperado) < 0.01`).
  - Sets `used = 1` and `used_at = CURRENT_TIMESTAMP` atomically in a transaction or update.
  - Returns `{ verificado: true, pago }`.

---

## 5. Deployment & Setup Steps

1. **Initialize Worker Project:**
   ```bash
   npm create cloudflare@latest pagomovil-worker -- --framework=hono --lang=ts
   cd pagomovil-worker
   ```

2. **Create D1 Database:**
   ```bash
   npx wrangler d1 create pagomovil-db
   ```
   Add the generated `database_id` and binding `DB` to `wrangler.toml`.

3. **Apply Schema:**
   ```bash
   # Local testing:
   npx wrangler d1 execute pagomovil-db --local --file=./schema.sql

   # Production:
   npx wrangler d1 execute pagomovil-db --remote --file=./schema.sql
   ```

4. **Configure Secrets:**
   ```bash
   npx wrangler secret put WEBHOOK_SECRET
   # (Optional) For push forwarding:
   npx wrangler secret put FORWARD_WEBHOOK_URL
   ```

5. **Deploy:**
   ```bash
   npx wrangler deploy
   ```
   This generates your permanent HTTPS URL (e.g., `https://pagomovil-worker.<subdomain>.workers.dev`).

6. **Link to Android App:**
   - In Android app -> Settings (Gear icon):
     - Webhook URL: `https://pagomovil-worker.<subdomain>.workers.dev/webhooks/pagomovil`
     - Webhook Secret: `<WEBHOOK_SECRET>`
