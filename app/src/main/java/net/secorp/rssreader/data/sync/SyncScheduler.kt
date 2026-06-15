package net.secorp.rssreader.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val workManager = WorkManager.getInstance(context)

    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun enqueueOneShot() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraint)
            .build()
        workManager.enqueueUniqueWork(
            ONE_SHOT_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    /** Push any pending mark-read/unread actions to the backend. */
    fun enqueueWritePush() {
        val request = OneTimeWorkRequestBuilder<WriteSyncWorker>()
            .setConstraints(networkConstraint)
            .build()
        workManager.enqueueUniqueWork(
            WRITE_PUSH_NAME,
            // APPEND_OR_REPLACE so a queued push that hasn't started
            // isn't redundantly enqueued, but a brand-new toggle while
            // a push is mid-flight still gets a follow-up run.
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }

    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraint)
            .build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancelAll() {
        workManager.cancelUniqueWork(ONE_SHOT_NAME)
        workManager.cancelUniqueWork(WRITE_PUSH_NAME)
        workManager.cancelUniqueWork(PERIODIC_NAME)
    }

    private companion object {
        const val ONE_SHOT_NAME = "rss_sync_oneshot"
        const val WRITE_PUSH_NAME = "rss_write_push"
        const val PERIODIC_NAME = "rss_sync_periodic"
    }
}
