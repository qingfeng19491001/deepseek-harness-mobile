package com.example.dsh.dsh

import com.example.dsh.theme.tokens
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.directives.velse
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.vforLazy
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Input
import com.tencent.kuikly.core.views.InputView
import com.tencent.kuikly.core.views.List
import com.tencent.kuikly.core.views.Modal
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.tencent.kuikly.core.views.compose.Button

internal fun ViewContainer<*, *>.DshConnectionSettingsModal(
    sshMode: () -> Boolean,
    host: () -> String,
    user: () -> String,
    port: () -> String,
    dshPort: () -> String,
    keyLabel: () -> String,
    keyPassphrase: () -> String,
    busy: () -> Boolean,
    error: () -> String,
    onModeChange: (Boolean) -> Unit,
    onHostChange: (String) -> Unit,
    onUserChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onDshPortChange: (String) -> Unit,
    onPickKey: () -> Unit,
    onPassphraseChange: (String) -> Unit,
    onTrustFingerprint: () -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    onOpenApiKey: () -> Unit,
) {
    Modal(inWindow = true) {
        attr { absolutePositionAllZero(); allCenter(); backgroundColor(tokens.scrim); padding(20f) }
        View {
            attr {
                width(pagerData.pageViewWidth - 40f)
                maxWidth(440f)
                flexDirectionColumn()
                padding(22f)
                borderRadius(16f)
                backgroundColor(tokens.surface)
            }
            View {
                attr { height(32f); flexDirectionRow(); alignItemsCenter() }
                Text { attr { text("连接设置"); flex(1f); fontSize(20f); fontWeightBold(); color(tokens.primaryText) } }
                View { attr { size(32f, 32f); allCenter() }; Image { attr { src(ImageUri.commonAssets("x.svg")); size(20f, 20f); tintColor(tokens.icon) } }; DshHitButton { if (!busy()) onClose() } }
            }
            Text { attr { text("选择 Agent 运行位置"); marginTop(16f); fontSize(13f); color(tokens.secondaryText) } }
            View {
                attr { height(42f); marginTop(8f); flexDirectionRow(); borderRadius(8f); backgroundColor(tokens.surfaceVariant); padding(4f) }
                View {
                    attr { flex(1f); height(34f); flexDirectionRow(); alignItemsCenter(); justifyContentCenter(); backgroundColor(if (!sshMode()) tokens.surfaceElevated else Color.TRANSPARENT); borderRadius(6f) }
                    Text { attr { text("扫码连接"); fontSize(13f); color(if (!sshMode()) tokens.primary else tokens.secondaryText) } }
                    event { click { onModeChange(false) } }
                }
                View {
                    attr { flex(1f); height(34f); flexDirectionRow(); alignItemsCenter(); justifyContentCenter(); backgroundColor(if (sshMode()) tokens.surfaceElevated else Color.TRANSPARENT); borderRadius(6f) }
                    Text { attr { text("SSH 连接电脑"); fontSize(13f); color(if (sshMode()) tokens.primary else tokens.secondaryText) } }
                    event { click { onModeChange(true) } }
                }
            }
            vif({ !sshMode() }) {
                Text { attr { text("扫码模式连接电脑上的 DSH。返回连接页可重新扫码或更换电脑。"); marginTop(16f); fontSize(14f); lineHeight(21f); color(tokens.secondaryText) } }
                View {
                    attr { height(40f); marginTop(16f); flexDirectionRow(); justifyContentFlexEnd() }
                    Button { attr { width(132f); height(40f); borderRadius(8f); backgroundColor(tokens.primary); titleAttr { text("返回连接页"); fontSize(14f); color(tokens.onPrimary) } }; event { click { if (!busy()) onSave() } } }
                }
            }
            velse {
                DshConnectionInput("SSH 主机", host, "例如 100.86.12.34 或 computer.example.com", onHostChange)
                DshConnectionInput("SSH 用户名", user, "例如 alex", onUserChange)
                View { attr { flexDirectionRow(); marginTop(12f) }; DshConnectionInput("SSH 端口", port, "22", onPortChange, 0.5f); DshConnectionInput("远程 DSH 端口", dshPort, "3080", onDshPortChange, 0.5f, 10f) }
                View {
                    attr { height(44f); marginTop(12f); flexDirectionRow(); alignItemsCenter(); paddingLeft(12f); paddingRight(10f); borderRadius(8f); backgroundColor(tokens.surfaceVariant) }
                    Text { attr { text(keyLabel()); flex(1f); fontSize(13f); color(tokens.secondaryText) } }
                    Text { attr { text(if (busy()) "导入中..." else "选择私钥"); fontSize(13f); color(tokens.primary) }; event { click { if (!busy()) onPickKey() } } }
                }
                DshConnectionInput("私钥口令（如有）", keyPassphrase, "仅本次连接使用", onPassphraseChange, password = true)
                vif({ error().startsWith("首次连接需要确认主机指纹：") }) {
                    View {
                        attr { marginTop(10f); padding(10f); borderRadius(8f); backgroundColor(tokens.warning.background) }
                        Text { attr { text("请确认这是你电脑的 SSH 主机指纹。确认后会保存，指纹变化时连接将被拒绝。"); fontSize(12f); lineHeight(18f); color(tokens.warning.foreground) } }
                        Text { attr { text("信任此指纹并连接"); marginTop(8f); fontSize(13f); color(tokens.primary) }; event { click { if (!busy()) onTrustFingerprint() } } }
                    }
                }
                vif({ error().isNotEmpty() && !error().startsWith("首次连接需要确认主机指纹：") }) {
                    Text { attr { text(error()); marginTop(8f); fontSize(12f); lineHeight(18f); color(tokens.error.foreground) } }
                }
                View { attr { marginTop(18f); height(40f); flexDirectionRow(); justifyContentFlexEnd() }; Button { attr { width(132f); height(40f); borderRadius(8f); backgroundColor(if (busy()) tokens.primaryDisabled else tokens.primary); titleAttr { text(if (busy()) "连接中..." else "保存并连接"); fontSize(14f); color(tokens.onPrimary) } }; event { click { if (!busy()) onSave() } } } }
            }
        }
    }
}

