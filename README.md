# PagoMóvil Listener (Android)

Native Android background service and Outbox pipeline that intercepts incoming Pago Móvil push notifications (Banco de Venezuela, Banco Fondo Común, etc.), parses payment data, persists it locally with Room (SQLite), and dispatches it reliably via HTTP POST to your backend webhook using the **Outbox Pattern** and **WorkManager** retries.

---

## 🏛️ Architecture Overview

Intercepting mobile banking notifications on Android requires resilience against network loss, device reboots, and aggressive Android OS background killers (Doze Mode). This application implements a **Local-First Outbox Pattern**:

```
[ Incoming Bank Notification ]
               │
               ▼
[ NotificationListenerService ] ──(onNotificationPosted)
               │
               ▼
   [ CompositePaymentParser ] ──► (BDV / BFC / Custom Parsers)
               │
       (Matches Pago Móvil)
               │
               ▼
      [ Room SQLite DB ] ──► Status: PENDING (Persisted immediately)
               │
               ▼
     [ Immediate HTTP POST ]
       ┌───────┴───────┐
       ▼               ▼
   (Success)       (Offline / Timeout / 5xx)
       │               │
  Status: SYNCED       │
                 Status: FAILED
                       │
                       ▼
            [ WorkManager Scheduler ]
            (Exponential backoff + Network constraint)
```

### Key Engineering Features
1. **Guaranteed Local Persistence:** No notification is ever lost if the device loses Wi-Fi/LTE. Every detected payment is saved to Room SQLite before any network request is attempted.
2. **Resilient Background Sync (WorkManager):** If the POST request fails or the phone is offline, `WorkManager` retries automatically when connectivity is restored, respecting Android Doze mode constraints.
3. **Pluggable Bank Parsers:** Built using the Open/Closed Principle (`PaymentParser` interface). Adding support for Mercantil, Banesco, Provincial, or Bancamiga only requires implementing a new parser class.
4. **Phone Masking Normalization:** Extracts both the full string and normalized components (`phonePrefix` and `phoneLastDigits`) to support banks that mask digits (e.g. BFC `0414***9152`).
5. **Interactive UI & Simulator:** Includes status monitoring, configuration dialog for the Webhook URL and Secret Token, and test simulation buttons to trigger synthetic notifications without spending real money.

---

## 📦 Supported Banks & Formats

### 1. Banco de Venezuela (BDV)
* **Notification Title:** `PagomovilBDV recibido`
* **Sample Text:** `Recibiste un PagomovilBDV por Bs.1,00 del 0412-9897838 Ref: 334023334023 en fecha 11-11-26 hora: 22:29.`
* **Extracted Fields:**
  * Amount: `1.00`
  * Phone: `0412-9897838` (Prefix: `0412`, Last Digits: `7838`)
  * Reference: `334023334023`
  * Date: `11-11-26`
  * Time: `22:29`

### 2. Banco Fondo Común (BFC)
* **Notification Title:** `Pago recibido`
* **Sample Text:** `Ha recibido un Pago por Bs. 35,00 del Celular 0414***9152 a traves del servicio TUBFC 11/11/2026`
* **Extracted Fields:**
  * Amount: `35.00`
  * Phone: `0414***9152` (Prefix: `0414`, Last Digits: `9152`)
  * Reference: `null` (BFC push notifications omit the reference number)
  * Date: `11/11/2026`

### 3. Bancamiga
* **Notification Title:** `Bancamiga Suite`
* **Sample Text:** `Has recibido un Pago Móvil desde el 04129897838 por Bs. 100,00.`
* **Extracted Fields:**
  * Amount: `100.00`
  * Phone: `04129897838` (Prefix: `0412`, Last Digits: `7838`)
  * Reference: `null` (Bancamiga push notifications omit the reference number)
  * Date: Real-time system capture date (`dd/MM/yyyy`)
  * Time: Real-time system capture time (`HH:mm`)

---

## 🚀 Webhook Payload (HTTP POST)

When a payment is captured, the application dispatches an HTTP POST request to your configured endpoint:

**Headers:**
```http
POST /webhooks/pagomovil HTTP/1.1
Host: api.yourcompany.com
Content-Type: application/json; charset=utf-8
User-Agent: PagoMovil-Listener/1.0
X-Webhook-Secret: your_configured_secret_token
```

**JSON Body:**
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

---

## ⚙️ Device Setup & Permissions

To allow this app to operate autonomously on a dedicated Android device:

1. **Grant Notification Access:**
   * Open the app and tap **"Conceder Acceso"** (or go to `Settings > Apps > Special app access > Notification access`).
   * Enable **PagoMóvil Listener**.
2. **Disable Battery Optimization:**
   * In the app, tap **"Desactivar Optimización"** to grant `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
   * Set battery usage to **"Unrestricted"** in app info.
3. **Configure Webhook:**
   * Tap the **Settings** icon in the toolbar.
   * Enter your backend URL (e.g. `https://api.tuempresa.com/webhooks/pagomovil`) and optional `X-Webhook-Secret`.

---

## 🛠️ Building & Running

1. Clone this repository:
   ```bash
   git clone https://github.com/cegh2001/pagomovil-listener.git
   ```
2. Open the project in **Android Studio** (Hedgehog / Iguana / Jellyfish or newer).
3. Let Gradle sync and download dependencies.
4. Run on a physical Android device or emulator (Android 8.0+ / API 26+).

---

## 🧪 Unit Testing

Unit tests covering real-world notification vectors from BDV and BFC are located in:
`app/src/test/java/com/cegh/pagomovillistener/parser/CompositePaymentParserTest.kt`

Run tests via Gradle:
```bash
./gradlew test
```

---

## 📄 License
MIT License
