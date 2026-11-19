package org.moonfin.androidtv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IptvRecordingWarningPolicyTest {
    @Test
    fun `turns a verified upcoming recording into a concise silent warning`() {
        val warning = IptvRecordingWarningPolicy.fromUpcomingJson(
            """
            {
              "upcoming": true,
              "seconds_until": 119,
              "channel": "TNT Sports AR",
              "title": "Boca Juniors vs. Banfield",
              "poster_url": "http://192.168.1.4:5000/artwork/boca.jpg"
            }
            """.trimIndent(),
        )

        requireNotNull(warning)
        assertEquals("Recording starts in 2 min", warning.title)
        assertEquals("TNT Sports AR · Boca Juniors vs. Banfield", warning.detail)
        assertEquals(15_000L, warning.timeoutMillis)
        assertTrue(warning.silent)
        assertEquals("http://192.168.1.4:5000/artwork/boca.jpg", warning.artworkUrl)
    }

    @Test
    fun `uses a trusted IPTV poster for the image notification test`() {
        val warning = IptvRecordingWarningPolicy.testWarning()

        assertEquals("Recording starts in 2 min", warning.title)
        assertEquals("SHIELD notification image test", warning.detail)
        assertEquals("http://192.168.1.4:5000/artwork/boca.jpg", warning.artworkUrl)
    }

    @Test
    fun `rejects inactive malformed and expired responses`() {
        assertNull(IptvRecordingWarningPolicy.fromUpcomingJson("{\"upcoming\":false}"))
        assertNull(IptvRecordingWarningPolicy.fromUpcomingJson("not json"))
        assertNull(
            IptvRecordingWarningPolicy.fromUpcomingJson(
                """{"upcoming":true,"seconds_until":121,"channel":"A","title":"B"}""",
            ),
        )
        assertNull(
            IptvRecordingWarningPolicy.fromUpcomingJson(
                """{"upcoming":true,"seconds_until":30,"channel":"","title":""}""",
            ),
        )
    }
}
