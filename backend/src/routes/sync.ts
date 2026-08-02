import { Hono } from 'hono'
import { getPrisma } from '../lib/prisma'
import type {
  AppVariables,
  SyncRequest,
  SyncResponse,
  SyncTransaction,
  SyncSettleEvent
} from '../types'

export const syncRoute = new Hono<{ Variables: AppVariables }>()

syncRoute.post('/', async (c) => {
  const uid = c.get('uid')
  const body = await c.req.json<SyncRequest>()
  const prisma = getPrisma()
  const syncedAt = new Date()
  const since = body.lastSyncedAt ? new Date(body.lastSyncedAt) : new Date(0)

  // ponytail: sequential upserts — fine for personal-scale syncs; batch if throughput matters
  for (const tx of body.transactions) {
    const data = mapTxToDb(tx, uid)
    await prisma.transaction.upsert({
      where: { id: tx.id },
      update: data,
      create: data
    })
  }

  for (const se of body.settleEvents) {
    const data = mapSeToDb(se, uid)
    await prisma.settleEvent.upsert({
      where: { id: se.id },
      update: data,
      create: data
    })
  }

  const [transactions, settleEvents] = await Promise.all([
    prisma.transaction.findMany({ where: { userId: uid, updatedAt: { gt: since } } }),
    prisma.settleEvent.findMany({ where: { userId: uid, updatedAt: { gt: since } } })
  ])

  const response: SyncResponse = {
    syncedAt: syncedAt.toISOString(),
    transactions: transactions.map(mapTxFromDb),
    settleEvents: settleEvents.map(mapSeFromDb)
  }

  return c.json(response)
})

function mapTxToDb(tx: SyncTransaction, uid: string) {
  return {
    id: tx.id,
    userId: uid,
    amountPaise: BigInt(tx.amountPaise),
    type: tx.type,
    cardLast4: tx.cardLast4,
    bank: tx.bank,
    txnTime: new Date(tx.txnTime),
    dedupeHash: tx.dedupeHash,
    matchedSettleEventId: tx.matchedSettleEventId,
    suggestedType: tx.suggestedType,
    suggestedConfidence: tx.suggestedConfidence,
    reviewed: tx.reviewed,
    updatedAt: new Date(tx.updatedAt),
    deletedAt: tx.deletedAt ? new Date(tx.deletedAt) : null
  }
}

function mapTxFromDb(tx: any): SyncTransaction {
  return {
    id: tx.id,
    amountPaise: Number(tx.amountPaise),
    type: tx.type,
    cardLast4: tx.cardLast4,
    bank: tx.bank,
    txnTime: tx.txnTime.toISOString(),
    dedupeHash: tx.dedupeHash,
    matchedSettleEventId: tx.matchedSettleEventId,
    suggestedType: tx.suggestedType,
    suggestedConfidence: tx.suggestedConfidence,
    reviewed: tx.reviewed,
    updatedAt: tx.updatedAt.toISOString(),
    deletedAt: tx.deletedAt?.toISOString() ?? null
  }
}

function mapSeToDb(se: SyncSettleEvent, uid: string) {
  return {
    id: se.id,
    userId: uid,
    parentRef: se.parentRef,
    suffix: se.suffix,
    status: se.status,
    requestedAmountPaise: BigInt(se.requestedAmountPaise),
    pendingSnapshotPaise: BigInt(se.pendingSnapshotPaise),
    createdAt: new Date(se.createdAt),
    clearedAt: se.clearedAt ? new Date(se.clearedAt) : null,
    clearedAmountPaise: se.clearedAmountPaise != null ? BigInt(se.clearedAmountPaise) : null,
    updatedAt: new Date(se.updatedAt),
    deletedAt: se.deletedAt ? new Date(se.deletedAt) : null
  }
}

function mapSeFromDb(se: any): SyncSettleEvent {
  return {
    id: se.id,
    parentRef: se.parentRef,
    suffix: se.suffix,
    status: se.status,
    requestedAmountPaise: Number(se.requestedAmountPaise),
    pendingSnapshotPaise: Number(se.pendingSnapshotPaise),
    createdAt: se.createdAt.toISOString(),
    clearedAt: se.clearedAt?.toISOString() ?? null,
    clearedAmountPaise: se.clearedAmountPaise != null ? Number(se.clearedAmountPaise) : null,
    updatedAt: se.updatedAt.toISOString(),
    deletedAt: se.deletedAt?.toISOString() ?? null
  }
}