internal fun ViewContainer<*, *>.DshConnectionInput(
    title: String,
    value: () -> String,
    hint: String,
    onChange: (String) -> Unit,
    flexValue: Float = 1f,
    marginLeft: Float = 0f,
    password: Boolean = false,
) {
    View {
        attr { flex(flexValue); marginLeft(marginLeft); flexDirectionColumn() }
        Text { attr { text(title); marginTop(10f); fontSize(12f); color(tokens.secondaryText) } }
        View {
            attr { height(40f); marginTop(5f); borderRadius(8f); border(Border(1f, BorderStyle.SOLID, tokens.divider)); backgroundColor(tokens.background); paddingLeft(10f); paddingRight(10f) }
            Input {
                ref { it.view?.setText(value()) }
                attr { flex(1f); fontSize(14f); color(tokens.primaryText); placeholder(hint); placeholderColor(tokens.tertiaryText); returnKeyTypeDone(); if (password) keyboardTypePassword() }
                event { textDidChange { onChange(it.text) } }
            }
        }
    }
}

internal fun ViewContainer<*, *>.DshCredentialSetupModal(
    title: () -> String,
    busy: () -> Boolean,
    error: () -> String,
    inputRef: (ViewRef<InputView>) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onSave: () -> Unit,
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
                width(pagerData.pageViewWidth - 40f)
                maxWidth(420f)
                flexDirectionColumn()
                padding(24f)
                borderRadius(18f)
                backgroundColor(tokens.surface)
            }
            View {
                attr {
                    height(32f)
                    flexDirectionRow()
                    alignItemsCenter()
                }
                Text {
                    attr {
                        text(title())
                        flex(1f)
                        fontSize(20f)
                        fontWeightBold()
                        color(tokens.primaryText)
                    }
                }
                View {
                    attr {
                        size(32f, 32f)
                        allCenter()
                    }
                    Image {
                        attr {
                            src(ImageUri.commonAssets("x.svg"))
                            size(20f, 20f)
                        }
                    }
                    DshHitButton { if (!busy()) onClose() }
                }
            }
            Text {
                attr {
                    text(if (title().contains("电脑端")) "确认后将修改电脑端 DSH 的凭据。" else "配置 DeepSeek 官方模型，即可开始使用。")
                    marginTop(8f)
                    fontSize(14f)
                    lineHeight(21f)
                    color(tokens.secondaryText)
                }
            }
            Text {
                attr {
                    text("API Key")
                    marginTop(22f)
                    fontSize(13f)
                    fontWeightMedium()
                    color(tokens.primaryText)
                }
            }
            View {
                attr {
                    height(46f)
                    marginTop(8f)
                    borderRadius(8f)
                    border(Border(1f, BorderStyle.SOLID, if (error().isEmpty()) tokens.divider else tokens.error.foreground))
                    backgroundColor(tokens.background)
                    paddingLeft(12f)
                    paddingRight(12f)
                }
                Input {
                    ref { inputRef(it) }
                    attr {
                        flex(1f)
                        fontSize(15f)
                        color(tokens.primaryText)
                        placeholder("输入 DeepSeek API Key")
                        placeholderColor(tokens.tertiaryText)
                        keyboardTypePassword()
                        returnKeyTypeDone()
                        autofocus(true)
                        editable(!busy())
                    }
                    event {
                        textDidChange { onApiKeyChange(it.text) }
                        inputReturn { if (!busy()) onSave() }
                    }
                }
            }
            vif({ error().isNotEmpty() }) {
                Text {
                    attr {
                        text(error())
                        marginTop(8f)
                        fontSize(12f)
                        lineHeight(18f)
                        color(tokens.error.foreground)
                    }
                }
            }
            View {
                attr {
                    marginTop(24f)
                    height(40f)
                    flexDirectionRow()
                    justifyContentFlexEnd()
                }
                Button {
                    attr {
                        width(132f)
                        height(40f)
                        borderRadius(8f)
                        backgroundColor(if (busy()) tokens.primaryDisabled else tokens.primary)
                        titleAttr {
                            text(if (busy()) "保存中..." else "保存并继续")
                            fontSize(14f)
                            color(tokens.onPrimary)
                        }
                    }
                    event { click { if (!busy()) onSave() } }
                }
            }
        }
    }
}

private const val DSH_WORDMARK_RATIO = 143f / 23f

internal fun ViewContainer<*, *>.DshWordmark(height: Float = 22f) {
    Image {
        attr {
            src(ImageUri.commonAssets("wordmark.svg"))
            width(height * DSH_WORDMARK_RATIO)
            height(height)
            resizeContain()
            tintColor(tokens.primaryText)
        }
    }
}

