package net.secorp.rssreader.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import net.secorp.rssreader.auth.TokenStore
import net.secorp.rssreader.data.repo.RssRepository
import retrofit2.HttpException

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val rssRepository: RssRepository,
    private val tokenStore: TokenStore,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (tokenStore.getToken() == null) return Result.success()

        return try {
            rssRepository.refreshAll()
            Result.success()
        } catch (e: HttpException) {
            // Stop retrying on auth failures; retry transient server errors.
            if (e.code() in 400..499) Result.failure() else Result.retry()
        } catch (_: IOException) {
            Result.retry()
        } catch (_: Throwable) {
            Result.failure()
        }
    }
}
