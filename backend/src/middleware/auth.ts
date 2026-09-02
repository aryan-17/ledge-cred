import { createMiddleware } from 'hono/factory'
import { getFirebaseAuth } from '../lib/firebase'
import { getCached, setCache } from '../lib/authCache'
import type { AppVariables } from '../types'

export const authMiddleware = createMiddleware<{ Variables: AppVariables }>(
  async (c, next) => {
    const authorization = c.req.header('Authorization')
    if (!authorization?.startsWith('Bearer ')) {
      return c.json({ error: 'Missing authorization token' }, 401)
    }
    const token = authorization.slice(7)
    const cached = getCached(token)
    if (cached) {
      c.set('uid', cached)
      await next()
      return
    }
    try {
      const decoded = await getFirebaseAuth().verifyIdToken(token)
      setCache(token, decoded.uid, decoded.exp)
      c.set('uid', decoded.uid)
      await next()
    } catch {
      return c.json({ error: 'Invalid token' }, 401)
    }
  }
)
