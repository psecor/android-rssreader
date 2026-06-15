package net.secorp.rssreader

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import net.secorp.rssreader.data.sync.SyncScheduler

@HiltAndroidApp
class RssApp : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var syncScheduler: SyncScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    /**
     * Coil's default in-memory cache is sized as a fraction of available
     * RAM (often well over 100MB on modern phones). With long feed lists
     * scrolling many thumbnails, that turns into runaway pressure. Cap it
     * explicitly so the cache doesn't compete with the rest of the app.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizeBytes(32 * 1024 * 1024)
                    .build()
            }
            .build()

    override fun onCreate() {
        super.onCreate()
        // Periodic background sync is intentionally NOT scheduled in P2:
        // a runaway sync caused a foldable touch-driver hang during testing,
        // and until P3's delta sync lands (small, since=<cursor>-bounded
        // refreshes) we want every sync to be user-initiated. Sync still
        // runs once on successful sign-in via SyncScheduler.enqueueOneShot,
        // and the Refresh button in FeedListScreen triggers it on demand.
        syncScheduler.cancelAll()
    }
}
