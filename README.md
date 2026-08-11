# CC Settle

Android app that reads bank SMS alerts, aggregates credit-card spend into a running "pending to settle" balance, and lets you self-transfer that amount to the account your card bill is paid from.

## Why

No bank API is available to individual developers in India. SMS alerts are the only real-time, individually-accessible data source. The app reads them on every open, parses transactions by card last-4, and keeps a running ledger — no manual entry.

## How it works

1. Open app → reads last 7 days of SMS inbox → parses bank alerts for your tracked cards
2. Home screen shows total pending (Σ debits − Σ refunds − Σ self-transfers)
3. Transfer manually via any UPI app → bank sends credit SMS → app picks it up next open → pending resets
4. All data synced to backend every 15 min via WorkManager

## Stack

| Layer | Tech |
|---|---|
| Android | Kotlin + Jetpack Compose + Room + WorkManager + DataStore |
| Auth | Firebase Auth (Google sign-in) |
| Backend | Node.js + TypeScript + Hono |
| ORM | Prisma |
| Database | PostgreSQL (Neon) |
| Push | FCM via Firebase Admin SDK |
| Deploy | Render (backend) |

## Project structure

```
android/   — Kotlin + Compose Android app
backend/   — Node.js + TypeScript API server
design_handoff_cc_settle/   — UI specs and screen mocks
docs/      — Architecture docs and implementation plans
```

## Backend endpoints

| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET | `/health` | None | Health check |
| POST | `/users/register` | Firebase token | Register user + FCM token |
| POST | `/sync` | Firebase token | Delta sync transactions + settle events |
| GET | `/cards` | Firebase token | List tracked cards |
| POST | `/cards` | Firebase token | Add a tracked card |
| DELETE | `/cards/:id` | Firebase token | Remove a tracked card |
| DELETE | `/transactions/:id` | Firebase token | Delete a transaction |
| POST | `/fcm/token` | Firebase token | Update FCM token |
| POST | `/fcm/notify` | Firebase token | Send digest push notification |
| POST | `/dev/parse-sms` | None (dev only) | Test SMS parser |

## Running locally

### Backend

```bash
cd backend
npm install
cp .env.example .env   # fill in DATABASE_URL, FIREBASE_SERVICE_ACCOUNT
npx prisma migrate dev --name init
npm run dev            # starts on localhost:3000
```

### Android

1. Install [Android Studio](https://developer.android.com/studio)
2. Add `google-services.json` from Firebase Console → `android/app/`
3. Open `android/` in Android Studio → Run

For local dev, emulator hits `10.0.2.2:3000`. For real device, update `API_BASE_URL` in `app/build.gradle.kts` to your machine's local IP.

## Key design decisions

- **SMS read on app open, not real-time** — avoids OEM battery killers (Vivo/MIUI) that kill background receivers. `SmsInboxReader` reads last 7 days on every `onResume`.
- **Filter by card last-4 at ingestion** — only SMS containing a tracked card number are stored. Avoids mutual fund, OTP, promo messages.
- **Local-first** — Room is source of truth. Backend is sync target + backup. App works fully offline.
- **No AI in critical path** — SMS classification is pure regex + keyword matching. Fast, deterministic, no API cost.
- **Paise everywhere** — all amounts stored as `Long` (paise). Never `Float`. Indian grouping for display (₹1,04,300).

## Docs

- [`design_handoff_cc_settle/cc-settle-app-design.md`](design_handoff_cc_settle/cc-settle-app-design.md) — original architecture spec
- [`design_handoff_cc_settle/README.md`](design_handoff_cc_settle/README.md) — UI design handoff
- [`docs/superpowers/specs/2026-08-02-cc-settle-fullstack-design.md`](docs/superpowers/specs/2026-08-02-cc-settle-fullstack-design.md) — full-stack design spec
