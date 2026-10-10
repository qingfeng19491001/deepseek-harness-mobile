package com.example.dsh.dsh

import com.example.dsh.theme.tokens
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal fun ViewContainer<*, *>.DshAppSettingsHubModal(
    appearanceSummary: () -> String,
    showPlugins: () -> Boolean,
    pluginSummary: () -> String,
    logSummary: () -> String,
    onOpenAppearance: () -> Unit,
    onOpenPlugins: () -> Unit,
    onOpenLogs: () -> Unit,
    onClose: () -> Unit,
) {
    DshFullScreenModal {
        View {
            attr {
                flex(1f)
                flexDirectionColumn()
                backgroundColor(tokens.background)
            }
            DshModalHeader(
                title = { "设置" },
                onBack = onClose,
                showClose = false,
                onClose = onClose,
            )
            Scroller {
                attr { flex(1f); showScrollerIndicator(false) }
                View {
                    attr { flexDirectionColumn(); paddingBottom(32f) }
                    DshSettingsHubSection("应用")
                    DshSettingsHubCard {
                        DshSettingsHubRow(
                            icon = "appearance.svg",
                            title = "外观",
                            detail = appearanceSummary,
                            showDivider = true,
                            onClick = onOpenAppearance,
                        )
                        vif({ showPlugins() }) {
                            DshSettingsHubRow(
                                icon = "plugin.svg",
                                title = "插件",
                                detail = pluginSummary,
                                showDivider = true,
                                onClick = onOpenPlugins,
                            )
                        }
                        DshSettingsHubRow(
                            icon = "log.svg",
                            title = "日志",
                            detail = logSummary,
                            showDivider = false,
                            onClick = onOpenLogs,
                        )
                    }
                    View {
                        attr {
                            marginTop(24f)
                            paddingLeft(8f)
                            paddingRight(8f)
                        }
                        Text {
                            attr {
                                text("DSH Harness · 内容由 AI 生成，请仔细甄别并合法使用")
                                fontSize(11f)
                                lineHeight(16f)
                                textAlignCenter()
                                color(tokens.captionText)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.DshSettingsHubSection(title: String) {
    Text {
        attr {
            text(title)
            marginTop(8f)
            marginBottom(8f)
            marginLeft(4f)
            fontSize(13f)
            color(tokens.secondaryText)
        }
    }
}

private fun ViewContainer<*, *>.DshSettingsHubCard(content: ViewContainer<*, *>.() -> Unit) {
    View {
        attr {
            flexDirectionColumn()
            borderRadius(12f)
            backgroundColor(tokens.surface)
            border(Border(1f, BorderStyle.SOLID, tokens.divider))
            overflow(true)
        }
        content()
    }
}

private fun ViewContainer<*, *>.DshSettingsHubRow(
    icon: String,
    title: String,
    detail: () -> String,
    showDivider: Boolean,
    onClick: () -> Unit,
) {
    View {
        attr { flexDirectionColumn() }
        View {
            attr {
                minHeight(56f)
                flexDirectionRow()
                alignItemsCenter()
                paddingLeft(16f)
                paddingRight(12f)
            }
            Image {
                attr {
                    src(ImageUri.commonAssets(icon))
                    size(22f, 22f)
                    tintColor(tokens.icon)
                }
            }
            Text {
                attr {
                    text(title)
                    marginLeft(14f)
                    fontSize(16f)
                    color(tokens.primaryText)
                }
            }
            View { attr { flex(1f) } }
            Text {
                attr {
                    text(detail())
                    marginRight(4f)
                    fontSize(14f)
                    color(tokens.tertiaryText)
                    lines(1)
                }
            }
            Text {
                attr {
                    text("›")
                    fontSize(20f)
                    color(tokens.captionText)
                }
            }
            event { click { onClick() } }
        }
        vif({ showDivider }) {
            View {
                attr {
                    height(1f)
                    marginLeft(52f)
                    backgroundColor(tokens.divider)
                }
            }
        }
    }
}
