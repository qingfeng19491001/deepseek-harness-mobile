package com.example.dsh.connection

import com.example.dsh.dsh.DshConnectionMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DshConnectionCoordinatorTest {
    @Test
    fun beginRejectsThePreviousAttemptAndStopClearsTheMode() {
        val coordinator = DshConnectionCoordinator()
        val first = coordinator.begin(DshConnectionMode.RELAY)
        val second = coordinator.begin(DshConnectionMode.SSH)

        assertFalse(coordinator.accepts(first, DshConnectionMode.RELAY))
        assertTrue(coordinator.accepts(second, DshConnectionMode.SSH))
        assertFalse(coordinator.accepts(second, DshConnectionMode.RELAY))

        coordinator.stop()
        assertFalse(coordinator.accepts(second, DshConnectionMode.SSH))
        assertFalse(coordinator.isActive(DshConnectionMode.SSH))
        assertEquals(DshConnectionMode.RELAY, coordinator.activeModeOr(DshConnectionMode.RELAY))
    }
}
