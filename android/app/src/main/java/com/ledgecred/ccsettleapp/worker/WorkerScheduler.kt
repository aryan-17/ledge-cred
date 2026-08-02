package com.ledgecred.ccsettleapp.worker

import android.content.Context
import androidx.work.*
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

object WorkerScheduler {
    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)

        // Sync: every 15 minutes, requires network
        wm.enqueueUniquePeriodicWork(
            "sync",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
        )

        // Digest: daily at 22:00 local time
        val now         = LocalDateTime.now()
        val target      = now.withHour(22).withMinute(0).withSecond(0).withNano(0)
        val nextTarget  = if (target.isAfter(now)) target else target.plusDays(1)
        val initialDelay = Duration.between(now, nextTarget).toMillis()

        wm.enqueueUniquePeriodicWork(
            "digest",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DigestWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .build()
        )

        // Classify: daily (runs overnight alongside digest window)
        wm.enqueueUniquePeriodicWork(
            "classify",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ClassifyWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .build()
        )
    }
}
