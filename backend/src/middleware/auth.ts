import { createMiddleware } from 'hono/factory'
import { getFirebaseAuth } from '../lib/firebase'
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
      await next()
    } catch {
      return c.json({ error: 'Invalid token' }, 401)
    }
  }
)
