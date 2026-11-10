package com.example.dsh.session

import com.example.dsh.dsh.DshSession
import com.example.dsh.dsh.DshSessionSort
import com.example.dsh.dsh.syncVisibleSessions
import com.tencent.kuikly.core.reactive.collection.ObservableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeStateDerivationTest {
    @Test
    fun visibleSessionsDropBlankAndArchivedWithoutASecondSource() {
        val source = ObservableList<DshSession>()
        source.addAll(listOf(
            session("blank", blank = true),
            session("archived"),
            session("live"),
        ))
        val visible = ObservableList<DshSession>()
        syncVisibleSessions(source, visible, archivedIds = setOf("archived"), sort = DshSessionSort.TITLE)
        assertEquals(listOf("live"), visible.map { it.id })
    }

    @Test
    fun activeSessionArchivedFollowsTheArchivedIds() {
        assertTrue(activeSessionIsArchived(listOf("a", "b"), "b"))
        assertFalse(activeSessionIsArchived(listOf("a"), "b"))
    }

    private fun session(id: String, blank: Boolean = false) = DshSession(
        id = id,
        title = id,
        workspace = "",
        updatedLabel = "",
        blank = blank,
    )
}
