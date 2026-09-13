package com.example.dsh.theme

/** 代码高亮主题，可与 App 深浅色解耦。 */
enum class DshCodeThemePreference(val storageValue: String, val label: String) {
    FOLLOW("follow", "跟随界面"),
    LIGHT("light", "浅色代码"),
    DARK("dark", "深色代码");

    fun resolvedIsDark(appIsDark: Boolean): Boolean = when (this) {
        FOLLOW -> appIsDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromStorage(raw: String?): DshCodeThemePreference {
            val normalized = raw?.trim()?.lowercase() ?: return FOLLOW
            return entries.firstOrNull { it.storageValue == normalized } ?: FOLLOW
        }
    }
}
