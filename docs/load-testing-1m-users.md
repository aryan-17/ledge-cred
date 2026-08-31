# Load Testing Plan — 1M Users

## Stack

| Layer | Technology |
|---|---|
| Backend | Hono + Node.js on Render (free tier) |
| Database | Supabase Postgres (free tier, PgBouncer pooler, port 6543) |
| Auth | Firebase Auth (JWT verification per request) |
| Frontend | Android (Room local DB, sync via REST) |

---

## Realistic Load Model

1M users does NOT mean 1M concurrent. Model real traffic:

| Metric | Value | Reasoning |
|---|---|---|
| DAU | 1,000,000 | Target |
| Peak concurrent | 10,000–50,000 | 1–5% DAU active at same time |
| Requests/user/session | ~5–10 | sync, cards, transactions |
| Peak RPS | ~2,000–5,000 | concurrent × avg req/session / session duration |
| Spike multiplier | 10× | payday, viral moment |

---

## Current Bottlenecks (Identified)

### 1. Firebase Auth Token Verification — HIGH IMPACT
Every request calls Firebase to verify the JWT. At 2,000 RPS that is 2,000 Firebase roundtrips/sec.

**Current:** `authMiddleware` verifies token on every request — no caching.

**Impact:** ~50–100ms added latency per request. At scale this becomes the primary bottleneck before DB or CPU.

**Fix:** Cache decoded Firebase tokens in-process for their TTL (1 hour).

```typescript
// lib/authCache.ts
const cache = new Map<string, { uid: string; exp: number }>()

export function getCachedUid(token: string): string | null {
  const hit = cache.get(token)
  if (!hit || Date.now() / 1000 > hit.exp) { cache.delete(token); return null }
  return hit.uid
}

export function setCachedUid(token: string, uid: string, exp: number) {
  cache.set(token, { uid, exp })
  // Evict stale entries every 100 insertions to prevent unbounded growth
  if (cache.size % 100 === 0) {
    const now = Date.now() / 1000
    for (const [k, v] of cache) if (v.exp < now) cache.delete(k)
  }
}
```

Apply in `middleware/auth.ts` before calling Firebase.

---

### 2. Supabase Free Tier Connection Limits — HIGH IMPACT
Supabase free tier PgBouncer allows limited concurrent connections.

**Current:** Prisma default pool is 10 connections. PgBouncer transaction mode reuses them.

**Impact:** Connection pool exhaustion under load causes queuing and timeouts.

**Fix options:**
- Use `?pgbouncer=true&connection_limit=1` on DATABASE_URL (already done) — Prisma uses 1 connection per instance, letting PgBouncer manage the pool.
- Horizontal scale on Render (multiple instances) each with 1 connection = more parallelism without exhausting pooler.

**Limit:** Supabase free tier caps at ~60 concurrent connections via pooler.

---

### 3. Render Free Tier — Spins Down — MEDIUM IMPACT
Render free instances spin down after inactivity. Already mitigated with `/health` ping cron.

**Impact at scale:** Single Render instance will max out at ~100–200 RPS due to Node.js single-thread limits under heavy I/O.

**Fix:** Upgrade to Render paid (Starter $7/mo) for persistent instances, then scale horizontally to 2–4 instances.

---

### 4. Sync Endpoint Write Amplification — MEDIUM IMPACT
`POST /sync` does `createMany` + N individual `update` calls in parallel.

```typescript
await prisma.transaction.createMany({ data: txData, skipDuplicates: true })
await Promise.all(body.transactions.map(tx =>
  prisma.transaction.update({ where: { id: tx.id }, data: ... }).catch(() => {})
))
```

At 500 transactions per sync, this is 501 DB queries per request.

**Fix:** Replace with native Postgres upsert via `$executeRaw`:
```typescript
// Single query for all rows using ON CONFLICT DO UPDATE
await prisma.$executeRaw`
  INSERT INTO "Transaction" (...) VALUES ${...}
  ON CONFLICT (id) DO UPDATE SET ...
`
```

---

### 5. No Rate Limiting on Auth Endpoint — LOW IMPACT
`POST /users/register` has no rate limit. At scale, bots can hammer it.

**Current:** Rate limiting exists on `/sync` (60/min) and general (120/min) per UID. Register has none.

**Fix:** Add rate limit to `/users/register` by IP.

---

### 6. Android Thundering Herd on Reconnect — MEDIUM IMPACT
If backend goes down briefly (deploy, sleep), all Android clients retry simultaneously on reconnect.

**Impact:** Spike of 10,000+ sync requests in seconds after backend recovers.

**Fix:** Add jittered exponential backoff in `SyncWorker`:
```kotlin
val jitter = Random.nextLong(0, 30_000) // 0–30s random delay
delay(jitter)
```

---

## Load Test Setup

### Tool: k6

