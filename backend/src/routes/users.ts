import { Hono } from 'hono'
import { getPrisma } from '../lib/prisma'
import type { AppVariables } from '../types'

export const usersRoute = new Hono<{ Variables: AppVariables }>()

usersRoute.post('/register', async (c) => {
  const uid = c.get('uid')
  const { fcmToken } = await c.req.json<{ fcmToken: string }>()

  await getPrisma().user.upsert({
    where: { uid },
    update: { fcmToken },
    create: { uid, fcmToken }
  })

  return c.json({ uid })
})
