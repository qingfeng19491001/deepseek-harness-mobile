package com.example.dsh.session

import com.example.dsh.dsh.DshSession
import com.example.dsh.dsh.DshSessionSort
import com.example.dsh.dsh.DshWorkspaceGroup
import com.example.dsh.dsh.syncVisibleSessions
import com.tencent.kuikly.core.pager.Pager
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList

internal fun activeSessionIsArchived(archivedIds: Iterable<String>, activeSessionId: String): Boolean =
    archivedIds.any { it == activeSessionId }

/**
 * 页面级会话列表。当前选中项只保存在 [activeSessionId]。
 * 可见列表和“当前会话已归档”由这里推导，调用方不能单独改它们。
 */
internal class HomeState(pager: Pager) {
    val sessions by pager.observableList<DshSession>()
    val visibleSessions by pager.observableList<DshSession>()
    val archivedSessions by pager.observableList<DshSession>()
    var activeSessionId by pager.observable("session-1")
    var sessionSort by pager.observable(DshSessionSort.RECENT)
    var sessionUndoId by pager.observable("")
    var sessionUndoTitle by pager.observable("")
    var sessionActionBusy by pager.observable(false)
    var sessionActionError by pager.observable("")
    var canUnarchive by pager.observable(false)
    var canDeleteSession by pager.observable(false)
    val workspaceGroups by pager.observableList<DshWorkspaceGroup>()

    val activeSessionArchived: Boolean
        get() = activeSessionIsArchived(archivedSessions.map { it.id }, activeSessionId)

    fun publishVisible(archivedIds: Set<String>) {
        syncVisibleSessions(sessions, visibleSessions, archivedIds, sessionSort)
    }

    fun replaceArchived(next: List<DshSession>) {
        archivedSessions.clear()
        archivedSessions.addAll(next)
    }

    fun replaceWorkspaceGroups(next: List<DshWorkspaceGroup>) {
        workspaceGroups.clear()
        workspaceGroups.addAll(next)
    }
}
