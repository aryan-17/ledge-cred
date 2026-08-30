import { Hono } from 'hono'
import { getPrisma } from '../lib/prisma'
import log from '../lib/logger'
import type { AppVariables } from '../types'

export const cardsRoute = new Hono<{ Variables: AppVariables }>()

const VALID_TYPES = ['DEBIT', 'REFUND', 'SELF_TRANSFER', 'UNPARSED', 'OTP', 'DECLINED', 'STATEMENT'] as const
const VALID_STATUSES = ['AWAITING', 'CLEARED', 'PARTIAL', 'MANUAL_MATCH', 'EXPIRED'] as const

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
  let body: { bank?: unknown; last4?: unknown; nickname?: unknown; type?: unknown }

  try {
    body = await c.req.json()
  } catch {
    return c.json({ error: 'Invalid JSON body' }, 400)
  }

  const { bank, last4, nickname, type } = body
  const cardType = (type === 'account') ? 'account' : 'card'

  if (!bank || typeof bank !== 'string' || bank.trim().length < 2 || bank.trim().length > 30) {
    return c.json({ error: 'bank must be a string between 2–30 characters' }, 400)
  }
  if (!last4 || typeof last4 !== 'string' || !/^\d{4}$/.test(last4)) {
    return c.json({ error: 'last4 must be exactly 4 digits' }, 400)
  }
  if (nickname !== undefined && nickname !== null && (typeof nickname !== 'string' || nickname.length > 50)) {
    return c.json({ error: 'nickname must be a string under 50 characters' }, 400)
  }

  // Max 10 cards per user
  const count = await getPrisma().userCard.count({ where: { userId: uid } })
  if (count >= 10) {
    return c.json({ error: 'Maximum 10 cards allowed per user' }, 400)
  }

  // Ensure user exists before inserting card (FK constraint)
  await getPrisma().user.upsert({ where: { uid }, update: {}, create: { uid } })

  const card = await getPrisma().userCard.upsert({
    where: { userId_bank_last4: { userId: uid, bank: bank.trim(), last4 } },
    update: { nickname: typeof nickname === 'string' ? nickname.trim() || null : null, type: cardType },
    create: { userId: uid, bank: bank.trim(), last4, nickname: typeof nickname === 'string' ? nickname.trim() || null : null, type: cardType }
  })

  log.info({ uid, bank: bank.trim(), last4 }, 'card added')
  return c.json({ card })
})

// Delete a card
cardsRoute.delete('/:id', async (c) => {
  const uid = c.get('uid')
  const id  = c.req.param('id')

  if (!id || typeof id !== 'string' || id.length > 100) {
    return c.json({ error: 'Invalid card id' }, 400)
  }

  const card = await getPrisma().userCard.findUnique({ where: { id } })
  if (!card)               return c.json({ error: 'Not found' }, 404)
  if (card.userId !== uid) return c.json({ error: 'Forbidden' }, 403)

  await getPrisma().userCard.delete({ where: { id } })
  log.info({ uid, id }, 'card deleted')
  return c.json({ ok: true })
})

export { VALID_TYPES, VALID_STATUSES }
