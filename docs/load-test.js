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

const BASE_URL = __ENV.BASE_URL || 'https://ledge-cred.onrender.com';
const TOKEN = __ENV.TOKEN;

const HEADERS = {
  'Authorization': `Bearer ${TOKEN}`,
  'Content-Type': 'application/json',
};

const SYNC_BODY = JSON.stringify({
  lastSyncedAt: null,
  transactions: [],
  settleEvents: []
});

export default function () {
  // Health
  const health = http.get(`${BASE_URL}/health`);
  check(health, { 'health 200': (r) => r.status === 200 });
  errorRate.add(health.status !== 200);

  // Sync (primary load)
  const sync = http.post(`${BASE_URL}/sync`, SYNC_BODY, { headers: HEADERS });
  check(sync, { 'sync 200': (r) => r.status === 200 });
  errorRate.add(sync.status !== 200);
  syncDuration.add(sync.timings.duration);

  // Cards
  const cards = http.get(`${BASE_URL}/cards`, { headers: HEADERS });
  check(cards, { 'cards 200': (r) => r.status === 200 });
  errorRate.add(cards.status !== 200);

  sleep(Math.random() * 2 + 1);
}
