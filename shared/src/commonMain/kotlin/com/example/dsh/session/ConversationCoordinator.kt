package com.example.dsh.session

import com.example.dsh.dsh.DshDraftImage
import com.example.dsh.dsh.DshDraftImageStatus
import com.example.dsh.dsh.DshImageLimits
import com.example.dsh.dsh.DshMessage
import com.example.dsh.dsh.DshMessageRole
import com.example.dsh.dsh.dshAdmitDraftImages
import com.example.dsh.dsh.dshHistoryTailToResume

internal enum class SendChannel {
    Missing,
    Syncing,
    Ready,
}

internal data class SendRequest(
    val prompt: String,
    val draftImages: List<DshDraftImage>,
    val retryLocalId: String?,
    val streaming: Boolean,
    val attachmentsSending: Boolean,
    val channel: SendChannel,
    val syncingLabel: String,
    val sessionsEmpty: Boolean,
    val activeSessionId: String,
    val messageCount: Int,
    val limits: DshImageLimits,
    val limitsKnown: Boolean,
)

internal sealed class SendPlan {
    data object Ignored : SendPlan()

    data class NotReady(
        val label: String,
        val message: DshMessage? = null,
    ) : SendPlan()

    data class ImagesRejected(
        val localIds: Set<String>,
        val code: String,
        val message: String,
    ) : SendPlan()

    data object NeedSession : SendPlan()

    data class Ready(
        val sessionId: String,
        val prompt: String,
        val images: List<DshDraftImage>,
        val user: DshMessage,
        val assistantId: String,
        val reasoningId: String,
    ) : SendPlan()
}

internal sealed class ConversationAction {
    data class AppendMessage(val message: DshMessage) : ConversationAction()
    data class BeginOutgoingTurn(
        val user: DshMessage,
        val acceptedLocalIds: Set<String>,
        val attachmentsSending: Boolean,
    ) : ConversationAction()
    data class MarkDraftImagesFailed(
        val localIds: Set<String>,
        val code: String,
        val message: String,
    ) : ConversationAction()
    data class RestoreUnacceptedSend(
        val userMessageId: String,
        val prompt: String,
        val images: List<DshDraftImage>,
        val error: String,
    ) : ConversationAction()
    data class AddBlankSession(val sessionId: String) : ConversationAction()
    data class UpsertMessage(val message: DshMessage) : ConversationAction()
    data class RemoveMessage(val messageId: String) : ConversationAction()
    data class EnsureStreamingRow(val messageId: String) : ConversationAction()
    data class CloseStreamingTurn(val content: String) : ConversationAction()
    data object ReleaseStreamingTurn : ConversationAction()
}

internal data class StreamCursor(
    val streaming: Boolean = false,
    val rootId: String = "",
    val segment: Int = 0,
    val assistantId: String = "",
    val content: String = "",
    val pending: String = "",
    val reasoningId: String = "",
    val reasoningContent: String = "",
    val flushScheduled: Boolean = false,
)

internal data class AssistantDelta(
    val cursor: StreamCursor,
    val firstPaint: Boolean,
    val scheduleFlush: Boolean,
    val insertRow: Boolean,
)

internal data class ReasoningDelta(
    val cursor: StreamCursor,
    val message: DshMessage,
)

internal data class FlushedText(val cursor: StreamCursor)

internal data class SegmentReady(val cursor: StreamCursor, val insertRow: Boolean)

internal sealed class SplitText {
    data object Ignore : SplitText()
    data class Advance(val cursor: StreamCursor) : SplitText()
    data class Remove(val cursor: StreamCursor, val messageId: String) : SplitText()
    data class Close(val cursor: StreamCursor, val message: DshMessage) : SplitText()
}

internal sealed class SettledStream {
    data class Release(val cursor: StreamCursor) : SettledStream()
    data class Message(val cursor: StreamCursor, val message: DshMessage) : SettledStream()
}

internal enum class ResyncPlan {
    Ignore,
    KeepAcceptedLocalSend,
    ResumeFromHistory,
    FinishFromHistory,
}

internal sealed class HistoryResume {
    data class Rebound(val cursor: StreamCursor, val message: DshMessage) : HistoryResume()
    data class Continue(val cursor: StreamCursor, val replay: String?) : HistoryResume()
}

