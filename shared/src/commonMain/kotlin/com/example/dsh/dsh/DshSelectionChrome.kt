package com.example.dsh.dsh

import com.example.dsh.theme.tokens
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ComposeAttr
import com.tencent.kuikly.core.base.ComposeEvent
import com.tencent.kuikly.core.base.ComposeView
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.ViewRef
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.DivView
import com.tencent.kuikly.core.views.SelectableOption
import com.tencent.kuikly.core.views.SelectionType
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal fun ViewContainer<*, *>.DshCopyChip(enabled: () -> Boolean, onCopy: () -> Unit) {
    View {
        attr {
            height(22f)
            paddingLeft(8f)
            paddingRight(8f)
            borderRadius(11f)
            allCenter()
            selectable(SelectableOption.DISABLE)
            backgroundColor(if (enabled()) tokens.surfaceVariant else tokens.disabled.background)
            opacity(if (enabled()) 1f else 0.45f)
        }
        Text {
            attr {
                text("复制")
                fontSize(11f)
                fontWeightMedium()
                color(if (enabled()) tokens.primary else tokens.disabled.foreground)
            }
        }
        DshHitButton { if (enabled()) onCopy() }
    }
}

internal fun ViewContainer<*, *>.DshSelectCheck(selected: Boolean) {
    View {
        attr {
            size(22f, 22f)
            marginRight(10f)
            borderRadius(11f)
            allCenter()
            selectable(SelectableOption.DISABLE)
            border(Border(1.5f, BorderStyle.SOLID, if (selected) tokens.primary else tokens.dividerStrong))
            backgroundColor(if (selected) tokens.primary else Color.TRANSPARENT)
        }
        vif({ selected }) {
            View {
                attr {
                    size(8f, 8f)
                    borderRadius(4f)
                    backgroundColor(tokens.onPrimary)
                }
            }
        }
    }
}

internal fun ViewContainer<*, *>.DshSelectionToolbar(
    x: Float,
    y: Float,
    onCopySelection: () -> Unit,
    onCopyAll: () -> Unit,
    onSelectAll: () -> Unit,
    onEnterSelect: () -> Unit,
) {
    View {
        attr {
            absolutePosition(left = x, top = y)
            height(36f)
            flexDirectionRow()
            alignItemsCenter()
            paddingLeft(4f)
            paddingRight(4f)
            borderRadius(18f)
            backgroundColor(tokens.surfaceElevated)
            border(Border(1f, BorderStyle.SOLID, tokens.divider))
            zIndex(8)
            selectable(SelectableOption.DISABLE)
        }
        DshToolbarAction("复制", onCopySelection)
        DshToolbarDivider()
                        DshToolbarAction("整条", onCopyAll)
                        DshToolbarDivider()
                        DshToolbarAction("全选", onSelectAll)
                        DshToolbarDivider()
                        DshToolbarAction("多选", onEnterSelect)
    }
}

private fun ViewContainer<*, *>.DshToolbarAction(label: String, onClick: () -> Unit) {
    View {
        attr {
            height(36f)
            paddingLeft(10f)
            paddingRight(10f)
            allCenter()
        }
        Text {
            attr {
                text(label)
                fontSize(13f)
                fontWeightMedium()
                color(tokens.primary)
            }
        }
        DshHitButton { onClick() }
    }
}

private fun ViewContainer<*, *>.DshToolbarDivider() {
    View {
        attr {
            width(1f)
            height(16f)
            backgroundColor(tokens.divider)
        }
    }
}

internal fun ViewContainer<*, *>.DshSelectActionBar(
    selectedCount: () -> Int,
    onCopy: () -> Unit,
    onExport: () -> Unit,
) {
    View {
        attr {
            height(64f)
            flexDirectionRow()
            alignItemsCenter()
            paddingLeft(16f)
            paddingRight(16f)
            backgroundColor(tokens.surface)
            borderTop(Border(1f, BorderStyle.SOLID, tokens.divider))
        }
        Text {
            attr {
                text(if (selectedCount() == 0) "点选要导出的消息" else "已选 ${selectedCount()} 条")
                flex(1f)
                fontSize(14f)
                color(tokens.secondaryText)
            }
        }
        View {
            attr {
                height(36f)
                paddingLeft(14f)
                paddingRight(14f)
                borderRadius(18f)
                allCenter()
                backgroundColor(if (selectedCount() == 0) tokens.disabled.background else tokens.surfaceVariant)
            }
            Text {
                attr {
                    text("复制")
                    fontSize(14f)
                    color(if (selectedCount() == 0) tokens.disabled.foreground else tokens.primary)
                }
            }
            DshHitButton { if (selectedCount() > 0) onCopy() }
        }
        View {
            attr {
                height(36f)
                marginLeft(8f)
                paddingLeft(14f)
                paddingRight(14f)
                borderRadius(18f)
                allCenter()
                backgroundColor(if (selectedCount() == 0) tokens.primaryDisabled else tokens.primary)
            }
            Text {
                attr {
                    text("导出 HTML")
                    fontSize(14f)
                    color(tokens.onPrimary)
                }
            }
            DshHitButton { if (selectedCount() > 0) onExport() }
        }
    }
}

