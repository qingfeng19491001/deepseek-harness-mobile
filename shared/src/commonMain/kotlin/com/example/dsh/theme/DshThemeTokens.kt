package com.example.dsh.theme

import com.tencent.kuikly.core.base.Color

/** 状态色成对出现：容器用 [background]，文字 / 图标用 [foreground]。 */
internal data class DshStateColors(
    val background: Color,
    val foreground: Color,
)

/**
 * 语义色令牌。页面只允许引用这里的角色，不得再写 `Color(0x...)`。
 * 色值以 deepseek-harness `ui-theme` 的 `--dsw-alias-*` 为准，缺口处补充并经过对比度检查。
 */
internal data class DshThemeTokens(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val surfaceElevated: Color,
    val selectedSurface: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val tertiaryText: Color,
    val captionText: Color,
    val divider: Color,
    val dividerStrong: Color,
    val primary: Color,
    val primaryPressed: Color,
    val primaryDisabled: Color,
    val onPrimary: Color,
    val icon: Color,
    val scrim: Color,
    val userBubble: Color,
    val userBubbleText: Color,
    val success: DshStateColors,
    val warning: DshStateColors,
    val error: DshStateColors,
    val info: DshStateColors,
    val running: DshStateColors,
    val disabled: DshStateColors,
) {
    companion object {
        val LIGHT = DshThemeTokens(
            background = Color(DshChromePalette.LIGHT_BACKGROUND),
            surface = Color(0xFFFFFFFFL),
            surfaceVariant = Color(0xFFF1F3F5L),
            surfaceElevated = Color(0xFFFFFFFFL),
            selectedSurface = Color(0xFFE4EDFDL),
            primaryText = Color(DshChromePalette.LIGHT_BAR_CONTENT),
            secondaryText = Color(0xFF61666BL),
            tertiaryText = Color(0xFF81858CL),
            captionText = Color(0xFF81858CL),
            divider = Color(0x000000L, 0.10f),
            dividerStrong = Color(0x000000L, 0.16f),
            primary = Color(0xFF4176E6L),
            primaryPressed = Color(0xFF315FC7L),
            primaryDisabled = Color(0xFFB7C8FEL),
            onPrimary = Color(0xFFFFFFFFL),
            icon = Color(0xFF555D64L),
            scrim = Color(0x000000L, 0.40f),
            userBubble = Color(0xFFEDF3FEL),
            userBubbleText = Color(0xFF34415BL),
            success = DshStateColors(Color(0xFFE6FAEDL), Color(0xFF1B7A44L)),
            warning = DshStateColors(Color(0xFFFEF5E7L), Color(0xFF9A6500L)),
            error = DshStateColors(Color(0xFFFDEBECL), Color(0xFFBF3535L)),
            info = DshStateColors(Color(0xFFEEF3FAL), Color(0xFF2F5FC4L)),
            running = DshStateColors(Color(0xFFEEF3FAL), Color(0xFF3556D9L)),
            disabled = DshStateColors(Color(0xFFF1F3F5L), Color(0xFF666B71L)),
        )

        val DARK = DshThemeTokens(
            background = Color(DshChromePalette.DARK_BACKGROUND),
            surface = Color(0xFF232324L),
            surfaceVariant = Color(0xFF2C2C2EL),
            surfaceElevated = Color(0xFF353638L),
            selectedSurface = Color(0xFF1C2D49L),
            primaryText = Color(DshChromePalette.DARK_BAR_CONTENT),
            secondaryText = Color(0xFFCFD3D6L),
            tertiaryText = Color(0xFFADB2B8L),
            captionText = Color(0xFF9CA1A8L),
            divider = Color(0xFFFFFFL, 0.12f),
            dividerStrong = Color(0xFFFFFFL, 0.20f),
            primary = Color(0xFF5686FEL),
            primaryPressed = Color(0xFF679EFEL),
            primaryDisabled = Color(0xFF3A4A6EL),
            onPrimary = Color(0xFFFFFFFFL),
            icon = Color(0xFFD1D7DCL),
            scrim = Color(0x000000L, 0.60f),
            userBubble = Color(0xFF1C2D49L),
            userBubbleText = Color(0xFFDCE6FFL),
            success = DshStateColors(Color(0xFF163A27L), Color(0xFF72D79BL)),
            warning = DshStateColors(Color(0xFF453515L), Color(0xFFF4C96BL)),
            error = DshStateColors(Color(0xFF451D22L), Color(0xFFFF8F9AL)),
            info = DshStateColors(Color(0xFF1C2D49L), Color(0xFF8CB2FFL)),
            running = DshStateColors(Color(0xFF1C2D49L), Color(0xFF8CB2FFL)),
            disabled = DshStateColors(Color(0xFF2C2C2EL), Color(0xFF9CA2A8L)),
        )

        val LIGHT_HIGH_CONTRAST = LIGHT.copy(
            background = Color(0xFFFFFFFDL),
            surface = Color(0xFFFFFFFDL),
            surfaceVariant = Color(0xFFE8E8E8L),
            surfaceElevated = Color(0xFFFFFFFDL),
            primaryText = Color(0xFF000000L),
            secondaryText = Color(0xFF1A1A1AL),
            tertiaryText = Color(0xFF2E2E2EL),
            captionText = Color(0xFF2E2E2EL),
            divider = Color(0x000000L, 0.28f),
            dividerStrong = Color(0x000000L, 0.40f),
            icon = Color(0xFF111111L),
            userBubbleText = Color(0xFF0A1628L),
        )

        val DARK_HIGH_CONTRAST = DARK.copy(
            background = Color(0xFF000000L),
            surface = Color(0xFF0A0A0AL),
            surfaceVariant = Color(0xFF161616L),
            surfaceElevated = Color(0xFF1C1C1CL),
            primaryText = Color(0xFFFFFFFDL),
            secondaryText = Color(0xFFF2F2F2L),
            tertiaryText = Color(0xFFE0E0E0L),
            captionText = Color(0xFFD6D6D6L),
            divider = Color(0xFFFFFFL, 0.32f),
            dividerStrong = Color(0xFFFFFFL, 0.48f),
            icon = Color(0xFFFFFFFDL),
            userBubbleText = Color(0xFFFFFFFDL),
        )

        fun of(isDark: Boolean, highContrast: Boolean = false): DshThemeTokens = when {
            highContrast && isDark -> DARK_HIGH_CONTRAST
            highContrast -> LIGHT_HIGH_CONTRAST
            isDark -> DARK
            else -> LIGHT
        }
    }
}
