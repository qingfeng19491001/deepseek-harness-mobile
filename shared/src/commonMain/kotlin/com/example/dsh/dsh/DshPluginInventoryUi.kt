package com.example.dsh.dsh

import com.example.dsh.theme.tokens
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.directives.vfor
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
import com.tencent.kuikly.core.views.compose.Button

internal fun ViewContainer<*, *>.DshPluginInventoryModal(
    loading: () -> Boolean,
    refreshing: () -> Boolean,
    error: () -> String,
    query: () -> String,
    filterId: () -> String,
    writable: () -> Boolean,
    source: () -> String,
    entries: () -> ObservableList<DshPluginEntry>,
    visible: () -> ObservableList<DshPluginEntry>,
    presets: () -> ObservableList<DshPluginPreset>,
    presetId: () -> String,
    onQueryChange: (String) -> Unit,
    onFilterChange: (String) -> Unit,
    onPresetChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpen: (String) -> Unit,
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
            View {
                attr { height(36f); flexDirectionRow(); alignItemsCenter() }
                Text { attr { text("插件"); flex(1f); fontSize(20f); fontWeightBold(); color(tokens.primaryText) } }
                Text {
                    attr {
                        text(if (refreshing()) "刷新中" else "刷新")
                        width(52f)
                        height(32f)
                        textAlignCenter()
                        fontSize(13f)
                        color(if (loading() || refreshing()) tokens.tertiaryText else tokens.primary)
                    }
                    event { click { if (!loading() && !refreshing()) onRefresh() } }
                }
                View { attr { size(32f, 32f); allCenter() }; Image { attr { src(ImageUri.commonAssets("x.svg")); size(20f, 20f); tintColor(tokens.icon) } }; DshHitButton { onClose() } }
            }
            Text {
                attr {
                    text(pluginInventorySubtitle(writable(), source(), entries().size))
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
                        placeholder("搜索名称、模块或错误")
                        placeholderColor(tokens.tertiaryText)
                        returnKeyTypeDone()
                    }
                    event { textDidChange { onQueryChange(it.text) } }
                }
            }
            View {
                attr { marginTop(10f); flexDirectionRow(); flexWrap(FlexWrap.WRAP); alignItemsCenter() }
                DshPluginChip(label = DshPluginFilter.ALL.label, selected = { filterId() == DshPluginFilter.ALL.id }, onClick = { onFilterChange(DshPluginFilter.ALL.id) })
                DshPluginChip(label = DshPluginFilter.ACTIVE.label, selected = { filterId() == DshPluginFilter.ACTIVE.id }, onClick = { onFilterChange(DshPluginFilter.ACTIVE.id) })
                DshPluginChip(label = DshPluginFilter.DISABLED.label, selected = { filterId() == DshPluginFilter.DISABLED.id }, onClick = { onFilterChange(DshPluginFilter.DISABLED.id) })
                DshPluginChip(label = DshPluginFilter.FAILED.label, selected = { filterId() == DshPluginFilter.FAILED.id }, onClick = { onFilterChange(DshPluginFilter.FAILED.id) })
                DshPluginChip(label = DshPluginFilter.TRANSITION.label, selected = { filterId() == DshPluginFilter.TRANSITION.id }, onClick = { onFilterChange(DshPluginFilter.TRANSITION.id) })
            }
            vif({ presets().isNotEmpty() }) {
                View {
                    attr { marginTop(8f); flexDirectionRow(); flexWrap(FlexWrap.WRAP); alignItemsCenter() }
                    DshPluginChip(label = "全局", selected = { presetId().isEmpty() }, onClick = { onPresetChange("") })
                    vfor({ presets() }) { preset ->
                        View {
                            attr { marginRight(0f) }
                            DshPluginChip(
                                label = preset.name,
                                selected = { presetId() == preset.id },
                                onClick = { onPresetChange(preset.id) },
                            )
                        }
                    }
                }
            }
            vif({ loading() && visible().isEmpty() }) {
                DshPluginStateBlock("正在读取 Host 插件…", "首次打开会拉一份当前快照，不会自动订阅后续变化。")
            }
            vif({ error().isNotEmpty() && !loading() && visible().isEmpty() && entries().isEmpty() }) {
                DshPluginStateBlock("无法读取插件列表", error(), action = "重试", onAction = onRefresh)
            }
            vif({ !loading() && error().isEmpty() && entries().isEmpty() }) {
                DshPluginStateBlock("这台 Host 还没有可展示的插件", "官方清单只显示非分组的 Loader 条目。如果刚安装插件，点右上角刷新。")
            }
            vif({ !loading() && entries().isNotEmpty() && visible().isEmpty() }) {
                DshPluginStateBlock(
                    "没有符合条件的插件",
                    "试试清空搜索，或把状态筛选改回「全部」。",
                    action = "清除筛选",
                    onAction = {
                        onQueryChange("")
                        onFilterChange(DshPluginFilter.ALL.id)
                    },
                )
            }
            vif({ visible().isNotEmpty() }) {
                List {
                    attr { flex(1f); marginTop(8f) }
                    vforLazy({ visible() }) { entry, _, _ ->
                        View {
                            attr { marginBottom(8f) }
                            DshPluginCard(entry = entry, onOpen = { onOpen(entry.entryId) })
                        }
                    }
                }
            }
            vif({ source() == "official" && !loading() }) {
                Text {
                    attr {
                        text("当前为只读。在电脑安装 host-plugin 后可查看配置、失败原因，并安全启停。")
                        marginTop(8f)
                        fontSize(11f)
                        lineHeight(16f)
                        color(tokens.tertiaryText)
                    }
                }
            }
        }
    }
}