internal fun ViewContainer<*, *>.DshSessionDrawer(
    sessions: () -> ObservableList<DshSession>,
    workspaceGroups: () -> ObservableList<DshWorkspaceGroup>,
    archivedSessions: () -> ObservableList<DshSession>,
    isWebTimeline: () -> Boolean,
    activeId: () -> String,
    animated: () -> Boolean,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenPlugins: () -> Unit,
    onNewSession: () -> Unit,
    onOpenArchived: () -> Unit,
    sort: () -> DshSessionSort,
    onSort: (DshSessionSort) -> Unit,
    onManage: (String) -> Unit,
    onSelect: (String) -> Unit,
) {
    Modal(inWindow = true) {
        attr {
            absolutePositionAllZero()
            flexDirectionRow()
            backgroundColor(Color.TRANSPARENT)
        }
        View {
            attr {
                width((pagerData.pageViewWidth - 44f).coerceAtMost(340f))
                height(pagerData.pageViewHeight)
                flexDirectionColumn()
                paddingTop(pagerData.statusBarHeight + 10f)
                paddingLeft(14f)
                paddingRight(14f)
                paddingBottom(18f)
                backgroundColor(tokens.background)
                transform(Translate(if (animated()) 0f else -1f, 0f))
                animation(Animation.easeOut(0.24f), animated())
            }
            View {
                attr {
                    height(48f)
                    flexDirectionRow()
                    alignItemsCenter()
                }
                DshWordmark()
                View { attr { flex(1f) } }
                View {
                    attr { size(38f, 38f); allCenter() }
                    Image { attr { src(ImageUri.commonAssets("x.svg")); size(22f, 22f); tintColor(tokens.icon) } }
                    event { click { onClose() } }
                }
            }
            View {
                attr {
                    height(42f)
                    marginTop(8f)
                    flexDirectionRow()
                    alignItemsCenter()
                    paddingLeft(12f)
                    paddingRight(12f)
                    borderRadius(9f)
                    backgroundColor(tokens.surfaceVariant)
                }
                Image { attr { src(ImageUri.commonAssets("plus.svg")); size(20f, 20f); tintColor(tokens.icon) } }
                Text {
                    attr {
                        text("新会话")
                        marginLeft(10f)
                        fontSize(14f)
                        fontWeightMedium()
                        color(tokens.primaryText)
                    }
                }
                event { click { onNewSession() } }
            }
            View {
                attr {
                    height(42f)
                    marginTop(8f)
                    flexDirectionRow()
                    alignItemsCenter()
                    paddingLeft(12f)
                    paddingRight(12f)
                    borderRadius(9f)
                    backgroundColor(Color.TRANSPARENT)
                }
                Image { attr { src(ImageUri.commonAssets("appearance.svg")); size(20f, 20f); tintColor(tokens.icon) } }
                Text {
                    attr {
                        text("外观")
                        marginLeft(10f)
                        fontSize(14f)
                        fontWeightMedium()
                        color(tokens.secondaryText)
                    }
                }
                event { click { onOpenAppearance() } }
            }
            View {
                attr {
                    height(42f)
                    marginTop(8f)
                    flexDirectionRow()
                    alignItemsCenter()
                    paddingLeft(12f)
                    paddingRight(12f)
                    borderRadius(9f)
                    backgroundColor(Color.TRANSPARENT)
                }
                Image { attr { src(ImageUri.commonAssets("plugin.svg")); size(20f, 20f); tintColor(tokens.icon) } }
                Text {
                    attr {
                        text("插件")
                        marginLeft(10f)
                        fontSize(14f)
                        fontWeightMedium()
                        color(tokens.secondaryText)
                    }
                }
                event { click { onOpenPlugins() } }
            }
            View {
                attr {
                    height(42f)
                    marginTop(8f)
                    flexDirectionRow()
                    alignItemsCenter()
                    paddingLeft(12f)
                    paddingRight(12f)
                    borderRadius(9f)
                    backgroundColor(Color.TRANSPARENT)
                }
                Image { attr { src(ImageUri.commonAssets("sliders.svg")); size(20f, 20f); tintColor(tokens.icon) } }
                Text {
                    attr {
                        text("设置")
                        marginLeft(10f)
                        fontSize(14f)
                        fontWeightMedium()
                        color(tokens.secondaryText)
                    }
                }
                event { click { onOpenSettings() } }
            }
            View {
                attr {
                    marginTop(20f)
                    marginBottom(8f)
                    flexDirectionRow()
                    alignItemsCenter()
                }
                Text {
                    attr {
                        text("会话")
                        flex(1f)
                        fontSize(12f)
                        color(tokens.captionText)
                    }
                }
                DshSessionSortChip("最近", selected = { sort() == DshSessionSort.RECENT }) {
                    onSort(DshSessionSort.RECENT)
                }
                DshSessionSortChip("名称", selected = { sort() == DshSessionSort.TITLE }) {
                    onSort(DshSessionSort.TITLE)
                }
            }
            vif({ isWebTimeline() }) {
                View {
                    attr {
                        height(40f)
                        marginBottom(8f)
                        flexDirectionRow()
                        alignItemsCenter()
                        paddingLeft(12f)
                        paddingRight(12f)
                        borderRadius(9f)
                        backgroundColor(tokens.surfaceVariant)
                    }
                    Text {
                        attr {
                            text("已归档会话")
                            flex(1f)
                            fontSize(13f)
                            color(tokens.secondaryText)
                        }
                    }
                    Text {
                        attr {
                            text("${archivedSessions().size}")
                            fontSize(12f)
                            color(tokens.tertiaryText)
                        }
                    }
                    event { click { onOpenArchived() } }
                }
            }
            Scroller {
                attr { flex(1f) }
                vif({ !isWebTimeline() }) {
                    vfor({ sessions() }) { session ->
                        DshSessionDrawerRow(
                            title = session.title,
                            subtitle = session.workspace,
                            active = activeId() == session.id,
                            running = session.running,
                            onSelect = { onSelect(session.id) },
                            onLongPress = { onManage(session.id) },
                        )
                    }
                }
                vif({ isWebTimeline() }) {
                    vfor({ workspaceGroups() }) { group ->
                        View {
                            attr {
                                marginTop(10f)
                                marginBottom(6f)
                                flexDirectionColumn()
                            }
                            Text {
                                attr {
                                    text(group.title + if (group.path.isEmpty()) "" else " · ${group.path}")
                                    lines(1)
                                    fontSize(12f)
                                    fontWeightMedium()
                                    color(tokens.secondaryText)
                                }
                            }
                            group.sessions.forEach { session ->
                                DshSessionDrawerRow(
                                    title = session.title,
                                    subtitle = if (session.cwd.isEmpty()) group.title else session.cwd,
                                    active = activeId() == session.id,
                                    running = session.running,
                                    onSelect = { onSelect(session.id) },
                                    onLongPress = { onManage(session.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
        View {
            attr {
                flex(1f)
                height(pagerData.pageViewHeight)
            }
            event { click { onClose() } }
        }
    }
}

internal fun ViewContainer<*, *>.DshSessionSortChip(
    title: String,
    selected: () -> Boolean,
    onClick: () -> Unit,
) {
    View {
        attr {
            height(24f)
            marginLeft(6f)
            paddingLeft(8f)
            paddingRight(8f)
            borderRadius(12f)
            justifyContentCenter()
            backgroundColor(if (selected()) tokens.selectedSurface else tokens.surfaceVariant)
        }
        Text {
            attr {
                text(title)
                fontSize(11f)
                color(if (selected()) tokens.primary else tokens.secondaryText)
            }
        }
        event { click { onClick() } }
    }
}

internal fun ViewContainer<*, *>.DshSessionDrawerRow(
    title: String,
    subtitle: String,
    active: Boolean,
    running: Boolean,
    onSelect: () -> Unit,
    onLongPress: (() -> Unit)? = null,
) {
    View {
        attr {
            height(48f)
            marginBottom(4f)
            flexDirectionRow()
            alignItemsCenter()
            paddingLeft(12f)
            paddingRight(10f)
            borderRadius(9f)
            backgroundColor(if (active) tokens.selectedSurface else Color.TRANSPARENT)
        }
        View {
            attr {
                size(7f, 7f)
                borderRadius(4f)
                backgroundColor(if (running) tokens.primary else tokens.tertiaryText)
            }
        }
        View {
            attr {
                flex(1f)
                marginLeft(10f)
                flexDirectionColumn()
                justifyContentCenter()
            }
            Text {
                attr {
                    text(title)
                    lines(1)
                    fontSize(14f)
                    color(tokens.primaryText)
                }
            }
            Text {
                attr {
                    text(subtitle)
                    lines(1)
                    marginTop(2f)
                    fontSize(10f)
                    color(tokens.tertiaryText)
                }
            }
        }
        event {
            click { onSelect() }
            if (onLongPress != null) {
                longPress { params ->
                    if (params.state == "start") onLongPress()
                }
            }
        }
    }
}

internal fun ViewContainer<*, *>.DshModelPicker(
    options: () -> ObservableList<DshModelOption>,
    busy: () -> Boolean,
    error: () -> String,
    onClose: () -> Unit,
    onSelect: (DshModelOption) -> Unit,
) {
    Modal(inWindow = true) {
        attr {
            absolutePositionAllZero()
            flexDirectionColumn()
            justifyContentFlexEnd()
            backgroundColor(tokens.scrim)
        }
        View {
            attr { flex(1f) }
            event { click { onClose() } }
        }
        View {
            attr {
                height((pagerData.pageViewHeight * 0.62f).coerceAtMost(540f))
                flexDirectionColumn()
                padding(18f)
                borderRadius(20f)
                backgroundColor(tokens.surface)
            }
            View {
                attr { height(40f); flexDirectionRow(); alignItemsCenter() }
                Text {
                    attr {
                        text("选择模型")
                        fontSize(18f)
                        fontWeightBold()
                        color(tokens.primaryText)
                    }
                }
                View { attr { flex(1f) } }
                View {
                    attr { size(36f, 36f); allCenter() }
                    Image { attr { src(ImageUri.commonAssets("x.svg")); size(21f, 21f); tintColor(tokens.icon) } }
                    event { click { onClose() } }
                }
            }
            vif({ error().isNotEmpty() }) {
                Text {
                    attr {
                        text(error())
                        marginTop(6f)
                        marginBottom(6f)
                        fontSize(12f)
                        color(tokens.error.foreground)
                    }
                }
            }
            vif({ busy() && options().isEmpty() }) {
                Text {
                    attr {
                        text("正在加载模型...")
                        marginTop(24f)
                        fontSize(14f)
                        color(tokens.secondaryText)
                    }
                }
            }
            Scroller {
                attr { flex(1f); marginTop(8f) }
                vfor({ options() }) { option ->
                    View {
                        attr {
                            minHeight(58f)
                            marginBottom(6f)
                            flexDirectionRow()
                            alignItemsCenter()
                            padding(10f, 12f, 10f, 12f)
                            borderRadius(10f)
                            backgroundColor(if (option.selected) tokens.selectedSurface else tokens.surfaceVariant)
                        }
                        View {
                            attr { flex(1f); flexDirectionColumn() }
                            Text {
                                attr {
                                    text(option.name)
                                    fontSize(14f)
                                    fontWeightMedium()
                                    color(tokens.primaryText)
                                }
                            }
                            Text {
                                attr {
                                    text(option.providerName + if (option.description.isEmpty()) "" else " · ${option.description}")
                                    marginTop(3f)
                                    lines(1)
                                    fontSize(11f)
                                    color(tokens.tertiaryText)
                                }
                            }
                        }
                        if (option.selected) {
                            Text { attr { text("✓"); fontSize(17f); color(tokens.primary) } }
                        }
                        event { click { if (!busy()) onSelect(option) } }
                    }
                }
            }
        }
    }
}

internal fun ViewContainer<*, *>.DshTopBar(
    title: () -> String,
    connection: () -> String,
    archived: () -> Boolean = { false },
    selectMode: () -> Boolean = { false },
    selectedCount: () -> Int = { 0 },
    allSelected: () -> Boolean = { false },
    onOpenDrawer: () -> Unit,
    onManage: (() -> Unit)? = null,
    onOverflow: () -> Unit = {},
    onCancelSelect: () -> Unit = {},
    onToggleSelectAll: () -> Unit = {},
) {
    View {
        attr {
            height(58f)
            flexDirectionRow()
            alignItemsCenter()
            paddingLeft(12f)
            paddingRight(14f)
            backgroundColor(tokens.surface)
            borderBottom(Border(1f, BorderStyle.SOLID, tokens.divider))
        }
        vif({ selectMode() }) {
            Text {
                attr {
                    text("取消")
                    width(52f)
                    height(36f)
                    textAlignCenter()
                    fontSize(16f)
                    color(tokens.primary)
                }
                event { click { onCancelSelect() } }
            }
            Text {
                attr {
                    text(if (selectedCount() == 0) "选择消息" else "已选 ${selectedCount()} 条")
                    flex(1f)
                    textAlignCenter()
                    fontSize(17f)
                    fontWeightMedium()
                    color(tokens.primaryText)
                    lines(1)
                }
            }
            Text {
                attr {
                    text(if (allSelected()) "全不选" else "全选")
                    width(56f)
                    height(36f)
                    textAlignCenter()
                    fontSize(15f)
                    color(tokens.primary)
                }
                event { click { onToggleSelectAll() } }
            }
        }
        velse {
            View {
                attr { size(38f, 38f); allCenter() }
                Image {
                    attr {
                        src(ImageUri.commonAssets("menu.svg"))
                        size(26f, 26f)
                        tintColor(tokens.icon)
                    }
                }
                event { click { onOpenDrawer() } }
            }
            View {
                attr {
                    flex(1f)
                    marginLeft(10f)
                    flexDirectionColumn()
                    justifyContentCenter()
                }
                Text {
                    attr {
                        text(title())
                        fontSize(17f)
                        fontWeightMedium()
                        color(tokens.primaryText)
                        lines(1)
                    }
                }
                vif({ archived() }) {
                    Text {
                        attr {
                            text("已归档 · 仅查看历史")
                            marginTop(1f)
                            fontSize(10f)
                            color(tokens.captionText)
                        }
                    }
                }
            }
            View {
                attr {
                    val ready = isConnectionReadyLabel(connection())
                    height(22f)
                    marginLeft(8f)
                    paddingLeft(8f)
                    paddingRight(8f)
                    borderRadius(11f)
                    backgroundColor(if (ready) tokens.success.background else tokens.disabled.background)
                    justifyContentCenter()
                    alignItemsCenter()
                }
                Text {
                    attr {
                        val ready = isConnectionReadyLabel(connection())
                        text(if (ready) "已连接" else topBarConnectingText(connection()))
                        fontSize(11f)
                        lines(1)
                        color(if (ready) tokens.success.foreground else tokens.disabled.foreground)
                    }
                }
            }
            View {
                attr {
                    size(34f, 34f)
                    marginLeft(4f)
                    allCenter()
                }
                Text {
                    attr {
                        text("···")
                        fontSize(18f)
                        fontWeightBold()
                        color(tokens.icon)
                    }
                }
                event { click { onOverflow() } }
            }
        }
    }
}

internal fun ViewContainer<*, *>.DshSessionRail(
    sessions: () -> ObservableList<DshSession>,
    activeId: () -> String,
    compact: Boolean,
    onSelect: (String) -> Unit,
    onManage: (String) -> Unit = {},
) {
    View {
        attr {
            if (compact) {
                height(92f)
                flexDirectionRow()
            } else {
                width(236f)
                flexDirectionColumn()
            }
            backgroundColor(tokens.surfaceVariant)
            padding(14f)
        }
        Text {
            attr {
                text("会话")
                fontSize(13f)
                color(tokens.secondaryText)
                marginBottom(9f)
            }
        }
        if (compact) {
            Scroller {
                attr {
                    flex(1f)
                    flexDirectionRow()
                }
                vfor({ sessions() }) { session ->
                    DshSessionButton(session, activeId() == session.id, onSelect, onManage)
                }
            }
        } else {
            Scroller {
                attr { flex(1f) }
                vfor({ sessions() }) { session ->
                    DshSessionButton(session, activeId() == session.id, onSelect, onManage)
                }
            }
        }
    }
}

internal fun ViewContainer<*, *>.DshSessionButton(
    session: DshSession,
    active: Boolean,
    onSelect: (String) -> Unit,
    onManage: (String) -> Unit = {},
) {
    Button {
        attr {
            height(48f)
            width(if (active) 220f else 220f)
            marginBottom(4f)
            borderRadius(7f)
            backgroundColor(if (active) tokens.selectedSurface else Color.TRANSPARENT)
            titleAttr {
                text(session.title)
                color(if (active) tokens.primary else tokens.primaryText)
                fontSize(13f)
            }
        }
        event {
            click { onSelect(session.id) }
            longPress { params ->
                if (params.state == "start") onManage(session.id)
            }
        }
    }
}

internal fun ViewContainer<*, *>.DshSessionDetailsPanel(
    title: () -> String,
    cwd: () -> String,
    modelLabel: () -> String,
    agentPreset: () -> String,
    running: () -> Boolean,
    queueCount: () -> Int,
    jobCount: () -> Int,
    archived: () -> Boolean,
    canUnarchive: () -> Boolean = { false },
    canDelete: () -> Boolean = { false },
    onRename: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit = {},
    onDelete: () -> Unit = {},
    onSelectMessages: () -> Unit = {},
    onExportSession: () -> Unit = {},
) {
    View {
        attr {
            width(280f)
            height(pagerData.pageViewHeight)
            flexDirectionColumn()
            padding(16f)
            backgroundColor(tokens.surfaceVariant)
            border(Border(1f, BorderStyle.SOLID, tokens.divider))
        }
        Text {
            attr {
                text("Session")
                fontSize(12f)
                color(tokens.secondaryText)
            }
        }
        Text {
            attr {
                text(title())
                marginTop(6f)
                fontSize(17f)
                fontWeightSemiBold()
                color(tokens.primaryText)
                lines(2)
            }
        }
        View {
            attr {
                height(1f)
                marginTop(14f)
                backgroundColor(tokens.divider)
            }
        }
        DshDetailRow("状态", if (running()) "运行中" else "空闲")
        DshDetailRow("模型", modelLabel())
        vif({ agentPreset().isNotEmpty() }) {
            DshDetailRow("Agent Preset", agentPreset())
        }
        DshDetailRow("队列", "${queueCount()} 条")
        DshDetailRow("后台任务", "${jobCount()} 个")
        vif({ cwd().isNotEmpty() }) {
            DshDetailRow("目录", cwd())
        }
        View { attr { flex(1f) } }
        Text {
            attr {
                text("选择消息")
                height(40f)
                textAlignCenter()
                fontSize(13f)
                color(tokens.primary)
                backgroundColor(tokens.surface)
                borderRadius(8f)
            }
            event { click { onSelectMessages() } }
        }
        Text {
            attr {
                text("导出为 HTML")
                height(40f)
                marginTop(10f)
                textAlignCenter()
                fontSize(13f)
                color(tokens.primary)
                backgroundColor(tokens.surface)
                borderRadius(8f)
            }
            event { click { onExportSession() } }
        }
        Text {
            attr {
                text("重命名会话")
                height(40f)
                textAlignCenter()
                fontSize(13f)
                color(tokens.primary)
                backgroundColor(tokens.surfaceVariant)
                borderRadius(8f)
            }
            event { click { onRename() } }
        }
        vif({ !archived() }) {
            Text {
                attr {
                    text("归档会话")
                    height(40f)
                    marginTop(10f)
                    textAlignCenter()
                    fontSize(13f)
                    color(tokens.error.foreground)
                    backgroundColor(tokens.surfaceVariant)
                    borderRadius(8f)
                }
                event { click { onArchive() } }
            }
        }
        vif({ archived() && canUnarchive() }) {
            Text {
                attr {
                    text("恢复到主列表")
                    height(40f)
                    marginTop(10f)
                    textAlignCenter()
                    fontSize(13f)
                    color(tokens.primary)
                    backgroundColor(tokens.surfaceVariant)
                    borderRadius(8f)
                }
                event { click { onRestore() } }
            }
        }
        vif({ canDelete() }) {
            Text {
                attr {
                    text(if (running()) "删除不可用（运行中）" else "永久删除")
                    height(40f)
                    marginTop(10f)
                    textAlignCenter()
                    fontSize(13f)
                    color(if (running()) tokens.disabled.foreground else tokens.error.foreground)
                    backgroundColor(tokens.surfaceVariant)
                    borderRadius(8f)
                }
                event { click { if (!running()) onDelete() } }
            }
        }
    }
}

internal fun ViewContainer<*, *>.DshDetailRow(
    label: String,
    value: String,
) {
    View {
        attr {
            minHeight(44f)
            marginTop(10f)
            flexDirectionColumn()
            justifyContentCenter()
        }
        Text {
            attr {
                text(label)
                fontSize(11f)
                color(tokens.captionText)
            }
        }
        Text {
            attr {
                text(value)
                marginTop(2f)
                fontSize(13f)
                color(tokens.primaryText)
                lines(2)
            }
        }
    }
}

internal fun ViewContainer<*, *>.DshSessionManageModal(
    title: () -> String,
    archived: () -> Boolean,
    remote: () -> Boolean,
    canUnarchive: () -> Boolean,
    canDelete: () -> Boolean,
    running: () -> Boolean,
    onRename: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onSelectMessages: () -> Unit = {},
    onExportSession: () -> Unit = {},
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
        event { click { onClose() } }
        View {
            attr {
                width(220f)
                paddingTop(6f)
                paddingBottom(6f)
                borderRadius(14f)
                backgroundColor(tokens.surfaceElevated)
            }
            event { click { } }
            vif({ !remote() }) {
                Text {
                    attr {
                        text("需连接电脑上的 DSH 才能重命名、归档或删除会话。手机不会另存一套会话。")
                        margin(16f)
                        fontSize(13f)
                        lineHeight(20f)
                        color(tokens.secondaryText)
                    }
                }
            }
            vif({ remote() }) {
                DshSessionMenuRow("选择消息", tokens.primaryText, onSelectMessages)
                DshSessionMenuRow("导出为 HTML", tokens.primaryText, onExportSession)
                DshSessionMenuRow("重命名", tokens.primaryText, onRename)
                vif({ !archived() }) {
                    DshSessionMenuRow("归档", tokens.primaryText, onArchive)
                }
                vif({ archived() && canUnarchive() }) {
                    DshSessionMenuRow("恢复", tokens.primaryText, onRestore)
                }
                vif({ !canUnarchive() || !canDelete() }) {
                    Text {
                        attr {
                            text("恢复或永久删除需要电脑上已更新的 dsh-scan-remote 插件。")
                            marginLeft(18f)
                            marginRight(18f)
                            marginTop(8f)
                            marginBottom(8f)
                            fontSize(12f)
                            lineHeight(18f)
                            color(tokens.tertiaryText)
                        }
                    }
                }
                vif({ canDelete() }) {
                    View {
                        attr {
                            height(1f)
                            marginTop(4f)
                            marginBottom(4f)
                            backgroundColor(tokens.divider)
                        }
                    }
                    DshSessionMenuRow(
                        title = if (running()) "删除（运行中不可用）" else "删除",
                        color = if (running()) tokens.disabled.foreground else tokens.error.foreground,
                        onClick = { if (!running()) onDelete() },
                    )
                }
            }
            Text {
                attr {
                    text("取消")
                    height(40f)
                    textAlignCenter()
                    fontSize(14f)
                    color(tokens.secondaryText)
                }
                event { click { onClose() } }
            }
        }
    }
}

internal fun ViewContainer<*, *>.DshSessionMenuRow(
    title: String,
    color: Color,
    onClick: () -> Unit,
) {
    View {
        attr {
            height(44f)
            paddingLeft(18f)
            paddingRight(18f)
            justifyContentCenter()
        }
        Text {
            attr {
                text(title)
                fontSize(16f)
                color(color)
            }
        }
        event { click { onClick() } }
    }
}

internal fun ViewContainer<*, *>.DshSessionRenameModal(
    draft: () -> String,
    busy: () -> Boolean,
    error: () -> String,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
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
                width(pagerData.pageViewWidth - 40f)
                maxWidth(420f)
                padding(20f)
                borderRadius(16f)
                backgroundColor(tokens.surface)
            }
            Text {
                attr {
                    text("重命名会话")
                    fontSize(18f)
                    fontWeightBold()
                    color(tokens.primaryText)
                }
            }
            Input {
                attr {
                    height(40f)
                    marginTop(14f)
                    fontSize(14f)
                    color(tokens.primaryText)
                    placeholder("输入会话名称")
                    placeholderColor(tokens.tertiaryText)
                    text(draft())
                    returnKeyTypeDone()
                }
                event {
                    textDidChange { onDraftChange(it.text) }
                    inputReturn { if (!busy()) onSave() }
                }
            }
            vif({ error().isNotEmpty() }) {
                Text {
                    attr {
                        text(error())
                        marginTop(8f)
                        fontSize(12f)
                        lineHeight(18f)
                        color(tokens.error.foreground)
                    }
                }
            }
            View {
                attr {
                    height(40f)
                    marginTop(18f)
                    flexDirectionRow()
                    justifyContentFlexEnd()
                }
                Text {
                    attr {
                        text("取消")
                        width(78f)
                        height(38f)
                        textAlignCenter()
                        fontSize(14f)
                        color(tokens.secondaryText)
                    }
                    event { click { if (!busy()) onClose() } }
                }
                Text {
                    attr {
                        text(if (busy()) "保存中..." else "保存")
                        width(88f)
                        height(38f)
                        marginLeft(8f)
                        textAlignCenter()
                        fontSize(14f)
                        color(if (busy()) tokens.primaryDisabled else tokens.primary)
                    }
                    event { click { if (!busy()) onSave() } }
                }
            }
        }
    }
}

internal fun ViewContainer<*, *>.DshSessionArchiveModal(
    title: () -> String,
    busy: () -> Boolean,
    error: () -> String,
    onConfirm: () -> Unit,
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
                width(pagerData.pageViewWidth - 40f)
                maxWidth(420f)
                padding(20f)
                borderRadius(16f)
                backgroundColor(tokens.surface)
            }
            Text {
                attr {
                    text("归档“${title()}”？")
                    fontSize(18f)
                    fontWeightBold()
                    color(tokens.primaryText)
                    lines(2)
                }
            }
            Text {
                attr {
                    text("归档只会把此会话从主列表隐藏，不是永久删除。日志和工作区记账仍会保留，可在“已归档会话”中查看完整历史。")
                    marginTop(10f)
                    fontSize(13f)
                    lineHeight(20f)
                    color(tokens.secondaryText)
                }
            }
            vif({ error().isNotEmpty() }) {
                Text {
                    attr {
                        text(error())
                        marginTop(8f)
                        fontSize(12f)
                        lineHeight(18f)
                        color(tokens.error.foreground)
                    }
                }
            }
            View {
                attr {
                    height(40f)
                    marginTop(18f)
                    flexDirectionRow()
                    justifyContentFlexEnd()
                }
                Text {
                    attr {
                        text("取消")
                        width(78f)
                        height(38f)
                        textAlignCenter()
                        fontSize(14f)
                        color(tokens.secondaryText)
                    }
                    event { click { if (!busy()) onClose() } }
                }
                Text {
                    attr {
                        text(if (busy()) "归档中..." else "确认归档")
                        width(104f)
                        height(38f)
                        marginLeft(8f)
                        textAlignCenter()
                        fontSize(14f)
                        color(if (busy()) tokens.disabled.foreground else tokens.error.foreground)
                    }
                    event { click { if (!busy()) onConfirm() } }
                }
            }
        }
    }
}

internal fun ViewContainer<*, *>.DshSessionDeleteModal(
    title: () -> String,
    busy: () -> Boolean,
    error: () -> String,
    onConfirm: () -> Unit,
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
                width(pagerData.pageViewWidth - 40f)
                maxWidth(420f)
                padding(20f)
                borderRadius(16f)
                backgroundColor(tokens.surface)
            }
            Text {
                attr {
                    text("永久删除“${title()}”？")
                    fontSize(18f)
                    fontWeightBold()
                    color(tokens.primaryText)
                    lines(2)
                }
            }
            Text {
                attr {
                    text("这和归档不同：删除会从电脑上的 DSH 清掉会话日志，无法恢复。归档只是从主列表隐藏。")
                    marginTop(10f)
                    fontSize(13f)
                    lineHeight(20f)
                    color(tokens.secondaryText)
                }
            }
            vif({ error().isNotEmpty() }) {
                Text {
                    attr {
                        text(error())
                        marginTop(8f)
                        fontSize(12f)
                        lineHeight(18f)
                        color(tokens.error.foreground)
                    }
                }
            }
            View {
                attr {
                    height(40f)
                    marginTop(18f)
                    flexDirectionRow()
                    justifyContentFlexEnd()
                }
                Text {
                    attr {
                        text("取消")
                        width(78f)
                        height(38f)
                        textAlignCenter()
                        fontSize(14f)
                        color(tokens.secondaryText)
                    }
                    event { click { if (!busy()) onClose() } }
                }
                Text {
                    attr {
                        text(if (busy()) "删除中..." else "永久删除")
                        width(104f)
                        height(38f)
                        marginLeft(8f)
                        textAlignCenter()
                        fontSize(14f)
                        color(if (busy()) tokens.disabled.foreground else tokens.error.foreground)
                    }
                    event { click { if (!busy()) onConfirm() } }
                }
            }
        }
    }
}

