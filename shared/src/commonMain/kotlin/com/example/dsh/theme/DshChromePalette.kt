package com.example.dsh.theme

/**
 * Native 窗口 / 状态栏色。Android、iOS 直接引用；鸿蒙 ETS 必须与这里的 ARGB 保持一致。
 */
object DshChromePalette {
    const val LIGHT_BACKGROUND = 0xFFFAFAFAL
    const val DARK_BACKGROUND = 0xFF151517L
    const val LIGHT_SURFACE = 0xFFFFFFFFL
    const val DARK_SURFACE = 0xFF232324L
    const val LIGHT_BAR_CONTENT = 0xFF0F1115L
    const val DARK_BAR_CONTENT = 0xFFF9FAFBL

    fun backgroundArgb(isDark: Boolean): Int =
        (if (isDark) DARK_BACKGROUND else LIGHT_BACKGROUND).toInt()

    /** 页面顶栏 / 卡片色。系统栏透明时由页面自己铺到状态栏下方。 */
    fun surfaceArgb(isDark: Boolean): Int =
        (if (isDark) DARK_SURFACE else LIGHT_SURFACE).toInt()

    fun barContentArgb(isDark: Boolean): Int =
        (if (isDark) DARK_BAR_CONTENT else LIGHT_BAR_CONTENT).toInt()
}