internal fun ViewContainer<*, *>.DshPluginDetailModal(
    entry: () -> DshPluginEntry?,
    writable: () -> Boolean,
    busy: () -> Boolean,
    error: () -> String,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onReload: () -> Unit,
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
            View {
                attr { height(36f); flexDirectionRow(); alignItemsCenter() }
                Text { attr { text("返回"); width(44f); fontSize(14f); color(tokens.primary) }; event { click { onBack() } } }
                Text { attr { text(entry()?.displayName ?: "插件详情"); flex(1f); textAlignCenter(); fontSize(17f); fontWeightBold(); color(tokens.primaryText) } }
                View { attr { size(32f, 32f); allCenter() }; Image { attr { src(ImageUri.commonAssets("x.svg")); size(20f, 20f); tintColor(tokens.icon) } }; DshHitButton { onClose() } }
            }
            vif({ entry() == null }) {
                DshPluginStateBlock("找不到这个插件", "它可能刚被卸载。返回列表后刷新再试。")
            }
            vif({ entry() != null }) {
                Scroller {
                    attr { flex(1f); marginTop(8f) }
                    View {
                        attr { flexDirectionColumn() }
                        View {
                            attr { flexDirectionRow(); alignItemsCenter() }
                            DshPluginStatusTag(
                                enabled = { entry()?.enabled == true },
                                phase = { entry()?.fiberPhase ?: DshPluginFiberPhase.NONE },
                            )
                        }
                        DshPluginFact("模块") { entry()?.moduleName.orEmpty() }
                        DshPluginFact("条目 ID") { entry()?.entryId.orEmpty() }
                        DshPluginFact("启用状态") { if (entry()?.enabled == true) "已启用" else "已停用" }
                        DshPluginFact("生命周期") { entry()?.fiberPhase?.label.orEmpty() }
                        DshPluginFact("配置摘要") { entry()?.configSummary.orEmpty() }
                        vif({ entry()?.error.orEmpty().isNotEmpty() }) {
                            View {
                                attr {
                                    marginTop(12f)
                                    padding(12f)
                                    borderRadius(10f)
                                    backgroundColor(tokens.error.background)
                                }
                                Text { attr { text("失败原因"); fontSize(12f); color(tokens.error.foreground) } }
                                Text { attr { text(entry()?.error.orEmpty()); marginTop(6f); fontSize(13f); lineHeight(19f); color(tokens.error.foreground) } }
                            }
                        }
                        vif({ entry()?.configJson.orEmpty().isNotEmpty() }) {
                            View {
                                attr {
                                    marginTop(12f)
                                    padding(12f)
                                    borderRadius(10f)
                                    backgroundColor(tokens.surfaceVariant)
                                }
                                Text { attr { text("完整配置（已脱敏）"); fontSize(12f); color(tokens.secondaryText) } }
                                Text { attr { text(entry()?.configJson.orEmpty()); marginTop(6f); fontSize(12f); lineHeight(18f); color(tokens.primaryText) } }
                            }
                        }
                        vif({ entry()?.protectedEntry == true }) {
                            Text {
                                attr {
                                    text(entry()?.protectReason.orEmpty().ifEmpty { "核心或连通性插件，不允许从手机启停。" })
                                    marginTop(12f)
                                    fontSize(12f)
                                    lineHeight(18f)
                                    color(tokens.warning.foreground)
                                }
                            }
                        }
                        vif({ !writable() }) {
                            Text {
                                attr {
                                    text("这份清单是只读的。安装电脑侧管理插件后，才能启用、停用或重载。")
                                    marginTop(12f)
                                    fontSize(12f)
                                    lineHeight(18f)
                                    color(tokens.tertiaryText)
                                }
                            }
                        }
                        vif({ error().isNotEmpty() }) {
                            Text { attr { text(error()); marginTop(10f); fontSize(12f); color(tokens.error.foreground) } }
                        }
                        vif({ writable() && entry()?.protectedEntry == false }) {
                            View {
                                attr { marginTop(16f); flexDirectionRow(); justifyContentFlexEnd() }
                                vif({ entry()?.canDisable == true }) {
                                    DshPluginActionButton("停用", destructive = true, busy = { busy() }, onClick = onDisable)
                                }
                                vif({ entry()?.canEnable == true }) {
                                    DshPluginActionButton("启用", destructive = false, busy = { busy() }, onClick = onEnable)
                                }
                                vif({ entry()?.canReload == true }) {
                                    View { attr { width(8f) } }
                                    DshPluginActionButton("重载", destructive = false, busy = { busy() }, onClick = onReload)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun ViewContainer<*, *>.DshPluginConfirmModal(
    action: () -> String,
    entry: () -> DshPluginEntry?,
    busy: () -> Boolean,
    error: () -> String,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
) {
    Modal(inWindow = true) {
        attr { absolutePositionAllZero(); allCenter(); padding(20f); backgroundColor(tokens.scrim) }
        View {
            attr {
                width(pagerData.pageViewWidth - 40f)
                maxWidth(420f)
                padding(22f)
                borderRadius(16f)
                backgroundColor(tokens.surface)
            }
            Text { attr { text("${pluginControlLabel(action())}插件？"); fontSize(18f); fontWeightBold(); color(tokens.primaryText) } }
            Text {
                attr {
                    text(pluginControlPrompt(action(), entry()?.displayName ?: "该插件"))
                    marginTop(10f)
                    fontSize(14f)
                    lineHeight(21f)
                    color(tokens.secondaryText)
                }
            }
            vif({ error().isNotEmpty() }) {
                Text { attr { text(error()); marginTop(10f); fontSize(12f); color(tokens.error.foreground) } }
            }
            View {
                attr { marginTop(18f); height(40f); flexDirectionRow(); justifyContentFlexEnd() }
                Text {
                    attr {
                        text("取消")
                        width(64f)
                        height(40f)
                        textAlignCenter()
                        fontSize(14f)
                        color(tokens.secondaryText)
                    }
                    event { click { if (!busy()) onClose() } }
                }
                Button {
                    attr {
                        width(112f)
                        height(40f)
                        borderRadius(8f)
                        backgroundColor(
                            when {
                                busy() -> tokens.primaryDisabled
                                action() == "disable" -> tokens.error.foreground
                                else -> tokens.primary
                            },
                        )
                        titleAttr { text(if (busy()) "处理中..." else pluginControlLabel(action())); fontSize(14f); color(tokens.onPrimary) }
                    }
                    event { click { if (!busy()) onConfirm() } }
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.DshPluginCard(entry: DshPluginEntry, onOpen: () -> Unit) {
    View {
        attr {
            padding(12f)
            borderRadius(12f)
            backgroundColor(if (entry.failed) tokens.error.background else tokens.surface)
            border(Border(1f, BorderStyle.SOLID, if (entry.failed) tokens.error.foreground else tokens.divider))
        }
        View {
            attr { flexDirectionRow(); alignItemsCenter() }
            Text { attr { text(entry.displayName); flex(1f); lines(1); fontSize(15f); fontWeightMedium(); color(tokens.primaryText) } }
            DshPluginStatusTag(enabled = { entry.enabled }, phase = { entry.fiberPhase })
        }
        Text { attr { text(entry.moduleName); marginTop(4f); lines(1); fontSize(11f); color(tokens.tertiaryText) } }
        Text { attr { text(entry.configSummary); marginTop(6f); lines(2); fontSize(12f); color(tokens.secondaryText) } }
        vif({ entry.error.isNotEmpty() }) {
            Text { attr { text(entry.error); marginTop(6f); lines(2); fontSize(12f); color(tokens.error.foreground) } }
        }
        event { click { onOpen() } }
    }
}

private fun ViewContainer<*, *>.DshPluginStatusTag(enabled: () -> Boolean, phase: () -> DshPluginFiberPhase) {
    View {
        attr {
            height(22f)
            paddingLeft(8f)
            paddingRight(8f)
            borderRadius(11f)
            allCenter()
            backgroundColor(
                when {
                    phase() == DshPluginFiberPhase.FAILED -> tokens.error.background
                    !enabled() -> tokens.disabled.background
                    phase() == DshPluginFiberPhase.ACTIVE -> tokens.success.background
                    phase() == DshPluginFiberPhase.LOADING || phase() == DshPluginFiberPhase.UNLOADING -> tokens.running.background
                    else -> tokens.warning.background
                },
            )
        }
        Text {
            attr {
                text(
                    when {
                        phase() == DshPluginFiberPhase.FAILED -> "失败"
                        !enabled() -> "已停用"
                        else -> phase().label
                    },
                )
                fontSize(11f)
                color(
                    when {
                        phase() == DshPluginFiberPhase.FAILED -> tokens.error.foreground
                        !enabled() -> tokens.disabled.foreground
                        phase() == DshPluginFiberPhase.ACTIVE -> tokens.success.foreground
                        phase() == DshPluginFiberPhase.LOADING || phase() == DshPluginFiberPhase.UNLOADING -> tokens.running.foreground
                        else -> tokens.warning.foreground
                    },
                )
            }
        }
    }
}

private fun ViewContainer<*, *>.DshPluginChip(label: String, selected: () -> Boolean, onClick: () -> Unit) {
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

private fun ViewContainer<*, *>.DshPluginFact(label: String, value: () -> String) {
    View {
        attr { marginTop(12f); flexDirectionColumn() }
        Text { attr { text(label); fontSize(12f); color(tokens.secondaryText) } }
        Text { attr { text(value()); marginTop(4f); fontSize(14f); lineHeight(20f); color(tokens.primaryText) } }
    }
}

private fun ViewContainer<*, *>.DshPluginStateBlock(
    title: String,
    body: String,
    action: String = "",
    onAction: () -> Unit = {},
) {
    View {
        attr { flex(1f); allCenter(); padding(24f) }
        Text { attr { text(title); fontSize(16f); fontWeightMedium(); textAlignCenter(); color(tokens.primaryText) } }
        Text { attr { text(body); marginTop(8f); fontSize(13f); lineHeight(19f); textAlignCenter(); color(tokens.secondaryText) } }
        vif({ action.isNotEmpty() }) {
            Text {
                attr {
                    text(action)
                    marginTop(16f)
                    fontSize(14f)
                    color(tokens.primary)
                }
                event { click { onAction() } }
            }
        }
    }
}

private fun ViewContainer<*, *>.DshPluginActionButton(
    title: String,
    destructive: Boolean,
    busy: () -> Boolean,
    onClick: () -> Unit,
) {
    Button {
        attr {
            width(88f)
            height(40f)
            borderRadius(8f)
            backgroundColor(
                when {
                    busy() -> tokens.primaryDisabled
                    destructive -> tokens.error.foreground
                    else -> tokens.primary
                },
            )
            titleAttr { text(title); fontSize(14f); color(tokens.onPrimary) }
        }
        event { click { if (!busy()) onClick() } }
    }
}

internal fun pluginInventorySubtitle(writable: Boolean, source: String, count: Int): String {
    val mode = if (writable) "可管理" else "只读"
    val origin = if (source == "admin") "已接入管理插件" else "来自 Host 官方清单"
    return if (count > 0) "$mode · $origin · ${count} 个插件" else "$mode · $origin"
}
