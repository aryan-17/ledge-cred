# CC Settle — Full-Stack Design

**Date:** 2026-08-02
**Status:** Approved
**Scope:** Android app + backend service for multi-user CC Settle

---

## 1. Problem

The original spec described a personal sideloaded APK. This design extends it to a multi-user product:
- Multiple friends using the same app
- Data synced across devices (e.g. new phone restore)
- API keys (Gemini) never shipped in the APK
- Push notifications for the 22:00 settle digest

## 2. Non-Goals

- iOS support — `READ_SMS` does not exist on iOS. Android only.
- Email digest — replaced by FCM push notifications.
- Claude/Anthropic API — using Gemini instead (user-supplied key).
- Web frontend — the Android app is the only client.
- Automatic money movement — UPI intent handoff only, no PIN automation.

## 3. Stack

| Layer | Tech |
|---|---|
| Android | Kotlin + Jetpack Compose + Room + WorkManager |
| Auth | Firebase Auth (Google sign-in) |
| Backend | Node.js + TypeScript + Hono |
| ORM | Prisma |
| Database | PostgreSQL (Railway) |
| AI classification | Gemini 2.0 Flash |
| Push notifications | FCM via Firebase Admin SDK |
| Deployment | Railway (API service + PostgreSQL service) |

## 4. Architecture

```
Android App (Room + Compose + WorkManager)
    │
    ├── Firebase Auth ──► Google Sign-In
    │       └── Firebase ID token attached to every API call
    │
    └── Hono API (Railway)
            ├── Middleware: validates Firebase ID token → extracts uid
            ├── /users/register  — create user on first login
            ├── /sync            — delta sync transactions + settle_events
            ├── /classify        — Gemini proxy for UNPARSED SMS
            ├── /fcm/token       — register/update FCM token
            └── /fcm/notify      — send digest push notification
                    │
                    ├── PostgreSQL (Railway)
                    ├── Gemini 2.0 Flash
                    └── FCM (Firebase Admin SDK)
```

**Local-first:** Room is the source of truth on device. Backend is the sync target. App is fully functional offline; syncs when connected.

**Delta sync:** client sends `lastSyncedAt` → server returns rows modified after that timestamp → client sends its new/modified rows → last-write-wins on `updated_at` for conflicts.

**Digest flow:** WorkManager fires at 22:00 user-local time → calls `/fcm/notify` with current `pendingPaise` → backend sends FCM push → user taps → settle screen opens.

**Classify flow:** WorkManager nightly job sends `UNPARSED` SMS texts to `/classify` → backend proxies to Gemini Flash → returns `{ suggestedType, confidence }` per message → device writes suggestions to Room → never auto-applied, shown in review queue.

## 5. Data Model

### PostgreSQL

```sql
users (
  uid         TEXT PRIMARY KEY,   -- Firebase UID
  fcm_token   TEXT,
  created_at  TIMESTAMPTZ DEFAULT now()
)

transactions (
  id                       UUID PRIMARY KEY,   -- UUID generated on device
  user_id                  TEXT NOT NULL REFERENCES users(uid),
  amount_paise             BIGINT NOT NULL,
  type                     TEXT NOT NULL,      -- DEBIT | REFUND | SELF_TRANSFER | UNPARSED | OTP | DECLINED | STATEMENT
  card_last4               TEXT,
  bank                     TEXT NOT NULL,
  txn_time                 TIMESTAMPTZ NOT NULL,
  dedupe_hash              TEXT NOT NULL,
  matched_settle_event_id  UUID,
  suggested_type           TEXT,
  suggested_confidence     FLOAT,
  reviewed                 BOOLEAN NOT NULL DEFAULT false,
  updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at               TIMESTAMPTZ
)

settle_events (
  id                      UUID PRIMARY KEY,
  user_id                 TEXT NOT NULL REFERENCES users(uid),
  parent_ref              TEXT NOT NULL,      -- e.g. CCS20260801
  suffix                  TEXT,               -- A, B… for partial/split
  status                  TEXT NOT NULL,      -- AWAITING | CLEARED | PARTIAL | MANUAL_MATCH | EXPIRED
  requested_amount_paise  BIGINT NOT NULL,
  pending_snapshot_paise  BIGINT NOT NULL,
  created_at              TIMESTAMPTZ NOT NULL,
  cleared_at              TIMESTAMPTZ,
  cleared_amount_paise    BIGINT,
  updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at              TIMESTAMPTZ
)
```

**Indexes:** `(user_id, updated_at)` on both `transactions` and `settle_events` — delta sync query hits this index.

