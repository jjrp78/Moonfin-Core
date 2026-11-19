package org.moonfin.androidtv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
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
                    IptvRecordingWarningPolicy.testWarning()
                } else {
                    fetchUpcomingWarning()
                }
                if (warning != null) {
                    val artwork = warning.artworkUrl?.let(::fetchArtwork)
                    Handler(Looper.getMainLooper()).post {
                        try {
                            if (!showOverlay(context, warning, artwork)) {
                                showToast(context, warning)
                                showNotification(context, warning, artwork)
                            }
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

    private fun fetchArtwork(url: String): Bitmap? = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection)
        try {
            connection.connectTimeout = NETWORK_TIMEOUT_MILLIS
            connection.readTimeout = NETWORK_TIMEOUT_MILLIS
            connection.requestMethod = "GET"
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            connection.inputStream.use(BitmapFactory::decodeStream)
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private fun showOverlay(context: Context, warning: IptvRecordingWarning, artwork: Bitmap?): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !Settings.canDrawOverlays(context)) return false
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(14), dp(24), dp(14))
            background = GradientDrawable().apply {
                setColor(Color.argb(238, 20, 25, 32))
                cornerRadius = dp(18).toFloat()
                setStroke(dp(2), Color.rgb(60, 187, 230))
            }
        }
        if (artwork != null) {
            container.addView(ImageView(context).apply {
                setImageBitmap(artwork)
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = GradientDrawable().apply { cornerRadius = dp(10).toFloat() }
            }, LinearLayout.LayoutParams(dp(176), dp(100)).apply { marginEnd = dp(18) })
        }
        container.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = warning.title
                setTextColor(Color.WHITE)
                textSize = 22f
                setTypeface(typeface, 1)
            })
            addView(TextView(context).apply {
                text = warning.detail
                setTextColor(Color.rgb(210, 220, 230))
                textSize = 17f
                maxLines = 2
            })
        })
        val windowManager = context.getSystemService(WindowManager::class.java)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(80)
        }
        return try {
            windowManager.addView(container, params)
            Handler(Looper.getMainLooper()).postDelayed({
                runCatching { windowManager.removeView(container) }
            }, warning.timeoutMillis)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Could not show IPTV recording overlay", e)
            false
        }
    }

    private fun showToast(context: Context, warning: IptvRecordingWarning) {
        Toast.makeText(
            context.applicationContext,
            "${warning.title}\n${warning.detail}",
            Toast.LENGTH_LONG,
        ).show()
    }

    private fun showNotification(context: Context, warning: IptvRecordingWarning, artwork: Bitmap?) {
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
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setOngoing(false)
            .setShowWhen(false)
            .setSound(null)

        if (artwork != null) {
            builder
                .setLargeIcon(artwork)
                .setStyle(Notification.BigPictureStyle().bigPicture(artwork).bigLargeIcon(null as Bitmap?))
        } else {
            builder.setStyle(Notification.BigTextStyle().bigText(warning.detail))
        }

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
