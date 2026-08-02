package com.ledgecred.ccsettleapp.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ledgecred.ccsettleapp.data.api.ApiClient
import com.ledgecred.ccsettleapp.data.api.dto.FcmNotifyRequest
import com.ledgecred.ccsettleapp.data.db.AppDatabase
import com.ledgecred.ccsettleapp.data.prefs.AppPreferences
import com.ledgecred.ccsettleapp.data.repository.SettleRepository
import kotlinx.coroutines.flow.first

class DigestWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        return try {
            val prefs        = AppPreferences(applicationContext)
            val pendingPaise = SettleRepository(
                AppDatabase.getInstance(applicationContext), prefs
            ).observePendingPaise().first()

            if (pendingPaise <= 0L) return Result.success()

            // Format with Indian grouping for notification body
            val rupees    = pendingPaise / 100.0
            val formatted = formatIndianRupees(pendingPaise)

            ApiClient.get().sendNotification(
                FcmNotifyRequest(title = "CC Settle", body = "Settle $formatted now")
            )
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    /** Formats paise as ₹X,XX,XXX (Indian grouping). */
    private fun formatIndianRupees(paise: Long): String {
        val rupees = paise / 100
        val s = rupees.toString()
        if (s.length <= 3) return "₹$s"
        val last3 = s.takeLast(3)
        val rest  = s.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
        return "₹$rest,$last3"
    }
}
