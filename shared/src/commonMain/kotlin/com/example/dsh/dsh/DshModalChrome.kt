package com.example.dsh.dsh

import com.example.dsh.theme.DshMotion
import com.example.dsh.theme.tokens
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ComposeAttr
import com.tencent.kuikly.core.base.ComposeEvent
import com.tencent.kuikly.core.base.ComposeView
import com.tencent.kuikly.core.base.PagerScope
import com.tencent.kuikly.core.base.Scale
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.timer.setTimeout
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Modal
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.tencent.kuikly.core.views.compose.Button

internal enum class DshModalScrimTone {
    Dialog,
    Danger,
}

/** 弹窗的展示进度。`presented` 由 false→true 触发进场，true→false 触发退场。 */
internal class DshModalMotion {
    var presented by observable(false)
}

/**
 * 弹窗显隐宿主，替代 `vif`：条件变 false 后节点保留到退场动画结束才卸载，
 * 内部的 [DshDialogModal] / [DshBottomSheetModal] / [DshFullScreenModal] 会自动找到它。
 */
internal fun ViewContainer<*, *>.DshModalHost(
    visible: () -> Boolean,
    content: ViewContainer<*, *>.() -> Unit,
) {
    addChild(DshModalHostView()) {
        this.content = content
        attr {
            absolutePosition(top = 0f, left = 0f)
            this.visible = visible()
        }
    }
}

internal class DshModalHostView : ComposeView<DshModalHostAttr, ComposeEvent>() {
    internal val motion = DshModalMotion()
    internal var content: ViewContainer<*, *>.() -> Unit = {}
    private var mounted by observable(false)
    private var generation = 0

    override fun createAttr(): DshModalHostAttr = DshModalHostAttr { onVisibleChange(it) }
    override fun createEvent(): ComposeEvent = ComposeEvent()

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            vif({ ctx.mounted }) { ctx.content(this) }
        }
    }

    private fun onVisibleChange(visible: Boolean) {
        val token = ++generation
        if (visible) {
            if (mounted) {
                motion.presented = true
                return
            }
            motion.presented = false
            mounted = true
            dshPresentAfterLayout(motion) { token == generation }
        } else if (mounted) {
            setTimeout(pagerId, 0) {
                if (token != generation) return@setTimeout
                motion.presented = false
                setTimeout(pagerId, DshMotion.exitUnmountMs) {
                    if (token == generation) mounted = false
                }
            }
        }
    }
}

internal class DshModalHostAttr(private val onChange: (Boolean) -> Unit) : ComposeAttr() {
    var visible: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            onChange(value)
        }
}

/** 等节点首次排版落到渲染层后再切到展示态，否则渲染层只会看到终态、没有过渡。 */
private fun PagerScope.dshPresentAfterLayout(motion: DshModalMotion, stillValid: () -> Boolean) {
    getPager().addTaskWhenPagerUpdateLayoutFinish {
        setTimeout(pagerId, 48) { if (stillValid()) motion.presented = true }
    }
}

/** 找到最近的 [DshModalHostView]；不在宿主里时只做进场动画。 */
internal fun ViewContainer<*, *>.dshModalMotion(): DshModalMotion {
    var node: ViewContainer<*, *>? = this
    while (node != null && !node.isPager()) {
        if (node is DshModalHostView) return node.motion
        node = node.parent
    }
    return DshModalMotion().also { motion -> dshPresentAfterLayout(motion) { true } }
}

private fun ViewContainer<*, *>.DshModalScrim(
    motion: DshModalMotion,
    color: () -> Color,
    onTap: () -> Unit,
) {
    View {
        attr {
            absolutePositionAllZero()
            backgroundColor(color())
            opacity(if (motion.presented) 1f else 0f)
            animation(DshMotion.scrimPresence(motion.presented), motion.presented)
        }
        event { click { if (motion.presented) onTap() } }
    }
}

