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
            val body     = smsMessage.messageBody     ?: return@forEach
            val sender   = smsMessage.originatingAddress ?: return@forEach
            val smsTime  = smsMessage.timestampMillis

            val parsed = SmsParser.classify(body, sender)

            // Discard OTP and DECLINED — never store
            if (parsed.type == TransactionType.OTP || parsed.type == TransactionType.DECLINED) return@forEach

            val hash = SmsParser.dedupeHash(
                bank          = parsed.bank ?: sender,
                amountPaise   = parsed.amountPaise ?: 0L,
                cardLast4     = parsed.cardLast4,
                txnTimeMillis = smsTime
            )

            CoroutineScope(Dispatchers.IO).launch {
                // Deduplicate
                if (db.transactionDao().findByDedupeHash(hash) != null) return@launch

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
                    matchedSettleEventId = null,
                    suggestedType        = null,
                    suggestedConfidence  = null
                )

                // Check if SELF_TRANSFER clears an AWAITING settle event
                if (parsed.type == TransactionType.SELF_TRANSFER && parsed.amountPaise != null) {
                    val match = db.settleEventDao().getAwaitingEvents().firstOrNull { event ->
                        kotlin.math.abs(event.requestedAmountPaise - parsed.amountPaise) <= 100L // ±₹1
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
                        db.transactionDao().upsert(tx.copy(matchedSettleEventId = match.id))
                        return@launch
                    }
                }

                db.transactionDao().upsert(tx)
            }
        }
    }
}
