export type TransactionType =
  | 'DEBIT'
  | 'REFUND'
  | 'SELF_TRANSFER'
  | 'UNPARSED'
  | 'OTP'
  | 'DECLINED'
  | 'STATEMENT'

export type SettleStatus =
  | 'AWAITING'
  | 'CLEARED'
  | 'PARTIAL'
  | 'MANUAL_MATCH'
  | 'EXPIRED'

export interface SyncTransaction {
  id: string
  amountPaise: number
  type: TransactionType
  cardLast4: string | null
  bank: string
  txnTime: string           // ISO 8601
  dedupeHash: string
  matchedSettleEventId: string | null
  suggestedType: TransactionType | null
  suggestedConfidence: number | null
  reviewed: boolean
  updatedAt: string         // ISO 8601
  deletedAt: string | null  // ISO 8601
}

export interface SyncSettleEvent {
  id: string
  parentRef: string
  suffix: string | null
  status: SettleStatus
  requestedAmountPaise: number
  pendingSnapshotPaise: number
  createdAt: string
  clearedAt: string | null
  clearedAmountPaise: number | null
  updatedAt: string
  deletedAt: string | null
}

export interface SyncRequest {
  lastSyncedAt: string | null
  transactions: SyncTransaction[]
  settleEvents: SyncSettleEvent[]
}

export interface SyncResponse {
  syncedAt: string
  transactions: SyncTransaction[]
  settleEvents: SyncSettleEvent[]
}

export interface ClassifyMessage {
  id: string
  text: string
}

export interface ClassifyResult {
  id: string
  suggestedType: TransactionType
  confidence: number
}

export type AppVariables = { uid: string }
