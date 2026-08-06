package net.secorp.rssreader.ui.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import net.secorp.rssreader.MainActivity
import net.secorp.rssreader.R

/**
 * Posts (or clears) the persistent "N unread" notification whose sole
 * purpose is to make the launcher paint its notification-dot on the app
 * icon. Stock Pixel Launcher never renders a numeric badge on the icon
 * itself; the actual number is one glance away in the notification shade
 * and on the lockscreen.
 *
 * Uses a silent, low-importance channel with badging enabled so the dot
 * appears without ever making a sound or interrupting the user.
 */
@Singleton
class UnreadNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    init {
        ensureChannel()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Unread articles",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows a dot on the app icon when there are unread items."
            setShowBadge(true)
            enableVibration(false)
            setSound(null, null)
        }
        context.getSystemService<NotificationManager>()?.createNotificationChannel(channel)
    }

    fun update(unreadCount: Int) {
        val manager = NotificationManagerCompat.from(context)
        if (unreadCount == 0) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        val title = if (unreadCount == 1) "1 unread article" else "$unreadCount unread articles"
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_unread)
            .setContentTitle(title)
            .setContentText("Tap to open RSS Reader")
            .setNumber(unreadCount)
            .setContentIntent(pendingOpen)
            // Ongoing so users can't swipe the dot away accidentally — it
            // disappears when unread hits 0 (see cancel branch above).
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted (Android 13+). Silent no-op;
            // MainActivity requests the permission but the user can deny.
            // They can flip it on later via system settings.
        }
    }

    private companion object {
        const val CHANNEL_ID = "unread_count"
        const val NOTIFICATION_ID = 1001
    }
}