/** Shared modal chrome. Product flows should use one of these three levels. */
internal fun ViewContainer<*, *>.DshDialogModal(
    busy: () -> Boolean = { false },
    scrimTone: DshModalScrimTone = DshModalScrimTone.Dialog,
    onClose: () -> Unit,
    content: ViewContainer<*, *>.() -> Unit,
) {
    val motion = dshModalMotion()
    Modal(inWindow = true) {
        attr {
            absolutePositionAllZero()
            allCenter()
            paddingLeft(20f)
            paddingRight(20f)
        }
        DshModalScrim(
            motion = motion,
            color = {
                when (scrimTone) {
                    DshModalScrimTone.Dialog -> tokens.scrimDialog
                    DshModalScrimTone.Danger -> tokens.scrimDanger
                }
            },
            onTap = { if (!busy()) onClose() },
        )
        View {
            attr {
                width(pagerData.pageViewWidth - 40f)
                maxWidth(400f)
                padding(20f)
                borderRadius(18f)
                backgroundColor(tokens.surfaceElevated)
                val shown = motion.presented
                val scale = if (shown || DshMotion.reduced) 1f else DshMotion.DIALOG_ENTER_SCALE
                opacity(if (shown) 1f else 0f)
                transform(Scale(scale, scale))
                animation(DshMotion.presence(shown), motion.presented)
            }
            event { click { } }
            content()
        }
    }
}

internal fun ViewContainer<*, *>.DshBottomSheetModal(
    busy: () -> Boolean = { false },
    heightRatio: Float = 0.72f,
    minHeight: Float = 280f,
    maxHeight: Float = 720f,
    onClose: () -> Unit,
    content: ViewContainer<*, *>.() -> Unit,
) {
    val motion = dshModalMotion()
    Modal(inWindow = true) {
        attr {
            absolutePositionAllZero()
            flexDirectionColumn()
            justifyContentFlexEnd()
        }
        DshModalScrim(motion = motion, color = { tokens.scrimSheet }, onTap = { if (!busy()) onClose() })
        View {
            attr {
                val shown = motion.presented
                if (DshMotion.reduced) {
                    opacity(if (shown) 1f else 0f)
                } else {
                    transform(Translate(0f, if (shown) 0f else 1f))
                }
                animation(DshMotion.presence(shown), motion.presented)
                val safeBottom = pagerData.safeAreaInsets.bottom
                val availableHeight = (
                    pagerData.pageViewHeight - pagerData.statusBarHeight - safeBottom
                    ).coerceAtLeast(1f)
                val heightCap = availableHeight * 0.88f
                val resolvedHeight = minOf(
                    heightCap,
                    maxHeight,
                    maxOf(minOf(minHeight, heightCap), availableHeight * heightRatio),
                )
                width(pagerData.pageViewWidth)
                height(resolvedHeight)
                flexDirectionColumn()
                paddingLeft(16f)
                paddingRight(16f)
                paddingBottom(pagerData.safeAreaInsets.bottom + 12f)
                borderRadius(20f, 20f, 0f, 0f)
                backgroundColor(tokens.surfaceElevated)
            }
            event { click { } }
            DshSheetHandle()
            content()
        }
    }
}

internal fun ViewContainer<*, *>.DshFullScreenModal(
    content: ViewContainer<*, *>.() -> Unit,
) {
    val motion = dshModalMotion()
    Modal(inWindow = true) {
        attr { absolutePositionAllZero() }
        DshModalScrim(motion = motion, color = { tokens.scrimSheet }, onTap = { })
        View {
            attr {
                absolutePositionAllZero()
                flexDirectionColumn()
                paddingTop(pagerData.statusBarHeight + 8f)
                paddingLeft(16f)
                paddingRight(16f)
                paddingBottom(pagerData.safeAreaInsets.bottom + 12f)
                backgroundColor(tokens.background)
                val shown = motion.presented
                if (DshMotion.reduced) {
                    opacity(if (shown) 1f else 0f)
                } else {
                    transform(Translate(if (shown) 0f else 1f, 0f))
                }
                animation(DshMotion.pagePresence(shown), motion.presented)
            }
            event { click { } }
            content()
        }
    }
}

