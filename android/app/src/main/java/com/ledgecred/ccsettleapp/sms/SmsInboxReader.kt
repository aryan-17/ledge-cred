package com.ledgecred.ccsettleapp.sms

import android.content.Context
import android.provider.Telephony
import com.ledgecred.ccsettleapp.data.db.AppDatabase
import com.ledgecred.ccsettleapp.data.db.entity.Transaction
import com.ledgecred.ccsettleapp.data.prefs.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.UUID

object SmsInboxReader {

    /**
     * Reads SMS inbox since [lastInboxReadAt] (incremental — not the full 7 days every open).
     * Falls back to 7 days on first run.
     * Loads all existing dedupe hashes into memory once — no per-SMS DB query.
     */
    suspend fun sync(context: Context) = withContext(Dispatchers.IO) {
        val db    = AppDatabase.getInstance(context)
        val prefs = AppPreferences(context)

        val lastRead = prefs.lastInboxReadAt.first()
        val since    = lastRead ?: (System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L)
        val now      = System.currentTimeMillis()

        // Load all existing hashes into memory — O(1) lookup per SMS instead of O(n) DB queries
        val existingHashes = db.transactionDao().getAllDedupeHashes().toHashSet()

        // Load tracked card last4s
        val trackedLast4s = db.userCardDao().getAll().map { it.last4 }.toSet()

        val cursor = context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} > ?",
            arrayOf(since.toString()),
            "${Telephony.Sms.DATE} DESC"
        ) ?: return@withContext

        val newTransactions = mutableListOf<Transaction>()

        cursor.use {
            while (it.moveToNext()) {
                val sender  = it.getString(0) ?: continue
                val body    = it.getString(1) ?: continue
                val smsTime = it.getLong(2)

                val parsed = SmsParser.classify(body, sender)

                if (parsed.type in listOf(
                        TransactionType.OTP, TransactionType.DECLINED, TransactionType.STATEMENT
                    )) continue

                // Filter by tracked card last4s (SELF_TRANSFER exempt)
                if (trackedLast4s.isNotEmpty() && parsed.type != TransactionType.SELF_TRANSFER) {
                    val bodyLast4 = SmsParser.CARD_LAST4_REGEX.find(body)?.groupValues?.get(1)
                    if (bodyLast4 == null || bodyLast4 !in trackedLast4s) continue
                }

                if (parsed.amountPaise == null && parsed.type != TransactionType.UNPARSED) continue

                val hash = SmsParser.dedupeHash(
                    bank          = parsed.bank ?: sender,
                    amountPaise   = parsed.amountPaise ?: 0L,
                    cardLast4     = parsed.cardLast4,
                    txnTimeMillis = smsTime
                )

                // In-memory dedupe check — no DB query per SMS
                if (existingHashes.contains(hash)) continue
                existingHashes.add(hash)

                var matchedEventId: String? = null
                if (parsed.type == TransactionType.SELF_TRANSFER && parsed.amountPaise != null) {
                    val match = db.settleEventDao().getAwaitingEvents().firstOrNull { event ->
                        kotlin.math.abs(event.requestedAmountPaise - parsed.amountPaise) <= 100L
                    }
                    if (match != null) {
                        val isPartial = parsed.amountPaise < match.requestedAmountPaise
                        db.settleEventDao().upsert(
                            match.copy(
                                status             = if (isPartial) "PARTIAL" else "CLEARED",
                                clearedAt          = smsTime,
                                clearedAmountPaise = parsed.amountPaise,
                                updatedAt          = System.currentTimeMillis()
                            )
                        )
                        matchedEventId = match.id
                    }
                }

                newTransactions.add(Transaction(
                    id                   = UUID.randomUUID().toString(),
                    amountPaise          = parsed.amountPaise ?: 0L,
                    type                 = parsed.type.name,
                    cardLast4            = parsed.cardLast4,
                    bank                 = parsed.bank ?: sender,
                    txnTime              = smsTime,
                    smsTime              = smsTime,
                    rawSms               = body,
                    dedupeHash           = hash,
                    matchedSettleEventId = matchedEventId,
                    suggestedType        = null,
                    suggestedConfidence  = null
                ))
            }
        }

        // Batch insert all new transactions at once
        if (newTransactions.isNotEmpty()) {
            db.transactionDao().upsert(*newTransactions.toTypedArray())
        }

        // Update last read timestamp
        prefs.setLastInboxReadAt(now)
    }
}
