package com.example.dsh.dsh

import com.example.dsh.theme.DshCodeThemePreference
import com.example.dsh.theme.DshThemePreference
import com.example.dsh.theme.DshThemeSnapshot
import com.example.dsh.theme.reduceMotion
import com.example.dsh.theme.theme
import com.example.dsh.theme.tokens
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Switch
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal fun ViewContainer<*, *>.DshAppearanceModal(
    onSelect: (DshThemePreference) -> Unit,
    onSelectCodeTheme: (DshCodeThemePreference) -> Unit,
    onToggleHighContrast: (Boolean) -> Unit,
    onToggleReduceMotion: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    DshBottomSheetModal(
        heightRatio = 0.86f,
        minHeight = 320f,
        maxHeight = 760f,
        onClose = onClose,
    ) {
        DshModalHeader(title = { "外观" }, subtitle = { "选择后立即生效" }, onClose = onClose)
        Scroller {
            attr { flex(1f); showScrollerIndicator(false) }
            View {
                attr {
                    flexDirectionColumn()
                    paddingBottom(12f)
                    // 订阅 revision，避免只改偏好、深浅不变时单选圈不刷新。
                    opacity(if (theme.revision >= 0) 1f else 1f)
                }
                DshAppearanceSectionLabel("主题模式")
                DshAppearanceGroup {
                    DshThemePreference.entries.forEachIndexed { index, preference ->
                        DshAppearanceChoice(
                            title = preference.label,
                            selected = { theme.preference == preference },
                            subtitle = { appearanceSubtitle(preference, theme) },
                            marginTop = if (index == 0) 0f else 2f,
                            onSelect = { onSelect(preference) },
                        )
                    }
                }
                DshAppearanceSectionLabel("代码主题")
                DshAppearanceGroup {
                    DshCodeThemePreference.entries.forEachIndexed { index, preference ->
                        DshAppearanceChoice(
                            title = preference.label,
                            selected = { theme.codeTheme == preference },
                            subtitle = {
                                if (preference == DshCodeThemePreference.FOLLOW) {
                                    if (theme.codeIsDark) "当前：深色高亮" else "当前：浅色高亮"
                                } else {
                                    ""
                                }
                            },
                            marginTop = if (index == 0) 0f else 2f,
                            onSelect = { onSelectCodeTheme(preference) },
                        )
                    }
                }
                DshAppearanceSectionLabel("无障碍")
                DshAppearanceGroup {
                    DshAppearanceSwitch(
                        title = "高对比度",
                        subtitle = "提高正文、分割线和状态色的对比",
                        selected = { theme.highContrast },
                        onToggle = onToggleHighContrast,
                    )
                    View { attr { height(2f) } }
                    DshAppearanceSwitch(
                        title = "减弱动态效果",
                        subtitle = "弹窗、抽屉和按钮不再位移或缩放，只保留短暂淡入淡出",
                        selected = { reduceMotion },
                        onToggle = onToggleReduceMotion,
                    )
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.DshAppearanceSwitch(
    title: String,
    subtitle: String,
    selected: () -> Boolean,
    onToggle: (Boolean) -> Unit,
) {
    View {
        attr {
            minHeight(64f)
            flexDirectionRow()
            alignItemsCenter()
            paddingLeft(12f)
            paddingRight(8f)
            paddingTop(8f)
            paddingBottom(8f)
            borderRadius(9f)
            backgroundColor(tokens.surface)
        }
        View {
            attr { flex(1f); flexDirectionColumn() }
            Text { attr { text(title); fontSize(15f); fontWeightMedium(); color(tokens.primaryText) } }
            Text { attr { text(subtitle); marginTop(3f); fontSize(12f); color(tokens.tertiaryText) } }
        }
        Switch {
            attr {
                width(48f)
                height(28f)
                isOn(selected())
                onColor(tokens.primary)
                unOnColor(tokens.dividerStrong)
                thumbColor(Color.WHITE)
            }
            event { switchOnChanged { onToggle(it) } }
        }
    }
}

private fun appearanceSubtitle(preference: DshThemePreference, snapshot: DshThemeSnapshot): String = when {
    preference == DshThemePreference.SYSTEM && snapshot.preference == DshThemePreference.SYSTEM ->
        if (snapshot.isDark) "当前：深色" else "当前：浅色"
    preference == DshThemePreference.AUTO && snapshot.preference == DshThemePreference.AUTO ->
        if (snapshot.isDark) "当前：日落后深色" else "当前：日出后浅色"
    else -> ""
}

private fun ViewContainer<*, *>.DshAppearanceSectionLabel(title: String) {
    Text {
        attr {
            text(title)
            marginTop(14f)
            fontSize(13f)
            color(tokens.secondaryText)
        }
    }
}

private fun ViewContainer<*, *>.DshAppearanceGroup(init: ViewContainer<*, *>.() -> Unit) {
    View {
        attr {
            marginTop(8f)
            flexDirectionColumn()
            borderRadius(12f)
            border(Border(1f, BorderStyle.SOLID, tokens.divider))
            backgroundColor(tokens.surfaceVariant)
            padding(4f)
        }
        init()
    }
}

private fun ViewContainer<*, *>.DshAppearanceChoice(
    title: String,
    selected: () -> Boolean,
    subtitle: () -> String,
    marginTop: Float,
    onSelect: () -> Unit,
) {
    View {
        attr {
            minHeight(48f)
            marginTop(marginTop)
            flexDirectionRow()
            alignItemsCenter()
            paddingLeft(12f)
            paddingRight(12f)
            paddingTop(10f)
            paddingBottom(10f)
            borderRadius(9f)
            backgroundColor(if (selected()) tokens.surface else Color.TRANSPARENT)
        }
        View {
            attr {
                size(18f, 18f)
                borderRadius(9f)
                allCenter()
                border(
                    Border(
                        if (selected()) 5f else 1.5f,
                        BorderStyle.SOLID,
                        if (selected()) tokens.primary else tokens.dividerStrong,
                    ),
                )
                backgroundColor(tokens.surface)
            }
        }
        View {
            attr { flex(1f); marginLeft(12f); flexDirectionColumn() }
            Text {
                attr {
                    text(title)
                    fontSize(15f)
                    fontWeightMedium()
                    color(tokens.primaryText)
                }
            }
            vif({ subtitle().isNotEmpty() }) {
                Text {
                    attr {
                        text(subtitle())
                        marginTop(2f)
                        fontSize(12f)
                        color(tokens.tertiaryText)
                    }
                }
            }
        }
        event { click { onSelect() } }
    }
}