internal fun ViewContainer<*, *>.DshMessageOverflowModal(
    visible: () -> Boolean,
    onSelectMessages: () -> Unit,
    onExportSession: () -> Unit,
    onClose: () -> Unit,
) {
    vif({ visible() }) {
        View {
            attr {
                absolutePositionAllZero()
                zIndex(20)
                backgroundColor(tokens.scrim)
                justifyContentFlexEnd()
            }
            event { click { onClose() } }
            View {
                attr {
                    marginLeft(12f)
                    marginRight(12f)
                    marginBottom(pagerData.safeAreaInsets.bottom + 12f)
                    padding(8f)
                    borderRadius(16f)
                    backgroundColor(tokens.surface)
                }
                event { click { } }
                Text {
                    attr {
                        text("选择消息")
                        height(48f)
                        textAlignCenter()
                        fontSize(16f)
                        color(tokens.primaryText)
                    }
                    event { click { onSelectMessages() } }
                }
                View { attr { height(1f); backgroundColor(tokens.divider) } }
                Text {
                    attr {
                        text("导出当前会话")
                        height(48f)
                        textAlignCenter()
                        fontSize(16f)
                        color(tokens.primaryText)
                    }
                    event { click { onExportSession() } }
                }
                View { attr { height(8f); backgroundColor(tokens.background) } }
                Text {
                    attr {
                        text("取消")
                        height(48f)
                        textAlignCenter()
                        fontSize(16f)
                        color(tokens.secondaryText)
                    }
                    event { click { onClose() } }
                }
            }
        }
    }
}

internal class DshSelectableBlockView : ComposeView<DshSelectableBlockAttr, ComposeEvent>() {
    private var menuVisible by observable(false)
    private var menuX by observable(8f)
    private var menuY by observable(4f)
    private var containerRef: ViewRef<DivView>? = null

    override fun createAttr(): DshSelectableBlockAttr = DshSelectableBlockAttr()
    override fun createEvent(): ComposeEvent = ComposeEvent()

    private fun placeMenu(x: Float, y: Float, height: Float) {
        val menuWidth = 248f
        val maxX = (pagerData.pageViewWidth - menuWidth).coerceAtLeast(8f)
        val above = y - 44f
        menuX = x.coerceIn(8f, maxX)
        menuY = if (above >= 4f) above else y + height + 8f
    }

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            View {
                ref { ctx.containerRef = it }
                attr {
                    flexDirectionColumn()
                    selectable(
                        if (ctx.attr.enabled() && !ctx.attr.selectMode()) {
                            SelectableOption.ENABLE
                        } else {
                            SelectableOption.DISABLE
                        },
                    )
                    selectionColor(tokens.primary)
                }
                event {
                    longPress {
                        if (!it.isStart) return@longPress
                        if (ctx.attr.selectMode()) return@longPress
                        if (!ctx.attr.enabled()) return@longPress
                        ctx.containerRef?.view?.createSelection(it.x, it.y, SelectionType.WORD)
                    }
                    selectStart {
                        ctx.menuVisible = false
                    }
                    selectChange { frame ->
                        if (ctx.menuVisible) ctx.placeMenu(frame.x, frame.y, frame.height)
                    }
                    selectEnd { frame ->
                        if (!ctx.attr.enabled() || ctx.attr.selectMode()) return@selectEnd
                        ctx.placeMenu(frame.x, frame.y, frame.height)
                        ctx.menuVisible = true
                    }
                    selectCancel {
                        ctx.menuVisible = false
                    }
                }
                ctx.attr.content.invoke(this)
                vif({ ctx.menuVisible && ctx.attr.enabled() && !ctx.attr.selectMode() }) {
                    DshSelectionToolbar(
                        x = ctx.menuX,
                        y = ctx.menuY,
                        onCopySelection = {
                            ctx.containerRef?.view?.getSelection { result ->
                                val text = result.joinToString("").trim()
                                if (text.isNotEmpty()) {
                                    ctx.attr.onCopySelection(text)
                                }
                                ctx.containerRef?.view?.clearSelection()
                                ctx.menuVisible = false
                            }
                        },
                        onCopyAll = {
                            ctx.attr.onCopyAll()
                            ctx.containerRef?.view?.clearSelection()
                            ctx.menuVisible = false
                        },
                        onSelectAll = {
                            ctx.containerRef?.view?.createSelectionAll()
                        },
                        onEnterSelect = {
                            ctx.containerRef?.view?.clearSelection()
                            ctx.menuVisible = false
                            ctx.attr.onEnterSelect()
                        },
                    )
                }
            }
        }
    }
}

internal class DshSelectableBlockAttr : ComposeAttr() {
    var enabled: () -> Boolean by observable({ true })
    var selectMode: () -> Boolean by observable({ false })
    var onCopySelection: (String) -> Unit by observable({})
    var onCopyAll: () -> Unit by observable({})
    var onEnterSelect: () -> Unit by observable({})
    var content: ViewBuilder = {}
}

internal fun ViewContainer<*, *>.DshSelectableBlock(
    enabled: () -> Boolean,
    selectMode: () -> Boolean,
    onCopySelection: (String) -> Unit,
    onCopyAll: () -> Unit,
    onEnterSelect: () -> Unit,
    content: ViewBuilder,
) {
    addChild(DshSelectableBlockView()) {
        attr {
            this.enabled = enabled
            this.selectMode = selectMode
            this.onCopySelection = onCopySelection
            this.onCopyAll = onCopyAll
            this.onEnterSelect = onEnterSelect
            this.content = content
        }
    }
}
