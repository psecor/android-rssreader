package net.secorp.rssreader

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import net.secorp.rssreader.data.repo.RssRepository
import net.secorp.rssreader.data.sync.SyncScheduler
import net.secorp.rssreader.ui.notify.UnreadNotifier

@HiltAndroidApp
class RssApp : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var syncScheduler: SyncScheduler
    @Inject lateinit var rssRepository: RssRepository
    @Inject lateinit var unreadNotifier: UnreadNotifier

    // App-scoped coroutine scope for observers that should live for the whole
    // process. GC'd along with everything else when the process dies.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
        observeUnreadForBadge()
    }

    /**
     * Drives the icon-dot notification. Room's Flow can emit many times per
     * second during a bulk mark-all-read or a full re-sync (thousands of
     * per-row updates), so we distinctUntilChanged + debounce to coalesce
     * bursts into a single notification update at the tail.
     */
    @OptIn(FlowPreview::class)
    private fun observeUnreadForBadge() {
        appScope.launch {
            rssRepository.observeTotalUnread()
                .distinctUntilChanged()
                .debounce(500)
                .collect { unreadNotifier.update(it) }
        }
    }
}
