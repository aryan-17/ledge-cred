package com.ledgecred.ccsettleapp.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ledgecred.ccsettleapp.data.api.ApiClient
import com.ledgecred.ccsettleapp.data.db.AppDatabase
import com.ledgecred.ccsettleapp.data.prefs.AppPreferences
import com.ledgecred.ccsettleapp.data.repository.SyncRepository

class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        return try {
            SyncRepository(
                db    = AppDatabase.getInstance(applicationContext),
                api   = ApiClient.get(),
                prefs = AppPreferences(applicationContext)
            ).sync()
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
