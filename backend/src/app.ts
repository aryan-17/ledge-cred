import { Hono } from 'hono'
import { authMiddleware } from './middleware/auth'
import { usersRoute } from './routes/users'
import { syncRoute } from './routes/sync'
import { classifyRoute } from './routes/classify'
import { fcmRoute } from './routes/fcm'

const app = new Hono()

// Health check — no auth required
app.get('/health', (c) => c.json({ ok: true }))

app.use('*', authMiddleware)
app.route('/users', usersRoute)
app.route('/sync', syncRoute)
app.route('/classify', classifyRoute)
app.route('/fcm', fcmRoute)

export default app
