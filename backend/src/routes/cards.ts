import { Hono } from 'hono'
import { getPrisma } from '../lib/prisma'
import log from '../lib/logger'
import type { AppVariables } from '../types'

export const cardsRoute = new Hono<{ Variables: AppVariables }>()

// List user's cards
cardsRoute.get('/', async (c) => {
  const uid = c.get('uid')
  const cards = await getPrisma().userCard.findMany({
    where: { userId: uid },
    orderBy: { createdAt: 'asc' }
  })
  return c.json({ cards })
})

// Add a card
cardsRoute.post('/', async (c) => {
  const uid = c.get('uid')
  const { bank, last4, nickname } = await c.req.json<{
    bank: string; last4: string; nickname?: string
  }>()

  if (!bank || !last4 || last4.length !== 4 || !/^\d{4}$/.test(last4)) {
    return c.json({ error: 'bank and 4-digit last4 required' }, 400)
  }

  const card = await getPrisma().userCard.upsert({
    where: { userId_bank_last4: { userId: uid, bank, last4 } },
    update: { nickname: nickname ?? null },
    create: { userId: uid, bank, last4, nickname: nickname ?? null }
  })

  log.info({ uid, bank, last4 }, 'card added')
  return c.json({ card })
})

// Delete a card
cardsRoute.delete('/:id', async (c) => {
  const uid = c.get('uid')
  const id  = c.req.param('id')

  const card = await getPrisma().userCard.findUnique({ where: { id } })
  if (!card)           return c.json({ error: 'Not found' }, 404)
  if (card.userId !== uid) return c.json({ error: 'Forbidden' }, 403)

  await getPrisma().userCard.delete({ where: { id } })
  log.info({ uid, id }, 'card deleted')
  return c.json({ ok: true })
})
