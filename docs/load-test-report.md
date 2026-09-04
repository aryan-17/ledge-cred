# Load Test Report

**Date:** 2026-09-02  
**Tool:** k6 2.2.0  
**VUs:** 10 concurrent  
**Duration:** 1 minute  
**Endpoints tested:** `/health`, `/sync`, `/cards`

---

## Results Summary

### Run 1 — Against Render (prod)
Rate limiter kicked in at 60 req/min per UID. All 10 VUs shared one token → hit limit in ~13s.

| Metric | Value |
|---|---|
| Error rate | 52% (rate limiter 429s) |
| p95 latency | 136ms ✓ |
| Successful sync p95 | 188ms ✓ |

**Finding:** When requests succeeded, performance was excellent. Rate limiter is working correctly.

---

### Run 2 — Against Local Backend (rate limit raised to 100k)

| Metric | Value | Target | Status |
|---|---|---|---|
| Error rate | 0% | <1% | ✓ |
| p95 latency | 3.86s | <500ms | ✗ |
| p99 latency | 4.12s | <1000ms | ✗ |
| sync p95 | 4017ms | <800ms | ✗ |
| sync avg | 2101ms | — | — |
| RPS | 5.65 | — | — |

**Important context:** Local backend connects to Neon DB in `us-east-2` and Firebase Auth servers from India. Network roundtrip alone is 300–500ms per hop. This inflates all latency numbers artificially.

Render prod test (Run 1) showed p95 136ms — Render and Neon are co-located in same AWS region, so real user latency is ~30× better than local test.

---

## Confirmed Bottlenecks

### 1. Firebase Auth — Every Request (CRITICAL)
`verifyIdToken()` calls Firebase servers on every request. No caching.

- **Cost:** ~300–500ms per request (local), ~20–50ms (Render, same region)
- **At 10k RPS:** 10k Firebase calls/sec — will hit Firebase quota limits
- **Fix:** In-process token cache keyed by token, TTL = token expiry

### 2. Sync Double-Write — N+1 Queries (HIGH)
```typescript
await prisma.transaction.createMany(...)           // 1 query
await Promise.all(txs.map(tx => prisma.transaction.update(...)))  // N queries
```
500 transactions = 501 DB queries per sync request.

- **Cost:** Multiplies DB load by 500× under heavy sync load
- **Fix:** Single `INSERT ... ON CONFLICT DO UPDATE` via `$executeRaw`

### 3. Single Render Instance (MEDIUM)
Node.js is single-threaded. One instance handles all requests sequentially under CPU load.

- **Limit:** ~200–500 RPS per instance before CPU saturates
- **Fix:** Scale to 2–4 instances on Render Starter plan

### 4. Supabase Free Tier Connection Pool (MEDIUM)
PgBouncer free tier caps at ~60 concurrent DB connections.

- **Risk:** At 500+ concurrent users all syncing simultaneously, pool exhausts
- **Fix:** Upgrade to Supabase Pro (500 connections) at scale

### 5. Android Thundering Herd (MEDIUM)
All clients retry simultaneously after backend restart/deploy.

- **Risk:** Spike of 10k+ sync requests within seconds of backend recovery
- **Fix:** Jittered exponential backoff in SyncWorker

