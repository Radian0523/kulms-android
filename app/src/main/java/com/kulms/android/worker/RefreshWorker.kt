package com.kulms.android.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kulms.android.data.local.AppDatabase
import com.kulms.android.data.remote.SakaiApiClient
import com.kulms.android.notification.NotificationHelper
import java.util.concurrent.TimeUnit

class RefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "RefreshWorker"
        private const val WORK_NAME = "kulms_refresh"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(1, TimeUnit.HOURS)
                .setInitialDelay(1, TimeUnit.HOURS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val sessionValid = SakaiApiClient.checkSession()
            if (!sessionValid) {
                Log.d(TAG, "Session expired, skipping refresh")
                return Result.success()
            }

            val results = SakaiApiClient.fetchAllAssignments()
            val assignments = SakaiApiClient.buildAssignments(results)

            val dao = AppDatabase.getInstance(applicationContext).assignmentDao()
            dao.replaceAll(assignments)

            NotificationHelper.scheduleNotifications(applicationContext, assignments)

            Log.d(TAG, "Background refresh completed: ${assignments.size} assignments")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Background refresh failed", e)
            Result.retry()
        }
    }
}
