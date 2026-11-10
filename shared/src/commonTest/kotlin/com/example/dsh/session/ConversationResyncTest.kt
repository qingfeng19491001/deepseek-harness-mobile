package com.example.dsh.session

import com.example.dsh.dsh.DshMessage
import com.example.dsh.dsh.DshMessageRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ConversationResyncTest {
    @Test
    fun acceptedLocalSendIsNotRestartedWhenHostReportsRunning() {
        val plan = ConversationCoordinator.planResync(
            isRemote = true,
            requestedSessionId = "s1",
            activeSessionId = "s1",
            reason = "host-session-running",
            sessionRunning = true,
            streaming = true,
            stopVisible = true,
            rootId = "assistant-3",
        )
        assertEquals(ResyncPlan.KeepAcceptedLocalSend, plan)
    }

    @Test
    fun idleHostFinishesTheLocalTurnWithoutOpeningANewConnection() {
        val plan = ConversationCoordinator.planResync(
            isRemote = true,
            requestedSessionId = "s1",
            activeSessionId = "s1",
            reason = "host-session-idle",
            sessionRunning = false,
            streaming = true,
            stopVisible = false,
            rootId = "",
        )
        assertEquals(ResyncPlan.FinishFromHistory, plan)
    }

    @Test
    fun runningHostResumesTheHistoryTailInsteadOfThePreviousReply() {
        val previous = message("assistant-1", "old")
        val current = message("assistant-2", "new")
        val plan = ConversationCoordinator.planHistoryResume(
            messages = listOf(message("user-1", "ask"), previous, message("user-2", "next"), current),
            anchorAssistantId = "assistant-1",
            cursor = StreamCursor(rootId = "assistant-2"),
        )
        val rebound = assertIs<HistoryResume.Rebound>(plan)
        assertEquals("assistant-2", rebound.message.id)
        assertEquals("assistant-2", rebound.cursor.rootId)
    }

    @Test
    fun anchorTailIsNotResumed() {
        val previous = message("assistant-1", "old")
        val plan = ConversationCoordinator.planHistoryResume(
            messages = listOf(message("user-1", "ask"), previous),
            anchorAssistantId = "assistant-1",
            cursor = StreamCursor(rootId = "assistant-9", content = "partial"),
        )
        val continued = assertIs<HistoryResume.Continue>(plan)
        assertEquals("partial", continued.replay)
        assertNull(continued.cursor.assistantId.takeIf { it.isNotEmpty() })
    }

    private fun message(id: String, content: String) = DshMessage(id, DshMessageRole.ASSISTANT, content).let {
        if (id.startsWith("user")) it.copy(role = DshMessageRole.USER) else it
    }
}