```bash
npm install -g k6
```

### Test Script

```javascript
// load-test.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const errorRate = new Rate('errors');
const syncDuration = new Trend('sync_duration');

export const options = {
  stages: [
    { duration: '2m', target: 100 },    // warm up
    { duration: '3m', target: 1000 },   // ramp to 1k
    { duration: '5m', target: 5000 },   // ramp to 5k (peak DAU ~50k, 10% active)
    { duration: '5m', target: 10000 },  // stress — 10k concurrent
    { duration: '2m', target: 0 },      // ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    errors: ['rate<0.01'],              // <1% error rate
    sync_duration: ['p(95)<800'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'https://your-render-url.onrender.com';
const TOKEN = __ENV.FIREBASE_TOKEN;   // pre-generated Firebase token for test user

export default function () {
  const headers = {
    'Authorization': `Bearer ${TOKEN}`,
    'Content-Type': 'application/json',
  };

  // 1. Health check
  const health = http.get(`${BASE_URL}/health`);
  check(health, { 'health ok': (r) => r.status === 200 });

  // 2. Sync (main load — most frequent operation)
  const syncPayload = JSON.stringify({
    lastSyncedAt: null,
    transactions: [],
    settleEvents: []
  });
  const sync = http.post(`${BASE_URL}/sync`, syncPayload, { headers });
  check(sync, { 'sync 200': (r) => r.status === 200 });
  errorRate.add(sync.status !== 200);
  syncDuration.add(sync.timings.duration);

  // 3. Cards read
  const cards = http.get(`${BASE_URL}/cards`, { headers });
  check(cards, { 'cards 200': (r) => r.status === 200 });

  sleep(Math.random() * 2 + 1); // 1–3s think time between requests
}
```

### Run

```bash
# Ramp test
BASE_URL=https://your-app.onrender.com \
FIREBASE_TOKEN=your_test_token \
k6 run load-test.js

# Spike test (simulate payday)
k6 run --vus 10000 --duration 30s load-test.js
```

---

## Test Phases

### Phase 1 — Baseline (Run Now, No Changes)
**Goal:** Find where it breaks today.

```bash
k6 run --vus 100 --duration 5m load-test.js
```

Record: p95 latency, error rate, Render CPU/memory at 100 users.

### Phase 2 — Auth Cache Fix
Apply Firebase token cache. Re-run at 100 users.

**Expected improvement:** p95 latency drops 40–60ms per request.

### Phase 3 — Sync Upsert Fix
Replace double-write with single upsert. Re-run at 1,000 users.

**Expected improvement:** DB query count drops from 501 to 1 per sync.

### Phase 4 — Scale to 5,000
```bash
k6 run --vus 5000 --duration 5m load-test.js
```

At this point Render free tier will likely saturate. Upgrade to Render Starter + 2 instances.

### Phase 5 — Scale to 10,000
```bash
k6 run --vus 10000 --duration 5m load-test.js
```

Monitor Supabase connection pooler. If pool exhausts, move to Supabase Pro (500 connections).

### Phase 6 — Spike Test
```bash
k6 run --vus 50000 --duration 30s load-test.js
```

This simulates a viral spike. Expect degraded performance — goal is no data loss and graceful 429s.

---

## Observability During Tests

| What | Where |
|---|---|
| Response times, RPS, errors | k6 terminal output |
| CPU / Memory / Latency | Render dashboard → Metrics |
| Slow queries | Supabase dashboard → Database → Query insights |
| Error logs | Render dashboard → Logs (Pino JSON) |
| DB connections | Supabase dashboard → Database → Connections |

### Key Metrics to Watch

- **p95 latency** target: < 500ms
- **Error rate** target: < 1%
- **DB connections** limit: ~60 (Supabase free)
- **Render CPU** alert: > 80% sustained

---

## Fix Priority Order

| Priority | Fix | Effort | Impact |
|---|---|---|---|
| 1 | Firebase token cache in auth middleware | 1 hour | High — cuts latency 40–60ms |
| 2 | Replace sync double-write with single upsert | 2 hours | High — 500× fewer DB queries on sync |
| 3 | Jittered backoff in SyncWorker (Android) | 30 min | Medium — prevents reconnect spikes |
| 4 | Upgrade Render to Starter + 2 instances | 5 min (config) | High — persistent + horizontal scale |
| 5 | Rate limit on /users/register by IP | 30 min | Low — security hygiene |
| 6 | Upgrade Supabase to Pro if connections exhaust | 5 min (config) | High — only if free tier pools fill |

---

## Success Criteria

| Scenario | Target |
|---|---|
| 10,000 concurrent users | p95 < 500ms, error rate < 1% |
| 50,000 concurrent spike (30s) | No data loss, graceful degradation |
| 1M DAU sustained | Backend stable 24/7, no OOM crashes |
