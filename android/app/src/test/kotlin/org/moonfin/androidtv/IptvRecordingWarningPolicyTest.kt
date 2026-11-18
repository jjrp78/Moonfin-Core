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
              "title": "Boca Juniors vs. Banfield"
            }
            """.trimIndent(),
        )

        requireNotNull(warning)
        assertEquals("Recording starts in 2 min", warning.title)
        assertEquals("TNT Sports AR · Boca Juniors vs. Banfield", warning.detail)
        assertEquals(8_000L, warning.timeoutMillis)
        assertTrue(warning.silent)
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
