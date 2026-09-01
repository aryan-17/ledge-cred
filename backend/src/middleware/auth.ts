import { createMiddleware } from 'hono/factory'
import { getFirebaseAuth } from '../lib/firebase'
import log from '../lib/logger'
import type { AppVariables } from '../types'

export const authMiddleware = createMiddleware<{ Variables: AppVariables }>(
  async (c, next) => {
    const authorization = c.req.header('Authorization')
    if (!authorization?.startsWith('Bearer ')) {
      return c.json({ error: 'Missing authorization token' }, 401)
    }
    const token = authorization.slice(7)
    try {
      const decoded = await getFirebaseAuth().verifyIdToken(token)
      c.set('uid', decoded.uid)
      log.info({ uid: decoded.uid, token: token.slice(-12) }, 'auth ok — use last 12 chars to identify token')
      await next()
    } catch {
      return c.json({ error: 'Invalid token' }, 401)
    }
  }
)