internal sealed class HistoryFinish {
    data class Rebound(val cursor: StreamCursor) : HistoryFinish()
    data object Release : HistoryFinish()
}

internal sealed class SessionSwitch {
    data object Same : SessionSwitch()
    data object Switch : SessionSwitch()
}

/** 发消息的决定。不接触 Pager、observable 或页面。 */
internal object ConversationCoordinator {
    fun planSend(request: SendRequest): SendPlan {
        val prompt = request.prompt.trim()
        val sending = imagesToSend(request.draftImages, request.retryLocalId)
        if ((prompt.isEmpty() && sending.isEmpty()) || request.streaming || request.attachmentsSending) {
            return SendPlan.Ignored
        }
        when (request.channel) {
            SendChannel.Missing -> return SendPlan.NotReady(
                label = "本地内核尚未连接",
                message = DshMessage(
                    "send-engine-error-${request.messageCount}",
                    DshMessageRole.ERROR,
                    "本地 Harness 尚未连接，请稍候再试。",
                ),
            )
            SendChannel.Syncing -> return SendPlan.NotReady(request.syncingLabel)
            SendChannel.Ready -> Unit
        }
        val admitted = dshAdmitDraftImages(emptyList(), sending, request.limits, request.limitsKnown)
        if (sending.isNotEmpty() && admitted.accepted.isEmpty()) {
            return SendPlan.ImagesRejected(
                localIds = sending.map { it.localId }.toSet(),
                code = admitted.rejectedCode,
                message = admitted.rejectedMessage,
            )
        }
        if (request.sessionsEmpty) return SendPlan.NeedSession
        val images = admitted.accepted
        val user = DshMessage(
            "user-${request.messageCount}",
            DshMessageRole.USER,
            prompt,
            attachments = images.map { it.toRef() },
        )
        val assistantId = "assistant-${request.messageCount}"
        return SendPlan.Ready(
            sessionId = request.activeSessionId,
            prompt = prompt,
            images = images,
            user = user,
            assistantId = assistantId,
            reasoningId = "$assistantId-reasoning",
        )
    }

    fun sessionCreateFailed(messageCount: Int, error: String) = DshMessage(
        "send-session-error-$messageCount",
        DshMessageRole.ERROR,
        "无法创建会话：$error",
    )

    fun onAssistantDelta(cursor: StreamCursor, id: String, delta: String): AssistantDelta? {
        if (delta.isEmpty() || !cursor.streaming || cursor.rootId != id) return null
        val ensured = ensureSegment(cursor)
        val queued = ensured.cursor.copy(pending = ensured.cursor.pending + delta)
        val firstPaint = queued.content.isEmpty()
        val scheduleFlush = !(queued.flushScheduled && !firstPaint)
        return AssistantDelta(
            cursor = if (scheduleFlush) queued.copy(flushScheduled = true) else queued,
            firstPaint = firstPaint,
            scheduleFlush = scheduleFlush,
            insertRow = ensured.insertRow,
        )
    }

    fun onReasoningDelta(cursor: StreamCursor, id: String, delta: String, existing: DshMessage?): ReasoningDelta? {
        if (delta.isEmpty() || cursor.reasoningId != id) return null
        val content = cursor.reasoningContent + delta
        val message = (existing ?: DshMessage(
            id,
            DshMessageRole.ASSISTANT,
            "",
            streaming = true,
            isReasoning = true,
        )).copy(content = content, streaming = true, isReasoning = true)
        return ReasoningDelta(cursor.copy(reasoningContent = content), message)
    }

    fun flush(cursor: StreamCursor): FlushedText? {
        if (cursor.assistantId.isEmpty() || cursor.pending.isEmpty()) return null
        return FlushedText(cursor.copy(content = cursor.content + cursor.pending, pending = "", flushScheduled = false))
    }

    fun ensureSegment(cursor: StreamCursor): SegmentReady {
        if (cursor.assistantId.isNotEmpty() || cursor.rootId.isEmpty()) return SegmentReady(cursor, insertRow = false)
        val id = if (cursor.segment == 0) cursor.rootId else "${cursor.rootId}-segment-${cursor.segment}"
        val insertRow = cursor.content.isNotEmpty() || cursor.pending.isNotEmpty()
        return SegmentReady(cursor.copy(assistantId = id), insertRow)
    }

