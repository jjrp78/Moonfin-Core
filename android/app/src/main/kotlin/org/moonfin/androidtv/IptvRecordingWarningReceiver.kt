package org.moonfin.androidtv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.URL

/**
 * Receives a protected shell broadcast from Home Assistant, verifies the
 * framework's safe upcoming-recording response, then posts one quiet, short
 * Android TV heads-up notification without changing the foreground app.
 */
class IptvRecordingWarningReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SHOW_WARNING && intent.action != ACTION_SHOW_TEST_WARNING) return

        val pendingResult = goAsync()
        Thread {
            try {
                val warning = if (intent.action == ACTION_SHOW_TEST_WARNING) {
                    IptvRecordingWarning(
                        title = "Recording starts in 2 min",
                        detail = "SHIELD notification test",
                    )
                } else {
                    fetchUpcomingWarning()
                }
                if (warning != null) {
                    Handler(Looper.getMainLooper()).post {
                        try {
                            showToast(context, warning)
                            showNotification(context, warning)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                    return@Thread
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not show IPTV recording warning", e)
            }
            pendingResult.finish()
        }.apply {
            name = "iptv-recording-warning"
            start()
        }
    }

    private fun fetchUpcomingWarning(): IptvRecordingWarning? {
        val connection = (URL(IptvRecordingWarningPolicy.UPCOMING_ENDPOINT).openConnection() as HttpURLConnection)
        try {
            connection.connectTimeout = NETWORK_TIMEOUT_MILLIS
            connection.readTimeout = NETWORK_TIMEOUT_MILLIS
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            return connection.inputStream.bufferedReader().use { reader ->
                IptvRecordingWarningPolicy.fromUpcomingJson(reader.readText())
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun showToast(context: Context, warning: IptvRecordingWarning) {
        Toast.makeText(
            context.applicationContext,
            "${warning.title}\n${warning.detail}",
            Toast.LENGTH_LONG,
        ).show()
    }

    private fun showNotification(context: Context, warning: IptvRecordingWarning) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) {
            Log.w(TAG, "Moonfin notifications are disabled")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Recording warnings",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Brief warnings before IPTV recordings begin"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        builder
            .setSmallIcon(context.applicationInfo.icon)
            .setContentTitle(warning.title)
            .setContentText(warning.detail)
            .setStyle(Notification.BigTextStyle().bigText(warning.detail))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setOngoing(false)
            .setShowWhen(false)
            .setSound(null)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setTimeoutAfter(warning.timeoutMillis)
        } else {
            @Suppress("DEPRECATION")
            builder.setPriority(Notification.PRIORITY_HIGH)
        }

        manager.notify(NOTIFICATION_ID, builder.build())
    }

    companion object {
        const val ACTION_SHOW_WARNING =
            "org.moonfin.androidtv.subtitlefix.action.SHOW_IPTV_RECORDING_WARNING"
        const val ACTION_SHOW_TEST_WARNING =
            "org.moonfin.androidtv.subtitlefix.action.SHOW_IPTV_RECORDING_WARNING_TEST"

        private const val TAG = "IptvRecordingWarning"
        private const val CHANNEL_ID = "iptv_recording_warnings"
        private const val NOTIFICATION_ID = 20_261_118
        private const val NETWORK_TIMEOUT_MILLIS = 3_000
    }
}
