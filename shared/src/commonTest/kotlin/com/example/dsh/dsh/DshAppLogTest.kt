package com.example.dsh.dsh

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DshAppLogTest {
    @AfterTest
    fun tearDown() {
        DshAppLog.resetForTests()
    }

    @Test
    fun redactsSecretsAndOmitsBase64() {
        val text = dshSanitizeLogText(
            "Authorization: Bearer secret-token-value clientToken=abc123 hostToken=zzz " +
                "preview='hello world' data:image/png;base64,${"A".repeat(96)}",
        )
        assertFalse(text.contains("secret-token-value"))
        assertFalse(text.contains("abc123"))
        assertFalse(text.contains("zzz"))
        assertFalse(text.contains("hello world"))
        assertTrue(text.contains("<redacted>"))
        assertTrue(text.contains("base64-omitted") || text.contains("<base64-omitted>"))
    }

    @Test
    fun chunkLinesStoreMetadataOnly() {
        val classified = dshClassifyLogLine(
            "assistant/chunk session=sess-1 seq=9 chars=120 preview='DROP TABLE users'",
        )
        assertEquals("assistant/chunk", classified.eventType)
        assertEquals("sess-1", classified.sessionId)
        assertEquals(DshLogLevel.DEBUG, classified.level)
        assertFalse(classified.summary.contains("DROP"))
        assertTrue(classified.summary.contains("bytes=120"))
    }

    @Test
    fun ringBufferDropsOldest() {
        repeat(DshAppLog.CAPACITY + 5) { index ->
            DshAppLog.record(DshLogLevel.INFO, "rpc", "n=$index", sessionId = "s")
        }
        val all = DshAppLog.snapshot()
        assertEquals(DshAppLog.CAPACITY, all.size)
        assertTrue(all.first().summary.contains("n=5"))
        assertTrue(all.last().summary.contains("n=${DshAppLog.CAPACITY + 4}"))
    }

    @Test
    fun filtersAndClearLeaveHostHistoryUntouched() {
        DshAppLog.record(DshLogLevel.ERROR, "session.error", "boom", sessionId = "a")
        DshAppLog.record(DshLogLevel.INFO, "tool/call", "Read", sessionId = "a")
        DshAppLog.record(DshLogLevel.INFO, "reconnect", "retry 1", sessionId = "b")
        assertEquals(1, DshAppLog.filtered(DshLogFilter.ERROR, "", "").size)
        assertEquals(1, DshAppLog.filtered(DshLogFilter.TOOL_CALL, "", "").size)
        assertEquals(1, DshAppLog.filtered(DshLogFilter.RECONNECT, "", "").size)
        assertEquals(2, DshAppLog.filtered(DshLogFilter.ALL, "", "a").size)
        DshAppLog.clear()
        assertTrue(DshAppLog.snapshot().isEmpty())
    }

    @Test
    fun exportUsesSanitizedLines() {
        DshAppLog.record(DshLogLevel.INFO, "rpc", "token=super-secret", sessionId = "s1")
        val exported = DshAppLog.exportText(
            DshAppLog.snapshot(),
            connectionMode = "relay",
            appVersion = "1.0",
            device = "test",
        )
        assertTrue(exported.contains("connection=relay"))
        assertFalse(exported.contains("super-secret"))
        assertTrue(exported.contains("session=s1"))
    }
}
