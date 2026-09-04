package com.ledgecred.ccsettleapp.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ledgecred.ccsettleapp.data.api.ApiClient
import com.ledgecred.ccsettleapp.data.db.AppDatabase
import com.ledgecred.ccsettleapp.data.prefs.AppPreferences
import com.ledgecred.ccsettleapp.data.repository.SyncRepository
import kotlin.random.Random
import kotlinx.coroutines.delay

class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        // Jitter prevents thundering herd when backend restarts and all clients reconnect at once
        delay(Random.nextLong(0, 30_000))
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
