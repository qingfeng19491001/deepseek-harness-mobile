package com.example.dsh.session

import com.example.dsh.dsh.DshDraftImage
import com.example.dsh.dsh.DshDraftImageStatus
import com.example.dsh.dsh.DshMessage
import com.example.dsh.dsh.DshMessageRole
import com.example.dsh.dsh.DshSession
import com.example.dsh.dsh.dshAttachmentErrorLabel
import com.tencent.kuikly.core.pager.Pager

/** 页面级会话入口。把协调器的动作落到状态上，并隔离本页的迟到回调。 */
internal class HomeSession(pager: Pager) {
    val home = HomeState(pager)
    val conversation = ConversationState(pager)
    private val generation = SessionGeneration()

    val generationToken: Long get() = generation.current

    fun accepts(token: Long): Boolean = generation.accepts(token)

    fun dispose() = generation.dispose()

    fun apply(action: ConversationAction) {
        if (!accepts(generation.current)) return
        when (action) {
            is ConversationAction.AppendMessage -> conversation.messages.add(action.message)
            is ConversationAction.BeginOutgoingTurn -> beginOutgoingTurn(action)
            is ConversationAction.MarkDraftImagesFailed -> markDraftImagesFailed(action)
            is ConversationAction.RestoreUnacceptedSend -> restoreUnacceptedSend(action)
            is ConversationAction.AddBlankSession -> {
                home.sessions.add(DshSession(action.sessionId, "新会话", "Host", "", blank = true))
                home.activeSessionId = action.sessionId
            }
            is ConversationAction.UpsertMessage -> upsert(action.message)
            is ConversationAction.RemoveMessage -> removeMessage(action.messageId)
            is ConversationAction.EnsureStreamingRow -> ensureStreamingRow(action.messageId)
            is ConversationAction.CloseStreamingTurn -> {
                conversation.streaming = false
                conversation.stopButtonVisible = false
                conversation.streamingAssistantContent = action.content
            }
            ConversationAction.ReleaseStreamingTurn -> {
                conversation.streaming = false
                conversation.stopButtonVisible = false
                conversation.streamingAssistantContent = ""
                conversation.streamingAssistantId = ""
            }
        }
    }

    private fun upsert(message: DshMessage) {
        val index = conversation.messages.indexOfFirst { it.id == message.id }
        if (index >= 0) conversation.messages[index] = message
        else conversation.messages.add(message)
    }

    private fun removeMessage(messageId: String) {
        val index = conversation.messages.indexOfFirst { it.id == messageId }
        if (index >= 0) conversation.messages.removeAt(index)
    }

    private fun ensureStreamingRow(messageId: String) {
        if (messageId.isEmpty() || conversation.messages.any { it.id == messageId }) return
        conversation.messages.add(DshMessage(messageId, DshMessageRole.ASSISTANT, "", streaming = true))
    }

    private fun beginOutgoingTurn(action: ConversationAction.BeginOutgoingTurn) {
        val remaining = conversation.draftImages.filter { it.localId !in action.acceptedLocalIds }
        conversation.draftImages.clear()
        remaining.forEach { conversation.draftImages.add(it) }
        conversation.messages.add(action.user)
        conversation.draft = ""
        conversation.streamingAssistantId = ""
        conversation.streamingAssistantContent = ""
        conversation.streaming = true
        conversation.attachmentsSending = action.attachmentsSending
        conversation.stopButtonVisible = true
    }

    private fun markDraftImagesFailed(action: ConversationAction.MarkDraftImagesFailed) {
        val label = action.message.ifEmpty { dshAttachmentErrorLabel(action.code) }
        for (index in conversation.draftImages.indices) {
            val image = conversation.draftImages[index]
            if (image.localId in action.localIds) {
                conversation.draftImages[index] = image.copy(
                    status = DshDraftImageStatus.FAILED,
                    errorCode = action.code,
                    errorMessage = label,
                )
            }
        }
    }

    private fun restoreUnacceptedSend(action: ConversationAction.RestoreUnacceptedSend) {
        val index = conversation.messages.indexOfFirst { it.id == action.userMessageId }
        if (index >= 0) conversation.messages.removeAt(index)
        if (action.prompt.isNotEmpty()) conversation.draft = action.prompt
        action.images.forEach { image ->
            if (conversation.draftImages.none { it.localId == image.localId }) {
                conversation.draftImages.add(
                    image.copy(
                        status = DshDraftImageStatus.FAILED,
                        errorMessage = action.error,
                    ),
                )
            }
        }
    }
}
