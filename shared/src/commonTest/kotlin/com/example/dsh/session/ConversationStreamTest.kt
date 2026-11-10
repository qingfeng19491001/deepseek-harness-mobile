package com.example.dsh.session

import com.example.dsh.dsh.DshMessageRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConversationStreamTest {
    private val live = StreamCursor(streaming = true, rootId = "assistant-1", assistantId = "assistant-1")

    @Test
    fun deltaForAnotherRootIsIgnored() {
        assertNull(ConversationCoordinator.onAssistantDelta(live, "assistant-2", "a"))
    }

    @Test
    fun firstDeltaStaysOnTheSameAssistantId() {
        val decision = ConversationCoordinator.onAssistantDelta(live, "assistant-1", "hi")
        assertEquals("hi", decision?.cursor?.pending)
        assertEquals("assistant-1", decision?.cursor?.assistantId)
        assertTrue(decision?.scheduleFlush == true)
        val flushed = ConversationCoordinator.flush(decision!!.cursor)
        assertEquals("hi", flushed?.cursor?.content)
        assertEquals("", flushed?.cursor?.pending)
    }

    @Test
    fun settleUpdatesOnlyTheLiveAssistantId() {
        val settled = ConversationCoordinator.settle(live.copy(content = "hello"), DshMessageRole.ASSISTANT, "")
        val message = assertIs<SettledStream.Message>(settled)
        assertEquals("assistant-1", message.message.id)
        assertEquals("hello", message.message.content)
        assertFalse(message.message.streaming)
        assertFalse(message.cursor.streaming)
    }

    @Test
    fun stopMarksTheLiveContentWithoutChangingItsId() {
        val content = ConversationCoordinator.stoppedContent("partial")
        val settled = ConversationCoordinator.settle(live.copy(content = "partial"), DshMessageRole.ASSISTANT, content)
        val message = assertIs<SettledStream.Message>(settled)
        assertEquals("assistant-1", message.message.id)
        assertEquals("partial\n\n*已停止*", message.message.content)
    }

    @Test
    fun wrongReasoningIdDoesNotProduceAMessage() {
        val cursor = live.copy(reasoningId = "assistant-1-reasoning")
        assertNull(ConversationCoordinator.onReasoningDelta(cursor, "other", "x", null))
    }
}
