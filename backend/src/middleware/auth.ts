import { createMiddleware } from 'hono/factory'
import { getFirebaseApp } from '../lib/firebase'
import type { AppVariables } from '../types'

export const authMiddleware = createMiddleware<{ Variables: AppVariables }>(
  async (c, next) => {
    const authorization = c.req.header('Authorization')
    if (!authorization?.startsWith('Bearer ')) {
      return c.json({ error: 'Missing authorization token' }, 401)
    }
    const token = authorization.slice(7)
    try {
      const decoded = await getFirebaseApp().auth().verifyIdToken(token)
      c.set('uid', decoded.uid)
      await next()
    } catch {
      return c.json({ error: 'Invalid token' }, 401)
    }
  }
)
