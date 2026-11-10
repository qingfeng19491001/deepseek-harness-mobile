package com.example.dsh.connection

import com.example.dsh.dsh.DshRawSessionEvent

/**
 * 连接层交给页面的事件。页面在 Session 抽出之前继续处理会话副作用。
 * 连接层不接收知道会话字段的回调实现。
 */
internal interface DshConnectionEvents {
    fun activeSessionId(): String
    fun syncTurnStatus()
    fun loadRepository(preferredSessionId: String?)
    fun probeSessionLifecycle()
    fun dismissKeyboard()
    fun closeSessionDrawer()
    fun hideAppSettingsHub()
    fun openConnectionSetup()
    fun clearGoalAndStream()
    fun clearApiKeyInput()
    fun onLocalEngineError(error: String)
    fun onQueueSnapshot(sessionId: String)
    fun onJobsSnapshot(sessionId: String)
    fun onSessionStatus(sessionId: String, running: Boolean)
    fun onProjection(sessionId: String, key: String, value: String, seq: Int)
    fun onSessionEvent(sessionId: String, event: DshRawSessionEvent)
    fun onRemoteEvent(event: String)
    fun onArchivedSessionsChanged()
    fun onPendingInteraction(sessionId: String)
}
