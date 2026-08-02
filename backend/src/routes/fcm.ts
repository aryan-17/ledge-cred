import { Hono } from 'hono'
import { getPrisma } from '../lib/prisma'
import { sendFcmNotification } from '../services/fcm'
import type { AppVariables } from '../types'

export const fcmRoute = new Hono<{ Variables: AppVariables }>()

fcmRoute.put('/token', async (c) => {
  const uid = c.get('uid')
  const { token } = await c.req.json<{ token: string }>()

  await getPrisma().user.update({
    where: { uid },
    data: { fcmToken: token }
  })

  return c.json({ ok: true })
})

fcmRoute.post('/notify', async (c) => {
  const uid = c.get('uid')
  const { title, body } = await c.req.json<{ title: string; body: string }>()

  const user = await getPrisma().user.findUnique({ where: { uid } })
  if (!user?.fcmToken) {
    return c.json({ error: 'No FCM token registered' }, 400)
  }

  await sendFcmNotification(user.fcmToken, title, body)
  return c.json({ ok: true })
})