internal fun ViewContainer<*, *>.DshSessionUndoBar(
    message: () -> String,
    onUndo: () -> Unit,
) {
    View {
        attr {
            height(44f)
            marginLeft(16f)
            marginRight(16f)
            marginBottom(12f)
            paddingLeft(14f)
            paddingRight(10f)
            flexDirectionRow()
            alignItemsCenter()
            borderRadius(12f)
            backgroundColor(tokens.surfaceElevated)
        }
        Text {
            attr {
                text(message())
                flex(1f)
                fontSize(13f)
                color(tokens.primaryText)
                lines(1)
            }
        }
        Text {
            attr {
                text("撤销")
                width(52f)
                height(32f)
                textAlignCenter()
                fontSize(14f)
                fontWeightMedium()
                color(tokens.primary)
            }
            event { click { onUndo() } }
        }
    }
}

internal fun ViewContainer<*, *>.DshArchivedSessionsModal(
    sessions: () -> ObservableList<DshSession>,
    activeId: () -> String,
    onSelect: (String) -> Unit,
    onManage: (String) -> Unit,
    onClose: () -> Unit,
) {
    Modal(inWindow = true) {
        attr {
            absolutePositionAllZero()
            allCenter()
            paddingLeft(16f)
            paddingRight(16f)
            backgroundColor(tokens.scrim)
        }
        View {
            attr {
                width(pagerData.pageViewWidth - 32f)
                maxWidth(520f)
                height((pagerData.pageViewHeight - 80f).coerceAtMost(620f))
                padding(18f)
                borderRadius(16f)
                backgroundColor(tokens.background)
            }
            View {
                attr {
                    height(44f)
                    flexDirectionRow()
                    alignItemsCenter()
                }
                Text {
                    attr {
                        text("已归档会话")
                        flex(1f)
                        fontSize(18f)
                        fontWeightBold()
                        color(tokens.primaryText)
                    }
                }
                Text {
                    attr {
                        text("关闭")
                        width(52f)
                        height(36f)
                        textAlignCenter()
                        fontSize(13f)
                        color(tokens.primary)
                    }
                    event { click { onClose() } }
                }
            }
            Text {
                attr {
                    text("这些会话仅从主列表隐藏，历史记录仍完整保留。")
                    marginBottom(12f)
                    fontSize(12f)
                    color(tokens.secondaryText)
                }
            }
            vif({ sessions().isEmpty() }) {
                Text {
                    attr {
                        text("暂无已归档会话")
                        marginTop(24f)
                        textAlignCenter()
                        fontSize(14f)
                        color(tokens.tertiaryText)
                    }
                }
            }
            vif({ sessions().isNotEmpty() }) {
                List {
                    attr { flex(1f) }
                    vforLazy({ sessions() }) { session, _, _ ->
                        View {
                            attr { height(52f) }
                            DshSessionDrawerRow(
                                title = session.title,
                                subtitle = session.cwd.ifEmpty { "Host" },
                                active = activeId() == session.id,
                                running = session.running,
                                onSelect = { onSelect(session.id) },
                                onLongPress = { onManage(session.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun ViewContainer<*, *>.DshWorkspaceBrowserModal(
    path: () -> String,
    home: () -> String,
    entries: () -> ObservableList<DshDirectoryEntry>,
    busy: () -> Boolean,
    error: () -> String,
    newName: () -> String,
    onDirectorySelect: (String) -> Unit,
    onNewNameChange: (String) -> Unit,
    onCreateDirectory: () -> Unit,
    onAdopt: () -> Unit,
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
                width(pagerData.pageViewWidth - 40f)
                maxWidth(560f)
                maxHeight(pagerData.pageViewHeight - 80f)
                flexDirectionColumn()
                padding(18f)
                borderRadius(16f)
                backgroundColor(tokens.surface)
            }
            View {
                attr { height(36f); flexDirectionRow(); alignItemsCenter() }
                Text {
                    attr {
                        text(if (path().isEmpty()) home() else path())
                        flex(1f)
                        lines(1)
                        fontSize(17f)
                        fontWeightBold()
                        color(tokens.primaryText)
                    }
                }
                View { attr { size(32f, 32f); allCenter() }; Image { attr { src(ImageUri.commonAssets("x.svg")); size(20f, 20f); tintColor(tokens.icon) } }; DshHitButton { onClose() } }
            }
            Scroller {
                attr {
                    flex(1f)
                    marginTop(12f)
                    borderRadius(8f)
                    backgroundColor(tokens.surfaceVariant)
                }
                vfor({ entries() }) { entry ->
                    View {
                        attr {
                            height(42f)
                            flexDirectionRow()
                            alignItemsCenter()
                            paddingLeft(10f)
                            paddingRight(10f)
                        }
                        Text {
                            attr {
                                text(entry.name)
                                flex(1f)
                                lines(1)
                                fontSize(14f)
                                color(tokens.primaryText)
                            }
                        }
                        event { click { if (!busy()) onDirectorySelect(entry.path) } }
                    }
                }
            }
            vif({ error().isNotEmpty() }) {
                Text { attr { text(error()); marginTop(8f); fontSize(12f); color(tokens.error.foreground) } }
            }
            Input {
                attr {
                    height(38f)
                    marginTop(10f)
                    fontSize(14f)
                    placeholder("新目录名称")
                    placeholderColor(tokens.tertiaryText)
                }
                event { textDidChange { onNewNameChange(it.text) } }
            }
            View {
                attr { height(42f); marginTop(12f); flexDirectionRow(); justifyContentFlexEnd() }
                Text {
                    attr {
                        text(if (busy()) "处理中..." else "新建目录")
                        width(88f)
                        height(38f)
                        textAlignCenter()
                        fontSize(13f)
                        color(tokens.secondaryText)
                    }
                    event { click { if (!busy()) onCreateDirectory() } }
                }
                Text {
                    attr {
                        text(if (busy()) "处理中..." else "使用此目录")
                        width(112f)
                        height(38f)
                        marginLeft(8f)
                        textAlignCenter()
                        fontSize(13f)
                        color(tokens.primary)
                    }
                    event { click { if (!busy()) onAdopt() } }
                }
            }
        }
    }
}
