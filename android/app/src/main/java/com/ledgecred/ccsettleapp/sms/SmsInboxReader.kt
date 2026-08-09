package com.ledgecred.ccsettleapp.sms

import android.content.Context
import android.provider.Telephony
import com.ledgecred.ccsettleapp.data.db.AppDatabase
import com.ledgecred.ccsettleapp.data.db.entity.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

object SmsInboxReader {

    /**
     * Reads SMS inbox for the last [lookbackDays] days, parses bank SMS,
     * and inserts any new transactions into Room (deduped by hash).
     */
    suspend fun sync(context: Context, lookbackDays: Int = 7) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        val since = System.currentTimeMillis() - lookbackDays * 24 * 60 * 60 * 1000L

        val cursor = context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE
            ),
            "${Telephony.Sms.DATE} > ?",
            arrayOf(since.toString()),
            "${Telephony.Sms.DATE} DESC"
        ) ?: return@withContext

        cursor.use {
            while (it.moveToNext()) {
                val sender = it.getString(0) ?: continue
                val body   = it.getString(1) ?: continue
                val smsTime = it.getLong(2)

                val parsed = SmsParser.classify(body, sender)

                // Only process SMS containing one of the user's tracked card last4s
                // SELF_TRANSFER (savings credit) is exempt from this filter
                val trackedLast4s = db.userCardDao().getAll().map { it.last4 }.toSet()
                if (trackedLast4s.isNotEmpty() && parsed.type != TransactionType.SELF_TRANSFER) {
                    val bodyLast4 = SmsParser.CARD_LAST4_REGEX.find(body)?.groupValues?.get(1)
                    if (bodyLast4 == null || bodyLast4 !in trackedLast4s) continue
                }

                // Discard non-financial SMS
                if (parsed.type in listOf(
                        TransactionType.OTP,
                        TransactionType.DECLINED,
                        TransactionType.STATEMENT
                    )) continue

                if (parsed.amountPaise == null && parsed.type != TransactionType.UNPARSED) continue

                val hash = SmsParser.dedupeHash(
                    bank          = parsed.bank ?: sender,
                    amountPaise   = parsed.amountPaise ?: 0L,
                    cardLast4     = parsed.cardLast4,
                    txnTimeMillis = smsTime
                )

                // Skip if already in Room
                if (db.transactionDao().findByDedupeHash(hash) != null) continue

                var matchedEventId: String? = null

                // Match SELF_TRANSFER against AWAITING settle events
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

                val tx = Transaction(
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
                )
                db.transactionDao().upsert(tx)
            }
        }
    }
}
