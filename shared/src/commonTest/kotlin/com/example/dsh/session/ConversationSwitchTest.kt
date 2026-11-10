package com.example.dsh.session

import kotlin.test.Test
import kotlin.test.assertIs

class ConversationSwitchTest {
    @Test
    fun sameSessionDoesNotSwitch() {
        assertIs<SessionSwitch.Same>(ConversationCoordinator.planSessionSwitch("s1", "s1"))
    }

    @Test
    fun differentSessionSwitchesWithoutAConnectionOperation() {
        assertIs<SessionSwitch.Switch>(ConversationCoordinator.planSessionSwitch("s1", "s2"))
    }
}
