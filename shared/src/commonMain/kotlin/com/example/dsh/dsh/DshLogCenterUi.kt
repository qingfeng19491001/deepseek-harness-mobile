package com.example.dsh.dsh

import com.example.dsh.theme.tokens
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.directives.vforLazy
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.layout.FlexWrap
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Input
import com.tencent.kuikly.core.views.List
import com.tencent.kuikly.core.views.Modal
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal fun ViewContainer<*, *>.DshLogCenterModal(
    query: () -> String,
    sessionQuery: () -> String,
    filterId: () -> String,
    entries: () -> ObservableList<DshLogEntry>,
    detail: () -> DshLogEntry?,
    onQueryChange: (String) -> Unit,
    onSessionQueryChange: (String) -> Unit,
    onFilterChange: (String) -> Unit,
    onOpen: (DshLogEntry) -> Unit,
    onCloseDetail: () -> Unit,
    onCopy: () -> Unit,
    onExport: () -> Unit,
    onFeedback: () -> Unit,
    onClear: () -> Unit,
    onJump: (String) -> Unit,
    onClose: () -> Unit,
) {
    Modal(inWindow = true) {
        attr {
            absolutePositionAllZero()
            allCenter()
            paddingLeft(12f)
            paddingRight(12f)
            backgroundColor(tokens.scrim)
        }
        View {
            attr {
                width(pagerData.pageViewWidth - 24f)
                maxWidth(560f)
                height((pagerData.pageViewHeight - 48f).coerceAtMost(720f))
                flexDirectionColumn()
                padding(16f)
                borderRadius(16f)
                backgroundColor(tokens.background)
            }
            vif({ detail() == null }) {
                View {
                    attr { height(36f); flexDirectionRow(); alignItemsCenter() }
                    Text { attr { text("日志"); flex(1f); fontSize(20f); fontWeightBold(); color(tokens.primaryText) } }
                    View { attr { size(32f, 32f); allCenter() }; Image { attr { src(ImageUri.commonAssets("x.svg")); size(20f, 20f); tintColor(tokens.icon) } }; DshHitButton { onClose() } }
                }
                Text {
                    attr {
                        text("本地环形缓冲，不含 chunk 正文、密钥和附件 Base64。清空不影响会话。")
                        marginTop(4f)
                        fontSize(12f)
                        color(tokens.secondaryText)
                    }
                }
                View {
                    attr {
                        height(40f)
                        marginTop(12f)
                        flexDirectionRow()
                        alignItemsCenter()
                        paddingLeft(12f)
                        paddingRight(12f)
                        borderRadius(10f)
                        backgroundColor(tokens.surfaceVariant)
                    }
                    Input {
                        ref { it.view?.setText(query()) }
                        attr {
                            flex(1f)
                            fontSize(14f)
                            color(tokens.primaryText)
                            placeholder("关键词")
                            placeholderColor(tokens.tertiaryText)
                            returnKeyTypeDone()
                        }
                        event { textDidChange { onQueryChange(it.text) } }
                    }
                }
                View {
                    attr {
                        height(40f)
                        marginTop(8f)
                        flexDirectionRow()
                        alignItemsCenter()
                        paddingLeft(12f)
                        paddingRight(12f)
                        borderRadius(10f)
                        backgroundColor(tokens.surfaceVariant)
                    }
                    Input {
                        ref { it.view?.setText(sessionQuery()) }
                        attr {
                            flex(1f)
                            fontSize(14f)
                            color(tokens.primaryText)
                            placeholder("按 sessionId 筛选")
                            placeholderColor(tokens.tertiaryText)
                            returnKeyTypeDone()
                        }
                        event { textDidChange { onSessionQueryChange(it.text) } }
                    }
                }
                View {
                    attr { marginTop(10f); flexDirectionRow(); flexWrap(FlexWrap.WRAP); alignItemsCenter() }
                    DshLogChip(label = DshLogFilter.ALL.label, selected = { filterId() == DshLogFilter.ALL.id }, onClick = { onFilterChange(DshLogFilter.ALL.id) })
                    DshLogChip(label = DshLogFilter.ERROR.label, selected = { filterId() == DshLogFilter.ERROR.id }, onClick = { onFilterChange(DshLogFilter.ERROR.id) })
                    DshLogChip(label = DshLogFilter.CHUNK.label, selected = { filterId() == DshLogFilter.CHUNK.id }, onClick = { onFilterChange(DshLogFilter.CHUNK.id) })
                    DshLogChip(label = DshLogFilter.TOOL_CALL.label, selected = { filterId() == DshLogFilter.TOOL_CALL.id }, onClick = { onFilterChange(DshLogFilter.TOOL_CALL.id) })
                    DshLogChip(label = DshLogFilter.TOOL_RESULT.label, selected = { filterId() == DshLogFilter.TOOL_RESULT.id }, onClick = { onFilterChange(DshLogFilter.TOOL_RESULT.id) })
                    DshLogChip(label = DshLogFilter.TURN_END.label, selected = { filterId() == DshLogFilter.TURN_END.id }, onClick = { onFilterChange(DshLogFilter.TURN_END.id) })
                    DshLogChip(label = DshLogFilter.RECONNECT.label, selected = { filterId() == DshLogFilter.RECONNECT.id }, onClick = { onFilterChange(DshLogFilter.RECONNECT.id) })
                }
                View {
                    attr { marginTop(4f); flexDirectionRow(); flexWrap(FlexWrap.WRAP); alignItemsCenter() }
                    DshLogTextAction("复制", onCopy)
                    DshLogTextAction("导出", onExport)
                    DshLogTextAction("反馈包", onFeedback)
                    DshLogTextAction("清空", onClear)
                }
                vif({ entries().isEmpty() }) {
                    View {
                        attr { flex(1f); allCenter(); padding(24f) }
                        Text { attr { text("没有符合条件的日志"); fontSize(16f); fontWeightMedium(); textAlignCenter(); color(tokens.primaryText) } }
                        Text {
                            attr {
                                text("连接、发消息或切会话后会写入。试试清空筛选。")
                                marginTop(8f)
                                fontSize(13f)
                                lineHeight(19f)
                                textAlignCenter()
                                color(tokens.secondaryText)
                            }
                        }
                    }
                }
                vif({ entries().isNotEmpty() }) {
                    List {
                        attr { flex(1f); marginTop(8f) }
                        vforLazy({ entries() }) { entry, _, _ ->
                            View {
                                attr {
                                    marginBottom(8f)
                                    padding(12f)
                                    borderRadius(12f)
                                    backgroundColor(tokens.surface)
                                }
                                Text {
                                    attr {
                                        text("${entry.level.label} · ${entry.eventType}")
                                        fontSize(13f)
                                        fontWeightMedium()
                                        color(
                                            when (entry.level) {
                                                DshLogLevel.ERROR -> tokens.error.foreground
                                                DshLogLevel.WARN -> tokens.warning.foreground
                                                else -> tokens.primaryText
                                            },
                                        )
                                    }
                                }
                                Text {
                                    attr {
                                        text(if (entry.sessionId.isEmpty()) entry.summary else "${entry.sessionId}  ${entry.summary}")
                                        marginTop(4f)
                                        fontSize(12f)
                                        lineHeight(18f)
                                        color(tokens.secondaryText)
                                    }
                                }
                                DshHitButton { onOpen(entry) }
                            }
                        }
                    }
                }
            }
            vif({ detail() != null }) {
                val current = detail()
                View {
                    attr { height(36f); flexDirectionRow(); alignItemsCenter() }
                    Text {
                        attr {
                            text("返回")
                            width(48f)
                            fontSize(14f)
                            color(tokens.primary)
                        }
                        event { click { onCloseDetail() } }
                    }
                    Text { attr { text("日志详情"); flex(1f); textAlignCenter(); fontSize(17f); fontWeightBold(); color(tokens.primaryText) } }
                    View { attr { size(32f, 32f); allCenter() }; Image { attr { src(ImageUri.commonAssets("x.svg")); size(20f, 20f); tintColor(tokens.icon) } }; DshHitButton { onClose() } }
                }
                Scroller {
                    attr { flex(1f); marginTop(8f) }
                    View {
                        attr { flexDirectionColumn(); paddingBottom(24f) }
                        Text { attr { text(current?.level?.label.orEmpty()); fontSize(12f); color(tokens.captionText) } }
                        Text { attr { text(current?.eventType.orEmpty()); marginTop(8f); fontSize(16f); fontWeightMedium(); color(tokens.primaryText) } }
                        Text {
                            attr {
                                text("sessionId：${current?.sessionId?.ifEmpty { "（无）" } ?: ""}")
                                marginTop(10f)
                                fontSize(13f)
                                color(tokens.secondaryText)
                            }
                        }
                        Text {
                            attr {
                                text("时间：${current?.timeMs ?: 0}")
                                marginTop(6f)
                                fontSize(13f)
                                color(tokens.secondaryText)
                            }
                        }
                        Text {
                            attr {
                                text(current?.summary.orEmpty())
                                marginTop(14f)
                                fontSize(14f)
                                lineHeight(21f)
                                color(tokens.primaryText)
                            }
                        }
                        vif({ current?.sessionId?.isNotEmpty() == true }) {
                            Text {
                                attr {
                                    text("打开对应会话")
                                    marginTop(20f)
                                    fontSize(14f)
                                    color(tokens.primary)
                                }
                                event { click { current?.sessionId?.let(onJump) } }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.DshLogChip(label: String, selected: () -> Boolean, onClick: () -> Unit) {
    View {
        attr {
            height(28f)
            marginRight(8f)
            marginBottom(8f)
            paddingLeft(10f)
            paddingRight(10f)
            borderRadius(14f)
            allCenter()
            backgroundColor(if (selected()) tokens.selectedSurface else tokens.surfaceVariant)
        }
        Text { attr { text(label); fontSize(12f); color(if (selected()) tokens.primary else tokens.secondaryText) } }
        event { click { onClick() } }
        DshHitButton { onClick() }
    }
}

private fun ViewContainer<*, *>.DshLogTextAction(title: String, onClick: () -> Unit) {
    View {
        attr {
            height(28f)
            marginRight(16f)
            marginBottom(8f)
            allCenter()
        }
        Text {
            attr {
                text(title)
                fontSize(14f)
                color(tokens.primary)
            }
        }
        event { click { onClick() } }
        DshHitButton { onClick() }
    }
}
