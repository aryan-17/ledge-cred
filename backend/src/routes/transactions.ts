import { Hono } from 'hono'
import { getPrisma } from '../lib/prisma'
import log from '../lib/logger'
import type { AppVariables } from '../types'

export const transactionsRoute = new Hono<{ Variables: AppVariables }>()

transactionsRoute.delete('/:id', async (c) => {
  const uid = c.get('uid')
  const id  = c.req.param('id')

  const tx = await getPrisma().transaction.findUnique({ where: { id } })

  if (!tx) return c.json({ error: 'Not found' }, 404)
  if (tx.userId !== uid) return c.json({ error: 'Forbidden' }, 403)

  await getPrisma().transaction.delete({ where: { id } })
  log.info({ uid, id }, 'transaction deleted')

  return c.json({ ok: true })
})
