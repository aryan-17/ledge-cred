import { Hono } from 'hono'
import { logger as honoLogger } from 'hono/logger'
import { authMiddleware } from './middleware/auth'
import { parseSms } from './lib/smsParser'
import { usersRoute } from './routes/users'
import { syncRoute } from './routes/sync'
import { fcmRoute } from './routes/fcm'
import { transactionsRoute } from './routes/transactions'
import log from './lib/logger'

const app = new Hono()

// HTTP request log — strip ANSI color codes Hono adds to message
const stripAnsi = (s: string) => s.replace(/\u001b\[\d+m/g, '')
app.use('*', honoLogger((message, ...rest) => {
  log.info({ msg: stripAnsi(message), ...rest })
}))

// Health check — no auth required
app.get('/health', (c) => c.json({ ok: true }))

// DEV ONLY: parse a raw SMS using regex parser (no auth required)
if (process.env.NODE_ENV !== 'production') {
  app.post('/dev/parse-sms', async (c) => {
    const { sender, body } = await c.req.json<{ sender: string; body: string }>()
    const result = parseSms(sender, body)
    log.info({ sender, result }, 'dev/parse-sms')
    return c.json(result)
  })
}

app.use('*', authMiddleware)
app.route('/users', usersRoute)
app.route('/sync', syncRoute)
app.route('/fcm', fcmRoute)
app.route('/transactions', transactionsRoute)

app.onError((err, c) => {
  log.error({ err: err.message, path: c.req.path, method: c.req.method }, 'unhandled error')
  return c.json({ error: 'Internal server error' }, 500)
})

export default app
