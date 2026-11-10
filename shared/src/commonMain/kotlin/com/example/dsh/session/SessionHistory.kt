package com.example.dsh.session

import com.example.dsh.dsh.DshMessage
import com.example.dsh.dsh.DshMessageRole
import com.example.dsh.dsh.DshRepository
import com.example.dsh.dsh.DshSessionCacheState
import com.example.dsh.dsh.DshStreamLog
import com.tencent.kuikly.core.reactive.collection.ObservableList

/**
 * 历史加载和重连入口。不启动 SSH / Relay。
 * 历史请求代次仍由页面上的同一个计数器保存，失效规则不变。
 */
internal class SessionHistory(
    private val host: SessionHistoryHost,
) {
    fun loadHistory(sessionId: String, scrollToEndAfterLoad: Boolean = true) {
        host.historyRequestGeneration += 1
        // Show the selected session immediately. The Host history request is
        // remote and can take a moment, so keeping the previous list here
        // makes a session switch look stuck.
        host.messages = host.sessionMessageState(sessionId, scrollToEndAfterLoad)
        host.ensureConversationPanel(sessionId)
        fetchHostHistory(sessionId, scrollToEndAfterLoad)
    }

    fun fetchHostHistory(sessionId: String, scrollToEndAfterLoad: Boolean = true) {
        if (host.isRemoteHost) {
            host.loadSkills(sessionId)
            host.loadWebTimeline(sessionId, scrollToEndAfterLoad)
            return
        }
        val requestGeneration = host.historyRequestGeneration
        val repository = host.repository() ?: return
        repository.loadHistory(sessionId, { loaded ->
            if (requestGeneration != host.historyRequestGeneration || host.activeSessionId != sessionId) return@loadHistory
            host.markMessageReady(sessionId)
            host.markCache(sessionId, DshSessionCacheState.SYNCED)
            host.replaceMessagesIfChanged(loaded)
            host.replaceStoredMessages(sessionId, loaded)
            host.completePendingSessionSelection(sessionId)
            host.realizeSessionAfterData(sessionId, scrollToEndAfterLoad)
        }, { error ->
            if (requestGeneration != host.historyRequestGeneration || host.activeSessionId != sessionId) return@loadHistory
            if (host.messages.isNotEmpty()) {
                if (host.isRemoteHost) {
                    host.markCache(sessionId, DshSessionCacheState.SYNC_FAILED)
                    host.connectionLabel = "远程历史同步失败 · 已显示缓存"
                } else {
                    host.connectionLabel = "内核连接失败 · 已显示缓存"
                }
            } else {
                host.messages.add(DshMessage("history-error", DshMessageRole.ERROR, error))
            }
        })
    }

    fun resyncStreamingWithHost(sessionId: String, reason: String) {
        val token = host.generationToken()
        val plan = ConversationCoordinator.planResync(
            isRemote = host.isRemoteHost,
            requestedSessionId = sessionId,
            activeSessionId = host.activeSessionId,
            reason = reason,
            sessionRunning = host.sessionRunning,
            streaming = host.streaming,
            stopVisible = host.stopButtonVisible,
            rootId = host.streamingAssistantRootId,
        )
        DshStreamLog.i(
            "ui.resync.begin reason=$reason session=$sessionId running=${host.sessionRunning} streaming=${host.streaming} stop=${host.stopButtonVisible}",
        )
        when (plan) {
            ResyncPlan.Ignore -> return
            ResyncPlan.KeepAcceptedLocalSend -> {
                DshStreamLog.i(
                    "ui.resync.skip-local-stream reason=$reason session=$sessionId root=${host.streamingAssistantRootId}",
                )
            }
            ResyncPlan.ResumeFromHistory -> host.loadWebTimeline(
                sessionId,
                scrollToEndAfterLoad = true,
                forceReplace = true,
            ) {
                if (!host.accepts(token)) return@loadWebTimeline
                host.resumeStreamingFromHistory(sessionId, reason)
            }
            ResyncPlan.FinishFromHistory -> host.loadWebTimeline(
                sessionId,
                scrollToEndAfterLoad = true,
                forceReplace = ConversationCoordinator.finishReplacesTimeline(host.streaming, host.stopButtonVisible),
            ) {
                if (!host.accepts(token)) return@loadWebTimeline
                host.finishStreamingFromHistory(sessionId)
                host.connectionLabel = "已连接"
                DshStreamLog.i("ui.resync.settled reason=$reason session=$sessionId messages=${host.messageCount}")
            }
        }
    }
}

internal interface SessionHistoryHost {
    val isRemoteHost: Boolean
    val activeSessionId: String
    val sessionRunning: Boolean
    val streaming: Boolean
    val stopButtonVisible: Boolean
    val streamingAssistantRootId: String
    val messageCount: Int
    val activeConnectionId: String
    var connectionLabel: String
    var historyRequestGeneration: Int
    var messages: ObservableList<DshMessage>
    fun repository(): DshRepository?
    fun generationToken(): Long
    fun accepts(token: Long): Boolean
    fun sessionMessageState(sessionId: String, scrollToEndAfterLoad: Boolean): ObservableList<DshMessage>
    fun ensureConversationPanel(sessionId: String)
    fun loadSkills(sessionId: String)
    fun loadWebTimeline(
        sessionId: String,
        scrollToEndAfterLoad: Boolean = true,
        forceReplace: Boolean = false,
        afterApply: () -> Unit = {},
    )
    fun markMessageReady(sessionId: String)
    fun markCache(sessionId: String, state: DshSessionCacheState)
    fun replaceMessagesIfChanged(loaded: List<DshMessage>, force: Boolean = false)
    fun replaceStoredMessages(sessionId: String, loaded: List<DshMessage>)
    fun completePendingSessionSelection(sessionId: String)
    fun realizeSessionAfterData(sessionId: String, scrollToEndAfterLoad: Boolean)
    fun resumeStreamingFromHistory(sessionId: String, reason: String)
    fun finishStreamingFromHistory(sessionId: String)
}
