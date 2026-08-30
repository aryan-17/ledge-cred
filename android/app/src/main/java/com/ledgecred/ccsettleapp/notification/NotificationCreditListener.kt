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

        // Only process UPI credit / received notifications — fast pre-filter
        val lower = body.lowercase()
        if (!lower.contains("received") && !lower.contains("credited") &&
            !lower.contains("you've got") && !lower.contains("money received")) return

        val parsed = SmsParser.classify(body, sbn.packageName)

        // Only SELF_TRANSFER (UPI credit) from notifications — DEBIT is covered by SMS
        if (parsed.type != TransactionType.SELF_TRANSFER) return
        val amount = parsed.amountPaise ?: return

        CoroutineScope(Dispatchers.IO).launch {
            val db             = AppDatabase.getInstance(applicationContext)
            val allTracked     = db.userCardDao().getAll().associateBy { it.last4 }
            val accountLast4s  = allTracked.values.filter { it.type == "account" }.map { it.last4 }.toSet()

            val bodyLast4 = SmsParser.CARD_LAST4_REGEX.find(body)?.groupValues?.get(1)

            // Only process if no accounts configured, OR body matches a tracked account last4
            if (accountLast4s.isNotEmpty() && (bodyLast4 == null || bodyLast4 !in accountLast4s)) return@launch

            val bankName = allTracked[bodyLast4]?.bank ?: sbn.packageName.substringAfterLast(".")

            val hash = SmsParser.dedupeHash(
                bank          = bankName,
                amountPaise   = amount,
                cardLast4     = bodyLast4,
                txnTimeMillis = sbn.postTime
            )
            if (db.transactionDao().findByDedupeHash(hash) != null) return@launch

            // Match against an awaiting settle event
            var matchedEventId: String? = null
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

            db.transactionDao().upsert(
                Transaction(
                    id                   = UUID.randomUUID().toString(),
                    amountPaise          = amount,
                    type                 = TransactionType.SELF_TRANSFER.name,
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
