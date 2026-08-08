import { describe, it, expect, vi, beforeEach } from 'vitest'
import { Hono } from 'hono'
import { authMiddleware } from '../../src/middleware/auth'
import { syncRoute } from '../../src/routes/sync'
import type { SyncTransaction } from '../../src/types'

const mockVerifyIdToken = vi.fn()
const mockPrisma = {
  transaction: { upsert: vi.fn(), findMany: vi.fn() },
  settleEvent: { upsert: vi.fn(), findMany: vi.fn() }
}

vi.mock('../../src/lib/firebase', () => ({
  getFirebaseAuth: vi.fn(() => ({ verifyIdToken: mockVerifyIdToken }))
}))

vi.mock('../../src/lib/prisma', () => ({
  getPrisma: vi.fn(() => mockPrisma)
}))

const app = new Hono()
app.use('*', authMiddleware)
app.route('/sync', syncRoute)

const AUTH = { Authorization: 'Bearer valid', 'Content-Type': 'application/json' }

const emptyBody = {
  lastSyncedAt: '2026-08-01T00:00:00Z',
  transactions: [],
  settleEvents: []
}

describe('POST /sync', () => {
  beforeEach(() => {
    mockVerifyIdToken.mockResolvedValue({ uid: 'user-123' })
    mockPrisma.transaction.upsert.mockResolvedValue({})
    mockPrisma.transaction.findMany.mockResolvedValue([])
    mockPrisma.settleEvent.upsert.mockResolvedValue({})
    mockPrisma.settleEvent.findMany.mockResolvedValue([])
  })

  it('returns 200 with empty arrays when no server data', async () => {
    const res = await app.request('/sync', {
      method: 'POST',
      headers: AUTH,
      body: JSON.stringify(emptyBody)
    })
    expect(res.status).toBe(200)
    const body = await res.json()
    expect(body.transactions).toEqual([])
    expect(body.settleEvents).toEqual([])
    expect(body.syncedAt).toBeDefined()
  })

  it('uses new Date(0) as sentinel when lastSyncedAt is null', async () => {
    await app.request('/sync', {
      method: 'POST',
      headers: AUTH,
      body: JSON.stringify({ ...emptyBody, lastSyncedAt: null })
    })
    expect(mockPrisma.transaction.findMany).toHaveBeenCalledWith(
      expect.objectContaining({
        where: { userId: 'user-123', updatedAt: { gt: new Date(0) } }
      })
    )
  })

  it('maps server transaction rows to wire format', async () => {
    mockPrisma.transaction.findMany.mockResolvedValue([{
      id: 'tx-1',
      amountPaise: BigInt(250000),
      type: 'DEBIT',
      cardLast4: '1234',
      bank: 'HDFC',
      txnTime: new Date('2026-08-01T10:00:00Z'),
      dedupeHash: 'hash-abc',
      matchedSettleEventId: null,
      suggestedType: null,
      suggestedConfidence: null,
      reviewed: false,
      settledAt: null,
      updatedAt: new Date('2026-08-01T10:00:01Z'),
      deletedAt: null
    }])

    const res = await app.request('/sync', {
      method: 'POST',
      headers: AUTH,
      body: JSON.stringify(emptyBody)
    })

    const body = await res.json()
    const tx: SyncTransaction = body.transactions[0]
    expect(tx.id).toBe('tx-1')
    expect(tx.amountPaise).toBe(250000)
    expect(tx.type).toBe('DEBIT')
    expect(tx.bank).toBe('HDFC')
    expect(tx.deletedAt).toBeNull()
  })

  it('upserts incoming transactions scoped to uid', async () => {
    const incoming: SyncTransaction = {
      id: 'tx-new',
      amountPaise: 500000,
      type: 'DEBIT',
      cardLast4: '5678',
      bank: 'ICICI',
      txnTime: '2026-08-02T08:00:00Z',
      dedupeHash: 'hash-xyz',
      matchedSettleEventId: null,
      suggestedType: null,
      suggestedConfidence: null,
      reviewed: false,
      settledAt: null,
      updatedAt: '2026-08-02T08:00:01Z',
      deletedAt: null
    }

    await app.request('/sync', {
      method: 'POST',
      headers: AUTH,
      body: JSON.stringify({ ...emptyBody, transactions: [incoming] })
    })

    expect(mockPrisma.transaction.upsert).toHaveBeenCalledWith(
      expect.objectContaining({
        where: { id: 'tx-new' },
        create: expect.objectContaining({ userId: 'user-123', bank: 'ICICI' })
      })
    )
  })

  it('round-trips a manually-settled transaction (settledAt, no SMS involved)', async () => {
    const settledAtIso = '2026-08-08T09:00:00.000Z'
    const incoming: SyncTransaction = {
      id: 'tx-manual-settle',
      amountPaise: 120000,
      type: 'DEBIT',
      cardLast4: '4321',
      bank: 'HDFC',
      txnTime: '2026-08-02T08:00:00Z',
      dedupeHash: 'hash-manual',
      matchedSettleEventId: null,
      suggestedType: null,
      suggestedConfidence: null,
      reviewed: false,
      settledAt: settledAtIso,
      updatedAt: '2026-08-08T09:00:00.001Z',
      deletedAt: null
    }

    mockPrisma.transaction.findMany.mockResolvedValue([{
      id: 'tx-manual-settle',
      amountPaise: BigInt(120000),
      type: 'DEBIT',
      cardLast4: '4321',
      bank: 'HDFC',
      txnTime: new Date('2026-08-02T08:00:00Z'),
      dedupeHash: 'hash-manual',
      matchedSettleEventId: null,
      suggestedType: null,
      suggestedConfidence: null,
      reviewed: false,
      settledAt: new Date(settledAtIso),
      updatedAt: new Date('2026-08-08T09:00:00.001Z'),
      deletedAt: null
    }])

    const res = await app.request('/sync', {
      method: 'POST',
      headers: AUTH,
      body: JSON.stringify({ ...emptyBody, transactions: [incoming] })
    })

    expect(mockPrisma.transaction.upsert).toHaveBeenCalledWith(
      expect.objectContaining({
        where: { id: 'tx-manual-settle' },
        create: expect.objectContaining({ settledAt: new Date(settledAtIso) })
      })
    )

    const body = await res.json()
    const tx: SyncTransaction = body.transactions[0]
    expect(tx.settledAt).toBe(settledAtIso)
  })
})
