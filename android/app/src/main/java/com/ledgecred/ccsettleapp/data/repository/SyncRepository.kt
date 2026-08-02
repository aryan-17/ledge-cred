package com.ledgecred.ccsettleapp.data.repository

import com.ledgecred.ccsettleapp.data.api.ApiService
import com.ledgecred.ccsettleapp.data.api.dto.*
import com.ledgecred.ccsettleapp.data.db.AppDatabase
import com.ledgecred.ccsettleapp.data.db.entity.SettleEvent
import com.ledgecred.ccsettleapp.data.db.entity.Transaction
import com.ledgecred.ccsettleapp.data.prefs.AppPreferences
import kotlinx.coroutines.flow.first
import java.time.Instant

class SyncRepository(
    private val db: AppDatabase,
    private val api: ApiService,
    private val prefs: AppPreferences
) {
    suspend fun sync() {
        val lastSyncedAt = prefs.lastSyncedAt.first()
        val since = lastSyncedAt ?: 0L
        val sinceIso = if (lastSyncedAt == null) null
                       else Instant.ofEpochMilli(since).toString()

        val localTxs = db.transactionDao().modifiedSince(since).map { it.toDto() }
        val localSes = db.settleEventDao().modifiedSince(since).map { it.toDto() }

        val response = api.sync(SyncRequest(
            lastSyncedAt = sinceIso,
            transactions = localTxs,
            settleEvents = localSes
        ))

        response.transactions.forEach { db.transactionDao().upsert(it.toEntity()) }
        response.settleEvents.forEach  { db.settleEventDao().upsert(it.toEntity()) }

        prefs.setLastSyncedAt(Instant.parse(response.syncedAt).toEpochMilli())
    }

    // --- Mapping helpers ---

    private fun Transaction.toDto() = SyncTransactionDto(
        id = id, amountPaise = amountPaise, type = type, cardLast4 = cardLast4, bank = bank,
        txnTime = Instant.ofEpochMilli(txnTime).toString(),
        dedupeHash = dedupeHash, matchedSettleEventId = matchedSettleEventId,
        suggestedType = suggestedType, suggestedConfidence = suggestedConfidence,
        reviewed = reviewed,
        updatedAt = Instant.ofEpochMilli(updatedAt).toString(),
        deletedAt = deletedAt?.let { Instant.ofEpochMilli(it).toString() }
        // rawSms and smsTime intentionally omitted — never synced
    )

    private fun SyncTransactionDto.toEntity() = Transaction(
        id = id, amountPaise = amountPaise, type = type, cardLast4 = cardLast4, bank = bank,
        txnTime = Instant.parse(txnTime).toEpochMilli(),
        smsTime = Instant.parse(txnTime).toEpochMilli(), // smsTime unknown from server, use txnTime
        rawSms = "",  // raw SMS never stored server-side
        dedupeHash = dedupeHash, matchedSettleEventId = matchedSettleEventId,
        suggestedType = suggestedType, suggestedConfidence = suggestedConfidence,
        reviewed = reviewed,
        updatedAt = Instant.parse(updatedAt).toEpochMilli(),
        deletedAt = deletedAt?.let { Instant.parse(it).toEpochMilli() }
    )

    private fun SettleEvent.toDto() = SyncSettleEventDto(
        id = id, parentRef = parentRef, suffix = suffix, status = status,
        requestedAmountPaise = requestedAmountPaise, pendingSnapshotPaise = pendingSnapshotPaise,
        createdAt = Instant.ofEpochMilli(createdAt).toString(),
        clearedAt = clearedAt?.let { Instant.ofEpochMilli(it).toString() },
        clearedAmountPaise = clearedAmountPaise,
        updatedAt = Instant.ofEpochMilli(updatedAt).toString(),
        deletedAt = deletedAt?.let { Instant.ofEpochMilli(it).toString() }
    )

    private fun SyncSettleEventDto.toEntity() = SettleEvent(
        id = id, parentRef = parentRef, suffix = suffix, status = status,
        requestedAmountPaise = requestedAmountPaise, pendingSnapshotPaise = pendingSnapshotPaise,
        createdAt = Instant.parse(createdAt).toEpochMilli(),
        clearedAt = clearedAt?.let { Instant.parse(it).toEpochMilli() },
        clearedAmountPaise = clearedAmountPaise,
        updatedAt = Instant.parse(updatedAt).toEpochMilli(),
        deletedAt = deletedAt?.let { Instant.parse(it).toEpochMilli() }
    )
}
