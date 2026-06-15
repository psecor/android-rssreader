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
        // Re-enabled in P3: each periodic run now uses since=<lastSyncedAt>
        // so the worker fetches a small delta, not the full item set.
        // KEEP policy means an existing schedule from a prior install isn't
        // reset every app launch.
        syncScheduler.schedulePeriodic()
    }
}
