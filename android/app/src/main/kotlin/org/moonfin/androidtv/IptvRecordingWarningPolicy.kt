package org.moonfin.androidtv

import org.json.JSONObject

/** A safe, brief notification for a recording that is about to occupy Live TV. */
data class IptvRecordingWarning(
    val title: String,
    val detail: String,
    val timeoutMillis: Long = DISPLAY_TIMEOUT_MILLIS,
    val silent: Boolean = true,
) {
    companion object {
        const val DISPLAY_TIMEOUT_MILLIS = 8_000L
    }
}

/** Keeps untrusted or stale framework responses from creating a SHIELD warning. */
object IptvRecordingWarningPolicy {
    const val UPCOMING_ENDPOINT =
        "http://192.168.1.4:5000/api/recording/live/upcoming-warning"
    const val WARNING_WINDOW_SECONDS = 120

    fun fromUpcomingJson(body: String): IptvRecordingWarning? =
        runCatching {
            val json = JSONObject(body)
            if (!json.optBoolean("upcoming", false)) return null

            val secondsUntil = json.optLong("seconds_until", -1)
            if (secondsUntil !in 0..WARNING_WINDOW_SECONDS.toLong()) return null

            val channel = json.optString("channel").trim()
            val programme = json.optString("title").trim()
            val detail = listOf(channel, programme)
                .filter { it.isNotEmpty() }
                .joinToString(" · ")
                .take(MAX_DETAIL_LENGTH)
            if (detail.isEmpty()) return null

            IptvRecordingWarning(
                title = "Recording starts in ${minutesLabel(secondsUntil)}",
                detail = detail,
            )
        }.getOrNull()

    private fun minutesLabel(secondsUntil: Long): String =
        if (secondsUntil > 60) "2 min" else "1 min"

    private const val MAX_DETAIL_LENGTH = 180
}
