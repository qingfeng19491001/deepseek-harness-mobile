package com.example.dsh.session

import com.example.dsh.dsh.DshDraftImage
import com.example.dsh.dsh.DshMessage
import com.tencent.kuikly.core.pager.Pager
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList

/** 当前会话里发消息会写入的源状态。不保存第二份 activeSessionId。 */
internal class ConversationState(pager: Pager) {
    var messages by pager.observableList<DshMessage>()
    var draft by pager.observable("")
    val draftImages by pager.observableList<DshDraftImage>()
    var streaming by pager.observable(false)
    var attachmentsSending by pager.observable(false)
    var stopButtonVisible by pager.observable(false)
    var streamingAssistantContent by pager.observable("")
    var streamingAssistantId by pager.observable("")
}

/**
 * 本页代次。历史加载、发送和流式回调在写入前核对它。
 * 它不代替连接 generation，也不代替宿主 WebSocket generation。
 */
internal class SessionGeneration {
    private var generation = 0L
    private var disposed = false

    val current: Long get() = generation

    fun dispose() {
        if (disposed) return
        disposed = true
        generation += 1
    }

    fun accepts(token: Long): Boolean = !disposed && token == generation
}
