package com.example.dsh.theme

/**
 * 主题解析状态：用户偏好 + 系统当前是否深色 + 太阳时 → 最终是否深色。
 * 系统回调只允许改 [systemDark]，不得改 [preference]。
 */
internal data class DshThemeState(
    val preference: DshThemePreference = DshThemePreference.SYSTEM,
    val systemDark: Boolean = false,
    val solarNight: Boolean = false,
    val highContrast: Boolean = false,
    val codeTheme: DshCodeThemePreference = DshCodeThemePreference.FOLLOW,
) {
    val isDark: Boolean
        get() = preference.resolvedIsDark(systemDark, solarNight)

    val codeIsDark: Boolean
        get() = codeTheme.resolvedIsDark(isDark)
}
