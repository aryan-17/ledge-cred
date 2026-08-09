import { Hono } from 'hono'
import { getPrisma } from '../lib/prisma'
import log from '../lib/logger'
import type {
  AppVariables,
  SyncRequest,
  SyncResponse,
  SyncTransaction,
  SyncSettleEvent,
  TransactionType,
  SettleStatus
} from '../types'

export const syncRoute = new Hono<{ Variables: AppVariables }>()

const VALID_TX_TYPES   = new Set<TransactionType>(['DEBIT','REFUND','SELF_TRANSFER','UNPARSED','OTP','DECLINED','STATEMENT'])
const VALID_SE_STATUSES = new Set<SettleStatus>(['AWAITING','CLEARED','PARTIAL','MANUAL_MATCH','EXPIRED'])
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const ISO_RE  = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}/

function isUUID(s: unknown): s is string { return typeof s === 'string' && UUID_RE.test(s) }
function isISO(s: unknown): s is string  { return typeof s === 'string' && ISO_RE.test(s) }
function isNullableISO(s: unknown): boolean { return s === null || isISO(s) }

function validateTransaction(tx: unknown, i: number): string | null {
  if (!tx || typeof tx !== 'object') return `transactions[${i}]: must be object`
  const t = tx as Record<string, unknown>
  if (!isUUID(t.id))                          return `transactions[${i}].id: invalid UUID`
  if (typeof t.amountPaise !== 'number' || !Number.isInteger(t.amountPaise) || t.amountPaise < 0)
                                               return `transactions[${i}].amountPaise: must be non-negative integer`
  if (!VALID_TX_TYPES.has(t.type as TransactionType))
                                               return `transactions[${i}].type: invalid (got "${t.type}")`
  if (t.cardLast4 !== null && (typeof t.cardLast4 !== 'string' || !/^\d{4}$/.test(t.cardLast4)))
                                               return `transactions[${i}].cardLast4: must be 4 digits or null`
  if (typeof t.bank !== 'string' || t.bank.length < 1 || t.bank.length > 60)
                                               return `transactions[${i}].bank: invalid`
  if (!isISO(t.txnTime))                       return `transactions[${i}].txnTime: invalid ISO date`
  if (typeof t.dedupeHash !== 'string' || t.dedupeHash.length < 1)
                                               return `transactions[${i}].dedupeHash: required`
  if (!isISO(t.updatedAt))                     return `transactions[${i}].updatedAt: invalid ISO date`
  if (!isNullableISO(t.deletedAt))             return `transactions[${i}].deletedAt: invalid ISO date or null`
  return null
}

function validateSettleEvent(se: unknown, i: number): string | null {
  if (!se || typeof se !== 'object') return `settleEvents[${i}]: must be object`
  const s = se as Record<string, unknown>
  if (!isUUID(s.id))                              return `settleEvents[${i}].id: invalid UUID`
  if (typeof s.parentRef !== 'string' || s.parentRef.length < 1)
                                                   return `settleEvents[${i}].parentRef: required`
  if (!VALID_SE_STATUSES.has(s.status as SettleStatus))
                                                   return `settleEvents[${i}].status: invalid (got "${s.status}")`
  if (typeof s.requestedAmountPaise !== 'number' || !Number.isInteger(s.requestedAmountPaise) || s.requestedAmountPaise < 0)
                                                   return `settleEvents[${i}].requestedAmountPaise: must be non-negative integer`
  if (typeof s.pendingSnapshotPaise !== 'number' || !Number.isInteger(s.pendingSnapshotPaise) || s.pendingSnapshotPaise < 0)
                                                   return `settleEvents[${i}].pendingSnapshotPaise: must be non-negative integer`
  if (!isISO(s.createdAt))                         return `settleEvents[${i}].createdAt: invalid ISO date`
  if (!isISO(s.updatedAt))                         return `settleEvents[${i}].updatedAt: invalid ISO date`
  if (!isNullableISO(s.deletedAt))                 return `settleEvents[${i}].deletedAt: invalid`
  return null
}

syncRoute.post('/', async (c) => {
  const uid = c.get('uid')
  let body: SyncRequest

  try {
    body = await c.req.json<SyncRequest>()
  } catch {
    return c.json({ error: 'Invalid JSON body' }, 400)
  }

  if (body.lastSyncedAt !== null && body.lastSyncedAt !== undefined && !isISO(body.lastSyncedAt)) {
    return c.json({ error: 'lastSyncedAt: must be ISO 8601 string or null' }, 400)
  }
  if (!Array.isArray(body.transactions)) {
    return c.json({ error: 'transactions: must be array' }, 400)
  }
  if (!Array.isArray(body.settleEvents)) {
    return c.json({ error: 'settleEvents: must be array' }, 400)
  }
  if (body.transactions.length > 500) {
    return c.json({ error: 'transactions: max 500 per sync' }, 400)
  }
  if (body.settleEvents.length > 200) {
    return c.json({ error: 'settleEvents: max 200 per sync' }, 400)
  }

  for (let i = 0; i < body.transactions.length; i++) {
    const err = validateTransaction(body.transactions[i], i)
    if (err) return c.json({ error: err }, 400)
  }

  for (let i = 0; i < body.settleEvents.length; i++) {
    const err = validateSettleEvent(body.settleEvents[i], i)
    if (err) return c.json({ error: err }, 400)
  }

  const prisma = getPrisma()
  const syncedAt = new Date()
  const since = body.lastSyncedAt ? new Date(body.lastSyncedAt) : new Date(0)
  log.info({ uid, txCount: body.transactions.length, seCount: body.settleEvents.length, since }, 'sync start')

  // ponytail: sequential upserts — fine for personal-scale syncs; batch if throughput matters
  for (const tx of body.transactions) {
    const data = mapTxToDb(tx, uid)
    await prisma.transaction.upsert({ where: { id: tx.id }, update: data, create: data })
  }

  for (const se of body.settleEvents) {
    const data = mapSeToDb(se, uid)
    await prisma.settleEvent.upsert({ where: { id: se.id }, update: data, create: data })
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
    settledAt: tx.settledAt ? new Date(tx.settledAt) : null,
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
    settledAt: tx.settledAt?.toISOString() ?? null,
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
