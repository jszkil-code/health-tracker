package dev.healthtracker.app.data.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogSanitizerTest {
    @Test fun redactsJwt() {
        val out = LogSanitizer.sanitize("token eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.abc_DEF-123 expired")
        assertEquals("token [jwt] expired", out)
    }

    @Test fun redactsBearerHeader() {
        assertEquals("Authorization: Bearer [redacted]", LogSanitizer.sanitize("Authorization: Bearer abc.def-ghi"))
    }

    @Test fun redactsKeys() {
        val out = LogSanitizer.sanitize("key=sb_publishable_AbC123 other sk-ant-api03-xyz")
        assertFalse(out.contains("AbC123"))
        assertFalse(out.contains("xyz"))
    }

    @Test fun redactsQueryStringSecrets() {
        assertEquals(
            "GET /cb?code=[redacted]&state=1",
            LogSanitizer.sanitize("GET /cb?code=s3cr3t&state=1"),
        )
    }

    @Test fun redactsEmail() {
        assertEquals("Sign-in for [email] failed", LogSanitizer.sanitize("Sign-in for someone@example.com failed"))
    }

    @Test fun keepsOrdinaryMessages() {
        val msg = "Synced 42 rows from health_connect in 812 ms"
        assertEquals(msg, LogSanitizer.sanitize(msg))
    }

    @Test fun truncatesLongMessages() {
        val out = LogSanitizer.sanitize("x".repeat(2000))
        assertTrue(out.length <= 501)
    }
}
