package com.example.dsh.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DshThemeStateTest {
    @Test
    fun systemFollowsSystemLight() {
        assertFalse(DshThemeState(DshThemePreference.SYSTEM, systemDark = false).isDark)
    }

    @Test
    fun systemFollowsSystemDark() {
        assertTrue(DshThemeState(DshThemePreference.SYSTEM, systemDark = true).isDark)
    }

    @Test
    fun lightIgnoresSystem() {
        assertFalse(DshThemeState(DshThemePreference.LIGHT, systemDark = true).isDark)
    }

    @Test
    fun darkIgnoresSystem() {
        assertTrue(DshThemeState(DshThemePreference.DARK, systemDark = false).isDark)
    }

    @Test
    fun storageValuesRoundTrip() {
        assertEquals(DshThemePreference.LIGHT, DshThemePreference.fromStorage("light"))
        assertEquals(DshThemePreference.DARK, DshThemePreference.fromStorage("dark"))
        assertEquals(DshThemePreference.SYSTEM, DshThemePreference.fromStorage("system"))
        assertEquals(DshThemePreference.DARK, DshThemePreference.fromStorage(" Dark "))
        assertTrue(DshThemePreference.DARK.resolvedIsDark(false))
        assertFalse(DshThemePreference.LIGHT.resolvedIsDark(true))
        DshThemePreference.entries.forEach {
            assertEquals(it, DshThemePreference.fromStorage(it.storageValue))
        }
    }

    @Test
    fun autoUsesSolarNight() {
        assertTrue(DshThemeState(DshThemePreference.AUTO, systemDark = false, solarNight = true).isDark)
        assertFalse(DshThemeState(DshThemePreference.AUTO, systemDark = true, solarNight = false).isDark)
    }

    @Test
    fun codeThemeCanDivergeFromApp() {
        val darkAppLightCode = DshThemeState(
            preference = DshThemePreference.DARK,
            codeTheme = DshCodeThemePreference.LIGHT,
        )
        assertTrue(darkAppLightCode.isDark)
        assertFalse(darkAppLightCode.codeIsDark)
    }

    @Test
    fun invalidStorageFallsBackToSystem() {
        listOf(null, "", "blue", "TRUE", "1", "night").forEach {
            assertEquals(DshThemePreference.SYSTEM, DshThemePreference.fromStorage(it), "raw=$it")
        }
        assertEquals(DshThemePreference.AUTO, DshThemePreference.fromStorage("auto"))
        assertEquals(DshCodeThemePreference.FOLLOW, DshCodeThemePreference.fromStorage(null))
        assertEquals(DshCodeThemePreference.DARK, DshCodeThemePreference.fromStorage("dark"))
    }
}
