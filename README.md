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
| Database | PostgreSQL (Supabase prod / Neon QA / local dev) |
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
npx prisma db push
npm run dev            # dev: local Postgres on localhost:3000
npm run dev:qa         # qa: Neon
```

**Environment files:**

| File | DB | Used for |
|---|---|---|
| `.env` | local Postgres | development |
| `.env.qa` | Neon | QA / staging |
| `.env.production` | Supabase | production (local run only) |

**Schema push per environment:**
```bash
npm run db:push        # → local
npm run db:push:qa     # → Neon
npm run db:push:prod   # → Supabase
```

### Android

1. Install [Android Studio](https://developer.android.com/studio)
2. Add `google-services.json` from Firebase Console → `android/app/`
3. Open `android/` in Android Studio → Run

For local dev, emulator hits `10.0.2.2:3000`. For real device, update `API_BASE_URL` in `app/build.gradle.kts` to your machine's local IP.

## Key design decisions

- **SMS read on app open, not real-time** — avoids OEM battery killers (Vivo/MIUI) that kill background receivers. `SmsInboxReader` reads since last open (`lastInboxReadAt`), falling back to 7 days on first run.
- **Incremental SMS scan + in-memory dedupe** — reads only new SMS since last open. All existing dedupe hashes loaded into a `HashSet` once per scan; O(1) lookup per SMS instead of a DB query per SMS.
- **Batch upserts on sync** — transactions sent to backend in a single bulk upsert call, not one-by-one.
- **Filter by card last-4 at ingestion** — only SMS containing a tracked card number are stored. Avoids mutual fund, OTP, promo, bill payment messages.
- **Bill payment SMS discarded** — SMS matching bill payment / bill received patterns are classified as `STATEMENT` and dropped before storage.
- **Local-first** — Room is source of truth. Backend is sync target + backup. App works fully offline.
- **No AI in critical path** — SMS classification is pure regex + keyword matching. Fast, deterministic, no API cost.
- **Paise everywhere** — all amounts stored as `Long` (paise). Never `Float`. Indian grouping for display (₹1,04,300).
- **Health endpoint wakes Neon** — `GET /health` runs a `SELECT 1` to keep the Neon serverless DB from cold-starting on the first real request.
- **Rate limiting + body size cap** — backend enforces per-IP rate limits and a request body size limit to prevent abuse.

## Load Testing

Tested with [k6](https://k6.io) across 13 runs covering baseline, post-fix regression, delta sync, spike, soak, and rate limiter validation.

**Bottlenecks found and fixed:**

| Fix | Impact |
|---|---|
| Firebase token cache (in-process, TTL = expiry) | Eliminates network call to Firebase after first auth |
| Bulk SQL upsert (`INSERT ... ON CONFLICT DO UPDATE`) | 501 DB queries per sync → 1 |
| UID existence cache in sync route | Skips `user.upsert` after first call per session |
| Jittered backoff in Android `SyncWorker` (0–30s) | Prevents thundering herd on backend restart |
| IP rate limit on `/users/register` | Closes abuse vector |
| Auth cache size cap (10k entries) | Prevents unbounded memory growth |

**Final results (local Postgres, single Node.js instance):**

| Scenario | VUs | RPS | p95 | Errors |
|---|---|---|---|---|
| Empty sync sustained | 1,000 | 955 | 9ms | 0% |
| Heavy sync (100 txns) | 10 | — | 4,200ms | 0% |
| Delta sync (realistic) | 10 | — | 7.55ms | 0% |
| Spike 0→1k VUs in 5s | 1,000 | 847 | 5.72ms | 0% |
| Soak 30 min | 100 | 99.5 | 7.8ms | 0% |
| Rate limiter | 1 | — | — | 429 at 61st req ✓ |

Full report: [`docs/load-test-report.md`](docs/load-test-report.md)

## Docs

- [`design_handoff_cc_settle/cc-settle-app-design.md`](design_handoff_cc_settle/cc-settle-app-design.md) — original architecture spec
- [`design_handoff_cc_settle/README.md`](design_handoff_cc_settle/README.md) — UI design handoff
- [`docs/superpowers/specs/2026-08-02-cc-settle-fullstack-design.md`](docs/superpowers/specs/2026-08-02-cc-settle-fullstack-design.md) — full-stack design spec
- [`docs/load-test-report.md`](docs/load-test-report.md) — full load test report (13 runs)
