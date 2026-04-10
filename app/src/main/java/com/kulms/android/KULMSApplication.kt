package com.kulms.android

import android.app.Application
import com.kulms.android.data.remote.WebViewFetcher
import com.kulms.android.notification.NotificationHelper
import com.kulms.android.worker.RefreshWorker

class KULMSApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        WebViewFetcher.init(this)
        NotificationHelper.createChannel(this)
        RefreshWorker.schedule(this)
    }
}
