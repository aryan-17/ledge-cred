package com.ledgecred.ccsettleapp

import android.app.Application
import com.ledgecred.ccsettleapp.worker.WorkerScheduler

class CcSettleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        WorkerScheduler.schedule(this)
    }
}