**`raw_sms` is not synced.** Raw bank SMS text (amounts, merchant names, card last-4) stays in Room only. The server never stores it. The classified fields are sufficient for sync and history.

**Soft deletes:** `deleted_at` propagates deletions across devices via the sync protocol. Hard deletes are never used.

### Room (Android — unchanged from original spec)

Room schema is unchanged from the original architecture spec (`cc-settle-app-design.md`). UUIDs align with PostgreSQL PKs. `raw_sms` and `sms_time` columns exist in Room only.

## 6. API

All endpoints require `Authorization: Bearer <Firebase ID token>`. Middleware rejects with `401` if token is missing or invalid.

### `POST /users/register`
Called once on first login or FCM token rotation.
```json
// Request
{ "fcmToken": "string" }

// Response 200
{ "uid": "string" }
```

### `POST /sync`
Delta sync — push local changes, pull remote changes.
```json
// Request
{
  "lastSyncedAt": "2026-08-01T10:00:00Z",  // ISO 8601, null on first sync
  "transactions": [ /* Transaction[] — new/modified since last sync */ ],
  "settleEvents": [ /* SettleEvent[] — new/modified since last sync */ ]
}

// Response 200
{
  "syncedAt": "2026-08-02T08:00:00Z",
  "transactions": [ /* rows where updated_at > lastSyncedAt for this uid */ ],
  "settleEvents": [ /* rows where updated_at > lastSyncedAt for this uid */ ]
}
```
Server upserts incoming rows. `updated_at` last-write-wins for conflicts. Soft-deleted rows (`deleted_at` set) are included in the response so the device can remove them from Room.

### `POST /classify`
Proxies UNPARSED SMS texts to Gemini. Raw SMS text is sent here but not persisted on the server.
```json
// Request
{
  "messages": [
    { "id": "uuid", "text": "INR 2,500 debited from A/c XX1234..." }
  ]
}

// Response 200
{
  "results": [
    { "id": "uuid", "suggestedType": "DEBIT", "confidence": 0.94 }
  ]
}
```

### `PUT /fcm/token`
Update FCM token when Firebase rotates it.
```json
// Request
{ "token": "string" }

// Response 200
{ "ok": true }
```

### `POST /fcm/notify`
Called by WorkManager at 22:00 to deliver the digest push.
```json
// Request
{ "title": "string", "body": "Settle ₹42,380 now" }

// Response 200
{ "ok": true }
```

Error responses follow `{ "error": "string" }` with appropriate HTTP status codes (400, 401, 500).

## 7. Android Integration

- **Firebase Auth SDK** — handles Google sign-in, token refresh. `FirebaseUser.getIdToken(false)` provides a fresh token for each request.
- **Retrofit + OkHttp** — `AuthInterceptor` attaches `Authorization: Bearer <token>` to every request.
- **Sync trigger** — on app foreground + on WorkManager periodic job (every 15 min). First sync sends `lastSyncedAt: null` to pull all server rows.
- **Classify trigger** — nightly WorkManager job, runs after the 22:00 digest notification.
- **FCM token** — `FirebaseMessaging.getInstance().token` registered via `/users/register` on login and `/fcm/token` on rotation (`onNewToken` callback).

## 8. Deployment

**Railway project:** two services.
1. **API service** — Node.js, built from `/backend` directory, `npm run start`.
2. **PostgreSQL service** — managed by Railway, `DATABASE_URL` injected automatically.

**Environment variables (API service):**
```
DATABASE_URL              # injected by Railway
FIREBASE_SERVICE_ACCOUNT  # JSON string of Firebase Admin service account
GEMINI_API_KEY            # Gemini API key
```

**Migrations:** `prisma migrate deploy` runs as part of the deploy command before the server starts.

## 9. Play Store Note

`READ_SMS` is restricted on Play Store. A permissions declaration must be submitted justifying the use case (personal finance, bank SMS alerts). Approval is not guaranteed. Keep the sideload distribution path (direct APK) as a fallback for early users and friends until Play Store approval is confirmed.

## 10. Build Phases

The original build phases from the architecture spec remain. The backend is added as a parallel workstream:

| Phase | Android | Backend |
|---|---|---|
| 1 | SMS receiver + parser + Room + Home screen | PostgreSQL schema + Prisma migrations + `/users/register` |
| 2 | Settle screen + waiting state + credit SMS matching | `/sync` endpoint + Retrofit integration |
| 3 | Review queue + Settings + first-run onboarding | `/classify` (Gemini proxy) |
| 4 | Partial settle + daily-cap pre-splitting | `/fcm/token` + `/fcm/notify` + WorkManager digest trigger |
| 5 | Play Store submission | Railway production hardening |