    fun splitBeforeTool(cursor: StreamCursor, row: DshMessage?): SplitText {
        if (!cursor.streaming || cursor.rootId.isEmpty()) return SplitText.Ignore
        val text = row?.content?.ifEmpty { cursor.content }.orEmpty()
        val cleared = cursor.copy(
            assistantId = "",
            content = "",
            pending = "",
            flushScheduled = false,
            segment = cursor.segment + 1,
        )
        return when {
            row == null -> SplitText.Advance(cleared)
            text.isEmpty() -> SplitText.Remove(cleared, row.id)
            else -> SplitText.Close(cleared, row.copy(content = text, streaming = false))
        }
    }

    fun settle(cursor: StreamCursor, role: DshMessageRole, requestedContent: String): SettledStream {
        if (cursor.assistantId.isEmpty()) return SettledStream.Release(releasedCursor())
        val content = requestedContent.ifEmpty { cursor.content }
        return SettledStream.Message(
            cursor = cursor.copy(
                streaming = false,
                content = content,
                pending = "",
                reasoningId = "",
                reasoningContent = "",
            ),
            message = DshMessage(cursor.assistantId, role, content, streaming = false),
        )
    }

    fun stoppedContent(content: String): String = content + "\n\n*已停止*"

    fun planResync(
        isRemote: Boolean,
        requestedSessionId: String,
        activeSessionId: String,
        reason: String,
        sessionRunning: Boolean,
        streaming: Boolean,
        stopVisible: Boolean,
        rootId: String,
    ): ResyncPlan {
        if (!isRemote || requestedSessionId != activeSessionId) return ResyncPlan.Ignore
        if (reason == "host-session-running" && streaming && rootId.isNotEmpty()) {
            return ResyncPlan.KeepAcceptedLocalSend
        }
        return if (sessionRunning) ResyncPlan.ResumeFromHistory else ResyncPlan.FinishFromHistory
    }

    fun finishReplacesTimeline(streaming: Boolean, stopVisible: Boolean): Boolean = streaming || stopVisible

    fun planHistoryResume(
        messages: List<DshMessage>,
        anchorAssistantId: String,
        cursor: StreamCursor,
    ): HistoryResume {
        val live = dshHistoryTailToResume(messages, anchorAssistantId)
        if (live != null) {
            return HistoryResume.Rebound(
                cursor.copy(
                    streaming = true,
                    assistantId = live.id,
                    rootId = live.id,
                    segment = 0,
                    content = live.content,
                ),
                live.copy(streaming = true),
            )
        }
        val rootId = cursor.rootId.ifEmpty { "assistant-adopted-${messages.size}" }
        val liveStillPresent = cursor.assistantId.isNotEmpty() && messages.any { it.id == cursor.assistantId }
        if (liveStillPresent) {
            return HistoryResume.Continue(cursor.copy(streaming = true, rootId = rootId), replay = null)
        }
        val kept = cursor.content + cursor.pending
        return HistoryResume.Continue(
            cursor.copy(
                streaming = true,
                rootId = rootId,
                assistantId = "",
                segment = 0,
                content = "",
                pending = "",
            ),
            replay = kept.takeIf { it.isNotEmpty() },
        )
    }

    fun planHistoryFinish(
        messages: List<DshMessage>,
        anchorAssistantId: String,
        cursor: StreamCursor,
    ): HistoryFinish {
        val live = dshHistoryTailToResume(messages, anchorAssistantId) ?: return HistoryFinish.Release
        return HistoryFinish.Rebound(
            cursor.copy(
                assistantId = live.id,
                rootId = live.id,
                segment = 0,
                content = live.content,
            ),
        )
    }

    fun planSessionSwitch(activeSessionId: String, requestedSessionId: String): SessionSwitch =
        if (activeSessionId == requestedSessionId) SessionSwitch.Same else SessionSwitch.Switch

    fun releasedCursor(): StreamCursor = StreamCursor()

    private fun imagesToSend(images: List<DshDraftImage>, retryLocalId: String?): List<DshDraftImage> {
        if (retryLocalId != null) return images.filter { it.localId == retryLocalId }
        val ready = images.filter { it.status != DshDraftImageStatus.FAILED }
        return ready.ifEmpty { images }
    }
}
