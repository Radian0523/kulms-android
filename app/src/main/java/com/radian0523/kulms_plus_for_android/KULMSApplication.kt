package com.radian0523.kulms_plus_for_android

import android.app.Application
import com.radian0523.kulms_plus_for_android.data.remote.WebViewFetcher
import com.radian0523.kulms_plus_for_android.notification.NotificationHelper
import com.radian0523.kulms_plus_for_android.worker.RefreshWorker

class KULMSApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        WebViewFetcher.init(this)
        NotificationHelper.createChannel(this)
        RefreshWorker.schedule(this)
    }
}
