package net.secorp.rssreader.data.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks the high-water mark for delta sync. Stored as ISO-8601 strings so
 * the timeline is human-readable in adb dumpsys and across version bumps.
 * Not encrypted — it's a timestamp, not a secret.
 */
@Singleton
class SyncStateStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("sync_state", Context.MODE_PRIVATE)

    fun getLastSyncedAt(): Instant? =
        prefs.getString(KEY_LAST_SYNCED_AT, null)?.let(Instant::parse)

    fun setLastSyncedAt(value: Instant) {
        prefs.edit { putString(KEY_LAST_SYNCED_AT, value.toString()) }
    }

    fun clear() {
        prefs.edit { clear() }
    }

    private companion object {
        const val KEY_LAST_SYNCED_AT = "last_synced_at"
    }
}