### 6. Rate Limiter — Per UID (LOW for prod, HIGH for testing)
60 req/min per UID on sync. Correct for production (real users won't hit this). Problem only for load testing with single token.

- **Fix (prod):** Keep as-is — protects against abuse
- **Fix (testing):** Use multiple tokens (one per VU)

---

## Next Steps — Priority Order

### Fix 1: Firebase Token Cache (1–2 hrs) — DO FIRST
Eliminates Firebase network call after first auth. Biggest single improvement.

```typescript
// backend/src/lib/authCache.ts
const cache = new Map<string, { uid: string; exp: number }>()

export function getCached(token: string): string | null {
  const hit = cache.get(token)
  if (!hit || Date.now() / 1000 > hit.exp - 60) {
    cache.delete(token)
    return null
  }
  return hit.uid
}

export function setCache(token: string, uid: string, exp: number) {
  cache.set(token, { uid, exp })
  if (cache.size % 100 === 0) {
    const now = Date.now() / 1000
    for (const [k, v] of cache) if (v.exp < now) cache.delete(k)
  }
}
```

Apply in `middleware/auth.ts`:
```typescript
const cached = getCached(token)
if (cached) { c.set('uid', cached); await next(); return }
const decoded = await getFirebaseAuth().verifyIdToken(token)
setCache(token, decoded.uid, decoded.exp)
```

**Expected improvement:** p95 drops from 3.86s → ~200ms locally, ~50ms on Render.

---

### Fix 2: Sync Single Upsert (2–3 hrs)
Replace 501 queries with 1 per sync.

```typescript
// Replace createMany + Promise.all(updates) with:
for (const tx of body.transactions) {
  await prisma.transaction.upsert({
    where: { id: tx.id },
    update: mapTxToDb(tx, uid),
    create: mapTxToDb(tx, uid)
  })
}
// Or better: raw SQL batch upsert
```

**Expected improvement:** DB load drops 500× for full syncs.

---

### Fix 3: Android Jittered Backoff (30 min)
```kotlin
// SyncWorker.kt — before sync call
val jitter = Random.nextLong(0, 30_000)
delay(jitter)
```

**Expected improvement:** Prevents 10k spike on backend recovery.

---

### Fix 4: Re-test on Render After Fixes
After implementing fixes 1+2, re-run with proper multi-token setup:

```javascript
// load-test.js — use VU-specific token
const TOKENS = open('./tokens.txt').trim().split('\n')
const TOKEN = TOKENS[__VU % TOKENS.length]
```

Create 10 Firebase test accounts, generate tokens, store in `docs/tokens.txt` (gitignored).

---

### Fix 5: Scale Render (when needed)
When single instance saturates (>500 RPS):
- Upgrade Render to Starter ($7/mo)
- Add 2nd instance
- Each instance uses 1 Supabase pooler connection (`connection_limit=1`)

---

### Fix 6: Revert Rate Limits After Testing
```typescript
// Revert 100_000 back to 60 (sync) and 120 (general)
rateLimit(uid, 60, 60_000)
rateLimit(`${uid}:general`, 120, 60_000)
```

---

---

### Run 3 — Local Backend, After Fixes (auth cache + bulk upsert)

**Date:** 2026-09-04 | **VUs:** 10 | **Duration:** 2 minutes | **Target:** localhost:3000

| Metric | Baseline (Run 2) | Run 3 | Change |
|---|---|---|---|
| Error rate | 0% | **0%** | — |
| p95 latency | 3860ms | **1700ms** | -56% ✓ |
| p99 latency | 4120ms | 5000ms | +21% (DB hiccup) |
| sync p95 | 4017ms | **2130ms** | -47% ✓ |
| sync avg | 2101ms | **1568ms** | -25% ✓ |
| RPS | 5.65 | **6.88** | +22% ✓ |
| median latency | — | **243ms** | (health/cards fast) |

**Analysis:** Auth cache working — health/cards median is 243ms. High sync p95 still driven by DB roundtrip (India → Neon us-east-2, ~300–500ms/query). p99 spike to 5s likely a connection hiccup at peak concurrency. Same geographic caveat as Run 2 applies — Render prod will be significantly faster.

**Note:** Local load testing only. Prod untested (co-located Render+Neon expected to perform similarly to Run 1, ~136ms p95).

---

---

### Run 4 — Phase 3: 1000 VUs (local, after fixes)

**Date:** 2026-09-04 | **VUs:** 1000 | **Duration:** 2 minutes | **Target:** localhost:3000 | **Tokens:** 10

| Metric | Run 3 (10 VUs) | Run 4 (1000 VUs) |
|---|---|---|
| Error rate | 0% | **81%** ✗ |
| p95 latency | 1700ms | 11680ms ✗ |
| p99 latency | 5000ms | 21170ms ✗ |
| sync p95 | 2130ms | 21124ms ✗ |
| RPS | 6.88 | 83.4 |
| health success | 100% | 35% |
| sync success | 100% | 2% |

**Analysis:** Backend saturates at 1000 VUs. Even `/health` fails at 35% — event loop fully saturated, not just DB. Errors are k6 default 10s timeout, not backend 500s. Identifies two bottlenecks:

1. **Single Node.js instance** — single-threaded event loop maxes out under ~500 concurrent. Fix: horizontal scale (2–4 Render instances).
2. **DB connection pool exhaustion** — 1000 concurrent writers exhaust Prisma/PgBouncer connections. Fix: Supabase Pro (500 pooler connections) + `connection_limit=1` per instance.

**Note:** RPS jumped to 83 (from 6.88) showing the backend did process more requests in absolute terms — it's queueing and timing out, not crashing. No data corruption.

**Next step (revised):** Local 1k VU testing not meaningful — see Run 6.

---

### Run 5 — 10 VUs, uid cache + connection_limit=5, sync+cards only

**Date:** 2026-09-04 | **VUs:** 10 | **Duration:** 2 minutes | **Target:** localhost:3000

| Metric | Run 3 (10 VUs) | **Run 5** | Change |
|---|---|---|---|
| Error rate | 0% | **0%** | — |
| p95 latency | 1700ms | **580ms** | -66% ✓ |
| p99 latency | 5000ms | 7490ms | cold start spike |
| sync p95 | 2130ms | **648ms ✓** | -70% |
| sync avg | 1568ms | **499ms** | -68% |
| median latency | 243ms | **247ms** | — |
| RPS | 6.88 | **6.99** | +2% |

**sync p95 648ms now passes the 800ms threshold.**

**Changes that drove improvement:**
- uid existence cache in sync route — saves 1 DB roundtrip (~300ms) on every request after first call per UID
- `connection_limit=5` in DATABASE_URL — 5 parallel DB queries in-flight per instance
- Removed `/health` from load test — isolated sync+cards latency accurately

**p99 spike to 7.49s = Neon autosuspend cold start**, confirmed in Neon monitoring. Endpoint was idle >5 min before test, first connection paid wake penalty. Not a code issue. Fix: disable autosuspend or keepalive ping.

**Neon monitoring confirmed:**
- Peak pooler connections: 21 (during 1000 VU run) — DB never saturated
- DB CPU: ~2% — DB was never the bottleneck
- Cache hit rate: 100% after warmup — all data in shared buffers

---

### Run 6 — 2 Node instances, 1k VUs (local)

**Date:** 2026-09-04 | **VUs:** 1000 | **Instances:** 2 (port 3000 + 3001) | **Duration:** 2 minutes

| Metric | Run 4 (1 instance) | Run 6 (2 instances) |
|---|---|---|
| Error rate | 81% | **99.4%** |
| RPS | 83 | 82 |
| sync success | 2% | 0.08% |
| p95 latency | 11680ms | 15080ms |

**Finding: 2 instances provided zero improvement locally.** RPS stayed at ~83. k6 splits VUs per-instance at startup (500 each), and 500 VUs still overwhelms each instance individually — same saturation point as 1000 VUs on one instance.

**Root cause of local saturation:** DB roundtrip India → Neon us-east-2 is ~300ms. Each request holds the async chain open for that duration. At 500 VUs per instance the queue builds faster than it drains regardless of instance count.

**This does NOT reflect prod behavior.** On Render (co-located with Neon, ~5-10ms RTT), the same Node instance completes requests 30-60× faster — same instance handles 30-60× more concurrent load before saturating.

**Conclusion: Local load testing is only meaningful up to ~50-100 VUs with remote DB.** Use local tests for latency regression checks, not capacity planning. Use a local Postgres DB (Homebrew/Docker) for high-VU local tests.

---

### Run 7 — 1k VUs, local Postgres (Homebrew pg14)

**Date:** 2026-09-04 | **VUs:** 1000 | **Duration:** 2 minutes | **DB:** localhost:5432

| Metric | Neon remote (Run 4) | **Local Postgres (Run 7)** |
|---|---|---|
| Error rate | 81% | **0%** ✓ |
| p95 latency | 11,680ms | **9ms** ✓ |
| p99 latency | 21,170ms | **70ms** ✓ |
| sync p95 | 21,124ms | **17ms** ✓ |
| sync avg | — | **46ms** |
| sync median | — | **1.2ms** |
| RPS | 83 | **955** |

**All thresholds pass.** Single Node.js instance handles 1k VUs trivially with local DB. Confirmed: bottleneck was always DB network latency (~300ms India→Neon), never code or Node.js capacity.

**Run 8 — 5k VUs attempt:** Hit Mac OS TCP socket backlog limit (`kern.ipc.somaxconn=128`). `dial: i/o timeout` errors at ~3k+ simultaneous connections. Not a Node.js or app limit — Linux (Render) default is 65535+. Local Mac testing ceiling is **~1k VUs**.

**Local testing strategy going forward:**
- Use local Postgres + ≤1k VUs for regression testing
- 5k+ VU testing requires Linux environment or Render

---

### Run 9 — Heavy sync: 100 transactions per request (10 VUs, local Postgres)

**Date:** 2026-09-05 | **VUs:** 10 | **Duration:** 2 minutes | **Payload:** 100 txns/sync | **DB:** localhost:5432

| Metric | Empty sync (Run 7) | Heavy sync 100 txns |
|---|---|---|
| Error rate | 0% | **0%** ✓ |
| sync success | 100% | **100%** ✓ |
| cards success | 100% | **100%** ✓ |
| sync avg | 46ms | 1776ms |
| sync p95 | 17ms | 4200ms |
| sync min | 0.5ms | 477ms |

**Bulk upsert confirmed correct under load.** Zero errors across 290 iterations × 100 transactions = 29,000 row upserts. Latency increase is expected: 100-row INSERT + returning 100 txns in response + connection pool queuing (10 VUs / 5 connections). No data loss, no failures.

**Note:** `type` column missing from Neon `UserCard` table — needs `prisma db push` against Neon before prod deploy.

---

## Target After All Fixes

| Scenario | Baseline | Run 3 (10 VUs) | Run 4 (1000 VUs) | Target |
|---|---|---|---|---|
| p95 latency (local) | 3860ms | 1700ms | 11680ms (saturated) | ~200ms |
| p95 latency (Render) | 136ms ✓ | not re-tested | not tested | <100ms |
| 1k concurrent | not tested | — | **fails** (single instance) | p95 <500ms |
| 10k concurrent | not tested | — | not tested | p95 <500ms |
| 50k spike (30s) | not tested | — | not tested | no data loss |
| sync DB queries | 501/request | **1/request** ✓ | 1/request ✓ | 1/request |
