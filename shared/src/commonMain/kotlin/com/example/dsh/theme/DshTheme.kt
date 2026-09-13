package com.example.dsh.theme

/** 某一时刻的完整主题快照。`revision` 单调递增，用于判断是否需要刷新。 */
internal data class DshThemeSnapshot(
    val state: DshThemeState,
    val tokens: DshThemeTokens,
    val codeColors: DshCodeColors,
    val revision: Int,
) {
    val isDark: Boolean get() = state.isDark
    val codeIsDark: Boolean get() = state.codeIsDark
    val preference: DshThemePreference get() = state.preference
    val highContrast: Boolean get() = state.highContrast
    val codeTheme: DshCodeThemePreference get() = state.codeTheme
}

/**
 * 主题的单一状态源。只存普通数据，不含 Kuikly observable。
 */
internal object DshTheme {
    const val EVENT = "dshThemeChanged"
    const val PREF_KEY = "theme_preference"
    const val CODE_PREF_KEY = "code_theme_preference"
    const val HIGH_CONTRAST_KEY = "theme_high_contrast"

    private var state = DshThemeState()
    private var revision = 0

    var snapshot: DshThemeSnapshot = buildSnapshot()
        private set

    fun bootstrap(
        stored: String?,
        systemDark: Boolean,
        storedCodeTheme: String? = null,
        storedHighContrast: Boolean = false,
        solarNight: Boolean = systemDark,
    ): Boolean {
        val next = DshThemeState(
            preference = DshThemePreference.fromStorage(stored),
            systemDark = systemDark,
            solarNight = solarNight,
            highContrast = storedHighContrast,
            codeTheme = DshCodeThemePreference.fromStorage(storedCodeTheme),
        )
        return commit(next)
    }

    fun setPreference(preference: DshThemePreference): Boolean =
        commit(state.copy(preference = preference))

    fun setCodeTheme(codeTheme: DshCodeThemePreference): Boolean =
        commit(state.copy(codeTheme = codeTheme))

    fun setHighContrast(enabled: Boolean): Boolean =
        commit(state.copy(highContrast = enabled))

    fun updateSystemDark(systemDark: Boolean): Boolean =
        commit(state.copy(systemDark = systemDark))

    fun updateSolarNight(solarNight: Boolean): Boolean =
        commit(state.copy(solarNight = solarNight))

    fun resetForTest() {
        state = DshThemeState()
        revision = 0
        snapshot = buildSnapshot()
    }

    private fun commit(next: DshThemeState): Boolean {
        val visibleChange = next.preference != state.preference ||
            next.isDark != state.isDark ||
            next.highContrast != state.highContrast ||
            next.codeIsDark != state.codeIsDark
        state = next
        if (!visibleChange) return false
        revision++
        snapshot = buildSnapshot()
        return true
    }

    private fun buildSnapshot(): DshThemeSnapshot = DshThemeSnapshot(
        state = state,
        tokens = DshThemeTokens.of(state.isDark, state.highContrast),
        codeColors = DshCodeColors.of(state.codeIsDark),
        revision = revision,
    )
}
