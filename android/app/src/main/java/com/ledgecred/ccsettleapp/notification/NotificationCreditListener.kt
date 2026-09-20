package com.ledgecred.ccsettleapp.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.ledgecred.ccsettleapp.data.db.AppDatabase
import com.ledgecred.ccsettleapp.data.db.entity.Transaction
import com.ledgecred.ccsettleapp.sms.SmsParser
import com.ledgecred.ccsettleapp.sms.TransactionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class NotificationCreditListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification?.extras ?: return
        val title  = extras.getCharSequence("android.title")?.toString() ?: ""
        val text   = extras.getCharSequence("android.text")?.toString()  ?: ""
        val body   = "$title $text"

        val lower = body.lowercase()
        // Fast pre-filter: must contain a credit or debit transaction keyword
        val hasCredit = lower.contains("received") || lower.contains("credited") ||
                        lower.contains("you've got") || lower.contains("money received")
        val hasDebit  = lower.contains("spent") || lower.contains("debited") ||
                        lower.contains("debit") || lower.contains("used at") ||
                        lower.contains("withdrawn")
        if (!hasCredit && !hasDebit) return

        val parsed = SmsParser.classify(body, sbn.packageName)

        // Handle UPI credits (SELF_TRANSFER) and bank push debit notifications (DEBIT)
        if (parsed.type != TransactionType.SELF_TRANSFER && parsed.type != TransactionType.DEBIT) return
        val amount = parsed.amountPaise ?: return

        CoroutineScope(Dispatchers.IO).launch {
            val db         = AppDatabase.getInstance(applicationContext)
            val allTracked = db.userCardDao().getAll().associateBy { it.last4 }

            val bodyLast4 = SmsParser.CARD_LAST4_REGEX.find(body)?.groupValues?.get(1)

            if (parsed.type == TransactionType.SELF_TRANSFER) {
                // Only process if no accounts configured, OR body matches a tracked account last4
                val accountLast4s = allTracked.values.filter { it.type == "account" }.map { it.last4 }.toSet()
                if (accountLast4s.isNotEmpty() && (bodyLast4 == null || bodyLast4 !in accountLast4s)) return@launch
            } else {
                // DEBIT: filter by tracked cards (mirrors SmsInboxReader behaviour)
                val cardLast4s = allTracked.values.filter { it.type == "card" }.map { it.last4 }.toSet()
                if (cardLast4s.isNotEmpty() && (bodyLast4 == null || bodyLast4 !in cardLast4s)) return@launch
            }

            val bankName = allTracked[bodyLast4]?.bank ?: sbn.packageName.substringAfterLast(".")

            val hash = SmsParser.dedupeHash(
                bank          = bankName,
                amountPaise   = amount,
                cardLast4     = bodyLast4,
                txnTimeMillis = sbn.postTime
            )
            if (db.transactionDao().findByDedupeHash(hash) != null) return@launch

            // Settle-event matching only applies to incoming credits
            var matchedEventId: String? = null
            if (parsed.type == TransactionType.SELF_TRANSFER) {
                val match = db.settleEventDao().getAwaitingEvents().firstOrNull { event ->
                    kotlin.math.abs(event.requestedAmountPaise - amount) <= 100L
                }
                if (match != null) {
                    val isPartial = amount < match.requestedAmountPaise
                    db.settleEventDao().upsert(
                        match.copy(
                            status             = if (isPartial) "PARTIAL" else "CLEARED",
                            clearedAt          = System.currentTimeMillis(),
                            clearedAmountPaise = amount,
                            updatedAt          = System.currentTimeMillis()
                        )
                    )
                    matchedEventId = match.id
                }
            }

            db.transactionDao().upsert(
                Transaction(
                    id                   = UUID.randomUUID().toString(),
                    amountPaise          = amount,
                    type                 = parsed.type.name,
                    cardLast4            = bodyLast4,
                    bank                 = bankName,
                    txnTime              = sbn.postTime,
                    smsTime              = sbn.postTime,
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
