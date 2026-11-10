package com.example.dsh.session

import com.example.dsh.dsh.DSH_IMAGE_LIMITS_FALLBACK
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ConversationSendTest {
    @Test
    fun emptyStreamingOrBusySendIsIgnored() {
        assertIs<SendPlan.Ignored>(coordinator.planSend(request(prompt = "  ")))
        assertIs<SendPlan.Ignored>(coordinator.planSend(request(streaming = true)))
        assertIs<SendPlan.Ignored>(coordinator.planSend(request(attachmentsSending = true)))
    }

    @Test
    fun missingChannelAppendsTheExistingErrorId() {
        val plan = coordinator.planSend(request(channel = SendChannel.Missing, messageCount = 4))
        val notReady = assertIs<SendPlan.NotReady>(plan)
        assertEquals("本地内核尚未连接", notReady.label)
        assertEquals("send-engine-error-4", notReady.message?.id)
    }

    @Test
    fun readySendKeepsTheCurrentMessageIds() {
        val plan = assertIs<SendPlan.Ready>(coordinator.planSend(request(messageCount = 2, sessionId = "s9")))
        assertEquals("s9", plan.sessionId)
        assertEquals("user-2", plan.user.id)
        assertEquals("assistant-2", plan.assistantId)
        assertEquals("assistant-2-reasoning", plan.reasoningId)
        assertEquals("hi", plan.user.content)
    }

    @Test
    fun emptySessionListAsksForASessionFirst() {
        assertIs<SendPlan.NeedSession>(coordinator.planSend(request(sessionsEmpty = true)))
    }

    @Test
    fun disposedPageRejectsTheCapturedSendToken() {
        val generation = SessionGeneration()
        val token = generation.current
        generation.dispose()
        generation.dispose()
        assertFalse(generation.accepts(token))
        val fresh = SessionGeneration()
        assertTrue(fresh.accepts(fresh.current))
    }

    private val coordinator = ConversationCoordinator

    private fun request(
        prompt: String = "hi",
        streaming: Boolean = false,
        attachmentsSending: Boolean = false,
        channel: SendChannel = SendChannel.Ready,
        sessionsEmpty: Boolean = false,
        messageCount: Int = 0,
        sessionId: String = "s1",
    ) = SendRequest(
        prompt = prompt,
        draftImages = emptyList(),
        retryLocalId = null,
        streaming = streaming,
        attachmentsSending = attachmentsSending,
        channel = channel,
        syncingLabel = "同步中",
        sessionsEmpty = sessionsEmpty,
        activeSessionId = sessionId,
        messageCount = messageCount,
        limits = DSH_IMAGE_LIMITS_FALLBACK,
        limitsKnown = true,
    )
}
