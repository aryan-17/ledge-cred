package com.ledgecred.ccsettleapp.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.ledgecred.ccsettleapp.data.db.AppDatabase
import com.ledgecred.ccsettleapp.data.db.entity.Transaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val db = AppDatabase.getInstance(context)

        messages.forEach { smsMessage ->
            val body    = smsMessage.messageBody        ?: return@forEach
            val sender  = smsMessage.originatingAddress ?: return@forEach
            val smsTime = smsMessage.timestampMillis

            val parsed = SmsParser.classify(body, sender)

            // Discard OTP, DECLINED, STATEMENT
            if (parsed.type in listOf(TransactionType.OTP, TransactionType.DECLINED, TransactionType.STATEMENT)) return@forEach

            CoroutineScope(Dispatchers.IO).launch {
                val allTracked       = db.userCardDao().getAll()
                val trackedCardLast4s = allTracked.filter { it.type == "card" }.map { it.last4 }.toSet()
                val trackedAcctLast4s = allTracked.filter { it.type == "account" }.map { it.last4 }.toSet()

                val bodyLast4 = SmsParser.CARD_LAST4_REGEX.find(body)?.groupValues?.get(1)

                // SELF_TRANSFER: only process if body matches a tracked account
                if (parsed.type == TransactionType.SELF_TRANSFER) {
                    if (bodyLast4 == null || bodyLast4 !in trackedAcctLast4s) return@launch
                } else if (trackedCardLast4s.isNotEmpty()) {
                    if (bodyLast4 == null || bodyLast4 !in trackedCardLast4s) return@launch
                }

                if (parsed.amountPaise == null && parsed.type != TransactionType.UNPARSED) return@launch

                val hash = SmsParser.dedupeHash(
                    bank          = parsed.bank ?: sender,
                    amountPaise   = parsed.amountPaise ?: 0L,
                    cardLast4     = parsed.cardLast4,
                    txnTimeMillis = smsTime
                )

                if (db.transactionDao().findByDedupeHash(hash) != null) return@launch

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
                                clearedAt          = System.currentTimeMillis(),
                                clearedAmountPaise = parsed.amountPaise,
                                updatedAt          = System.currentTimeMillis()
                            )
                        )
                        matchedEventId = match.id
                    }
                }

                db.transactionDao().upsert(
                    Transaction(
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
                )
            }
        }
    }
}
