package net.secorp.rssreader.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sticky UI toggles that survive process death and reinstalls. Plain
 * SharedPreferences — these are not secrets.
 */
@Singleton
class UiPreferencesStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)

    var onlyUnread: Boolean
        get() = prefs.getBoolean(KEY_ONLY_UNREAD, false)
        set(value) { prefs.edit { putBoolean(KEY_ONLY_UNREAD, value) } }

    private companion object {
        const val KEY_ONLY_UNREAD = "only_unread"
    }
}
