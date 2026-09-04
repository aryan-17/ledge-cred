import { Hono } from 'hono'
import { getPrisma } from '../lib/prisma'
import { rateLimit } from '../lib/rateLimit'
import type { AppVariables } from '../types'

export const usersRoute = new Hono<{ Variables: AppVariables }>()

usersRoute.post('/register', async (c) => {
  const ip = c.req.header('x-forwarded-for')?.split(',')[0].trim() ?? 'unknown'
  if (!rateLimit(`register:${ip}`, 10, 60_000)) {
    return c.json({ error: 'Rate limit exceeded.' }, 429)
  }

  const uid = c.get('uid')
  const { fcmToken } = await c.req.json<{ fcmToken: string }>()

  await getPrisma().user.upsert({
    where: { uid },
    update: { fcmToken },
    create: { uid, fcmToken }
  })

  return c.json({ uid })
})
