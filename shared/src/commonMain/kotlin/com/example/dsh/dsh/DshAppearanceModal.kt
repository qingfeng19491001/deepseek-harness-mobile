package com.example.dsh.dsh

import com.example.dsh.theme.DshCodeThemePreference
import com.example.dsh.theme.DshThemePreference
import com.example.dsh.theme.DshThemeSnapshot
import com.example.dsh.theme.theme
import com.example.dsh.theme.tokens
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Modal
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal fun ViewContainer<*, *>.DshAppearanceModal(
    onSelect: (DshThemePreference) -> Unit,
    onSelectCodeTheme: (DshCodeThemePreference) -> Unit,
    onToggleHighContrast: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    Modal(inWindow = true) {
        attr {
            absolutePositionAllZero()
            allCenter()
            paddingLeft(20f)
            paddingRight(20f)
            backgroundColor(tokens.scrim)
        }
        View {
            attr {
                absolutePositionAllZero()
                backgroundColor(Color.TRANSPARENT)
            }
            event { click { onClose() } }
        }
        View {
            attr {
                width(pagerData.pageViewWidth - 40f)
                maxWidth(420f)
                flexDirectionColumn()
                padding(24f)
                borderRadius(18f)
                backgroundColor(tokens.surface)
                // 订阅 revision，避免只改偏好、深浅不变时单选圈不刷新。
                opacity(if (theme.revision >= 0) 1f else 1f)
            }
            View {
                attr { height(32f); flexDirectionRow(); alignItemsCenter() }
                Text {
                    attr {
                        text("外观")
                        flex(1f)
                        fontSize(20f)
                        fontWeightBold()
                        color(tokens.primaryText)
                    }
                }
                View {
                    attr { size(32f, 32f); allCenter() }
                    Image {
                        attr {
                            src(ImageUri.commonAssets("x.svg"))
                            size(20f, 20f)
                            tintColor(tokens.icon)
                        }
                    }
                    event { click { onClose() } }
                }
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
                DshAppearanceChoice(
                    title = "高对比度",
                    selected = { theme.highContrast },
                    subtitle = { "提高正文、分割线和状态色的对比" },
                    marginTop = 0f,
                    onSelect = { onToggleHighContrast(!theme.highContrast) },
                )
            }
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
