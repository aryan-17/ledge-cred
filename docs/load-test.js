import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const errorRate = new Rate('errors');
const syncDuration = new Trend('sync_duration');

export const options = {
  stages: [
    { duration: '1m', target: 100 },
    { duration: '3m', target: 1000 },
    { duration: '5m', target: 5000 },
    { duration: '5m', target: 10000 },
    { duration: '2m', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    errors: ['rate<0.01'],
    sync_duration: ['p(95)<800'],
  },
};

// Round-robin across multiple base URLs if BASE_URLS env provided (comma-separated)
// e.g. BASE_URLS=http://localhost:3000,http://localhost:3001
const _urls = (__ENV.BASE_URLS || __ENV.BASE_URL || 'https://ledge-cred.onrender.com').split(',');
const BASE_URL = _urls[(__VU - 1) % _urls.length];

// Multi-token: load from tokens.txt (one per line) or fall back to single TOKEN env var.
// Each VU picks a token by index so rate limits apply per-user, not all on one UID.
const TOKENS = (() => {
  try {
    return open('./tokens.txt').trim().split('\n').map(t => t.trim()).filter(Boolean);
  } catch {
    const t = __ENV.TOKEN;
    if (!t) throw new Error('Set TOKEN env var or create docs/tokens.txt');
    return [t.trim()];
  }
})();

function getHeaders() {
  const token = TOKENS[(__VU - 1) % TOKENS.length];
  return {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json',
  };
}

const SYNC_BODY = JSON.stringify({
  lastSyncedAt: null,
  transactions: [],
  settleEvents: []
});

export default function () {
  const headers = getHeaders();

  // Sync (primary load)
  const sync = http.post(`${BASE_URL}/sync`, SYNC_BODY, { headers });
  check(sync, { 'sync 200': (r) => r.status === 200 });
  errorRate.add(sync.status !== 200);
  syncDuration.add(sync.timings.duration);

  // Cards
  const cards = http.get(`${BASE_URL}/cards`, { headers });
  check(cards, { 'cards 200': (r) => r.status === 200 });
  errorRate.add(cards.status !== 200);

  sleep(Math.random() * 2 + 1);
}
