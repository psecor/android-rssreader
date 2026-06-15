package net.secorp.rssreader.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import net.secorp.rssreader.auth.TokenStore
import net.secorp.rssreader.data.api.MarkReadRequest
import net.secorp.rssreader.data.api.MarkUnreadRequest
import net.secorp.rssreader.data.api.RssApi
import net.secorp.rssreader.data.db.dao.PendingActionDao
import retrofit2.HttpException

/**
 * Drains the local pending_actions queue, pushing each toggle to the
 * backend and deleting it on success. Per-item errors are handled inline:
 *
 * - 2xx: row dropped via deleteIfUnchanged so that a fresher toggle queued
 *   while we were mid-flight isn't accidentally discarded.
 * - 4xx (incl. 401): the row is dropped too. The server has refused this
 *   write and won't ever accept it (poison pill, or dead token). Letting
 *   it sit in the queue would loop forever; the user re-signs in to fix
 *   the auth case, fresh actions resume cleanly.
 * - 5xx / IOException: leave the row in place and Result.retry() so
 *   WorkManager's backoff brings us back.
 */
@HiltWorker
class WriteSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val rssApi: RssApi,
    private val pendingActionDao: PendingActionDao,
    private val tokenStore: TokenStore,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (tokenStore.getToken() == null) return Result.success()

        val actions = pendingActionDao.all()
        if (actions.isEmpty()) return Result.success()

        var anyTransient = false
        for (action in actions) {
            try {
                if (action.isRead) {
                    rssApi.markRead(MarkReadRequest(feedItemIds = listOf(action.itemId)))
                } else {
                    rssApi.markUnread(MarkUnreadRequest(feedItemId = action.itemId))
                }
                pendingActionDao.deleteIfUnchanged(action.itemId, action.queuedAt)
            } catch (e: HttpException) {
                if (e.code() in 400..499) {
                    pendingActionDao.deleteIfUnchanged(action.itemId, action.queuedAt)
                } else {
                    anyTransient = true
                }
            } catch (_: IOException) {
                anyTransient = true
            } catch (_: Throwable) {
                // Unexpected; treat as transient so we don't lose intent.
                anyTransient = true
            }
        }

        return if (anyTransient) Result.retry() else Result.success()
    }
}
