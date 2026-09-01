import { Hono } from 'hono'
import { logger as honoLogger } from 'hono/logger'
import { bodyLimit } from 'hono/body-limit'
import { authMiddleware } from './middleware/auth'
import { parseSms } from './lib/smsParser'
import { rateLimit } from './lib/rateLimit'
import { getPrisma } from './lib/prisma'
import { usersRoute } from './routes/users'
import { syncRoute } from './routes/sync'
import { fcmRoute } from './routes/fcm'
import { transactionsRoute } from './routes/transactions'
import { cardsRoute } from './routes/cards'
import log from './lib/logger'

const app = new Hono()

// HTTP request log — strip ANSI color codes Hono adds to message
const stripAnsi = (s: string) => s.replace(/\u001b\[\d+m/g, '')
app.use('*', honoLogger((message, ...rest) => {
  log.info({ msg: stripAnsi(message), ...rest })
}))

// Body size limit — 512KB max on all routes
app.use('*', bodyLimit({ maxSize: 512 * 1024, onError: (c) =>
  c.json({ error: 'Request body too large (max 512KB)' }, 413)
}))

// Health check — pings DB to keep Neon from pausing on free tier
app.get('/health', async (c) => {
  try {
    await getPrisma().$queryRaw`SELECT 1`
    return c.json({ ok: true })
  } catch {
    return c.json({ ok: false, db: 'unreachable' }, 503)
  }
})

// DEV ONLY: parse a raw SMS using regex parser
// Protected by dev secret header even in non-prod to prevent abuse
app.post('/dev/parse-sms', async (c) => {
  const devSecret = process.env.DEV_SECRET
  if (devSecret && c.req.header('x-dev-secret') !== devSecret) {
    return c.json({ error: 'Unauthorized' }, 401)
  }
  try {
    const { sender, body } = await c.req.json<{ sender: string; body: string }>()
    const result = parseSms(sender, body)
    log.info({ sender, result }, 'dev/parse-sms')
    return c.json(result)
  } catch {
    return c.json({ error: 'Invalid request body' }, 400)
  }
})

// Auth middleware for all routes below
app.use('*', authMiddleware)

// Per-UID rate limiting: 60 requests/min on sync, 120/min on others
// LOAD TEST: limits raised to 100k — revert after testing
app.use('/sync/*', async (c, next) => {
  const uid = (c as any).get('uid') as string | undefined
  if (uid && !rateLimit(uid, 100_000, 60_000)) {
    log.warn({ uid }, 'sync rate limit exceeded')
    return c.json({ error: 'Rate limit exceeded. Try again in a minute.' }, 429)
  }
  await next()
})

app.use('*', async (c, next) => {
  const uid = (c as any).get('uid') as string | undefined
  if (uid && !rateLimit(`${uid}:general`, 100_000, 60_000)) {
    return c.json({ error: 'Rate limit exceeded.' }, 429)
  }
  await next()
})

app.route('/users', usersRoute)
app.route('/sync', syncRoute)
app.route('/fcm', fcmRoute)
app.route('/transactions', transactionsRoute)
app.route('/cards', cardsRoute)

app.onError((err, c) => {
  log.error({ err: err.message, path: c.req.path, method: c.req.method }, 'unhandled error')
  return c.json({ error: 'Internal server error' }, 500)
})

export default app
