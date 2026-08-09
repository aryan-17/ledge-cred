package com.ledgecred.ccsettleapp.data.repository

import com.ledgecred.ccsettleapp.data.db.AppDatabase
import com.ledgecred.ccsettleapp.data.db.entity.SettleEvent
import com.ledgecred.ccsettleapp.data.prefs.AppPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

class SettleRepository(
    private val db: AppDatabase,
    private val prefs: AppPreferences
) {
    /** pendingPaise = Σ DEBIT − Σ REFUND − Σ matched SELF_TRANSFER. Derived, never stored. */
    fun observePendingPaise(): Flow<Long> = combine(
        db.transactionDao().observeAll(),
        db.settleEventDao().observeAll()
    ) { txs, events ->
        // Filtering by card happens at ingestion (SmsReceiver/SmsInboxReader) — all stored DEBITs are relevant
        val debits        = txs.filter { it.type == "DEBIT"         && it.deletedAt == null && it.settledAt == null }.sumOf { it.amountPaise }
        val refunds       = txs.filter { it.type == "REFUND"        && it.deletedAt == null }.sumOf { it.amountPaise }
        val selfTransfers = txs.filter { it.type == "SELF_TRANSFER" && it.deletedAt == null }.sumOf { it.amountPaise }
        maxOf(0L, debits - refunds - selfTransfers)
    }

    suspend fun createSettleEvent(requestedPaise: Long): SettleEvent {
        val pendingPaise = observePendingPaise().first()
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        val parentRef = "CCS$today"
        val existingCount = db.settleEventDao().getEventsForDay(parentRef).size
        val suffix = if (existingCount == 0) null else ('A' + existingCount).toString()

        val event = SettleEvent(
            id = UUID.randomUUID().toString(),
            parentRef = parentRef,
            suffix = suffix,
            status = "AWAITING",
            requestedAmountPaise = requestedPaise,
            pendingSnapshotPaise = pendingPaise,
            createdAt = System.currentTimeMillis()
        )
        db.settleEventDao().upsert(event)
        return event
    }

    suspend fun markManuallySettled(eventId: String) {
        val event = db.settleEventDao().findById(eventId) ?: return
        db.settleEventDao().upsert(
            event.copy(
                status = "MANUAL_MATCH",
                clearedAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }
}