internal fun ViewContainer<*, *>.DshSheetHandle() {
    View {
        attr {
            width(36f)
            height(4f)
            marginTop(8f)
            marginBottom(8f)
            alignSelfCenter()
            borderRadius(2f)
            backgroundColor(tokens.dividerStrong)
        }
    }
}

internal fun ViewContainer<*, *>.DshModalHeader(
    title: () -> String,
    subtitle: (() -> String)? = null,
    backTitle: String = "",
    onBack: (() -> Unit)? = null,
    showClose: Boolean = true,
    onClose: () -> Unit,
) {
    View {
        attr {
            minHeight(52f)
            flexDirectionRow()
            alignItemsCenter()
        }
        if (onBack != null) {
            View {
                attr { width(44f); height(44f); allCenter() }
                Text { attr { text("‹"); fontSize(28f); color(tokens.primaryText) } }
                DshHitButton { onBack() }
            }
        } else if (!showClose) {
            View { attr { width(44f); height(44f) } }
        }
        View {
            attr { flex(1f); flexDirectionColumn(); justifyContentCenter() }
            Text {
                attr {
                    text(title())
                    fontSize(if (onBack != null && subtitle == null) 17f else if (onBack == null) 20f else 17f)
                    fontWeightBold()
                    color(tokens.primaryText)
                    lines(1)
                    if (onBack != null) textAlignCenter()
                }
            }
            if (subtitle != null) {
                Text {
                    attr {
                        text(subtitle())
                        marginTop(2f)
                        fontSize(11f)
                        color(tokens.secondaryText)
                        lines(1)
                        if (onBack != null) textAlignCenter()
                    }
                }
            }
        }
        if (showClose) {
            View {
                attr { size(44f, 44f); allCenter() }
                Image {
                    attr {
                        src(ImageUri.commonAssets("x.svg"))
                        size(20f, 20f)
                        tintColor(tokens.icon)
                    }
                }
                DshHitButton { onClose() }
            }
        } else if (onBack != null) {
            View { attr { width(44f); height(44f) } }
        }
    }
}

internal fun ViewContainer<*, *>.DshDialogActions(
    confirmTitle: () -> String,
    busy: () -> Boolean,
    destructive: Boolean = false,
    confirmEnabled: () -> Boolean = { true },
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    View {
        attr {
            height(44f)
            marginTop(20f)
            flexDirectionRow()
            justifyContentFlexEnd()
        }
        View {
            attr { width(76f); height(44f); allCenter() }
            Text { attr { text("取消"); fontSize(14f); color(tokens.secondaryText) } }
            DshHitButton { if (!busy()) onCancel() }
        }
        Button {
            attr {
                minWidth(104f)
                height(44f)
                marginLeft(8f)
                paddingLeft(16f)
                paddingRight(16f)
                borderRadius(10f)
                backgroundColor(
                    when {
                        busy() || !confirmEnabled() -> tokens.primaryDisabled
                        destructive -> tokens.error.foreground
                        else -> tokens.primary
                    },
                )
                titleAttr {
                    text(confirmTitle())
                    fontSize(14f)
                    fontWeightMedium()
                    color(tokens.onPrimary)
                }
            }
            event { click { if (!busy() && confirmEnabled()) onConfirm() } }
        }
    }
}

internal fun ViewContainer<*, *>.DshDialogSubject(value: () -> String) {
    View {
        attr {
            marginTop(12f)
            padding(12f)
            borderRadius(10f)
            backgroundColor(tokens.surfaceVariant)
        }
        Text {
            attr {
                text(value())
                fontSize(14f)
                fontWeightMedium()
                color(tokens.primaryText)
                lines(2)
            }
        }
    }
}
