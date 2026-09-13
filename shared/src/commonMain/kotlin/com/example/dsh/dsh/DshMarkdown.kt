package com.example.dsh.dsh

import com.example.dsh.theme.theme
import com.example.dsh.theme.tokens
import com.tencent.kuikly.core.base.ComposeAttr
import com.tencent.kuikly.core.base.ComposeEvent
import com.tencent.kuikly.core.base.ComposeView
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.reactive.ReactiveObserver
import com.tencent.kuikly.core.reactive.handler.*
import com.tencent.kuikly.core.timer.setTimeout
import com.tencent.kuikly.core.views.SelectableOption
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.tencent.kuiklybase.KuiklyStreamingMarkdown
import com.tencent.kuiklybase.config.FontWeight
import com.tencent.kuiklybase.config.MarkdownColors
import com.tencent.kuiklybase.config.MarkdownConfig
import com.tencent.kuiklybase.config.MarkdownDimens
import com.tencent.kuiklybase.config.MarkdownTypography
import com.tencent.kuiklybase.config.TextStyleConfig
import com.tencent.kuiklybase.streaming.MarkdownBlock
import com.tencent.kuiklybase.streaming.MarkdownStreamingState

/** DSH theme wrapper around KuiklyMarkdown's streaming renderer. */
internal class DshMarkdownView : ComposeView<DshMarkdownAttr, ComposeEvent>() {
    private val streamingState = MarkdownStreamingState()
    private var blockList by observableList<MarkdownBlock>()
    private var blockCount by observable(0)
    private var treeEpoch by observable(0)
    private var liveKey by observable("")
    private var lastContent = ""
    private var lastStreaming = false
    private var pendingContent = ""
    private var pendingStreaming = false
    private var flushScheduled = false
    private var renderedThemeRevision = 0
    private var cachedConfig: MarkdownConfig? = null
    private var cachedConfigRevision = -1
    private var cachedConfigStreaming: Boolean? = null

    override fun createAttr(): DshMarkdownAttr = DshMarkdownAttr()

    override fun createEvent(): ComposeEvent = ComposeEvent()

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            View {
                attr {
                    autoDarkEnable(false)
                    if (ctx.attr.contentWidth > 0f) {
                        width(ctx.attr.contentWidth)
                    }
                }
                // vforLazy will not create later siblings of a nested vfor
                // after the row is already built (paragraph stays, code fence
                // never mounts). Remount when the block count changes so
                // KuiklyStreamingMarkdown can paint every block, including
                // the component's own codeFence.
                vbind({ ctx.treeEpoch }) {
                    vfor({ ctx.blockList }) { block ->
                        View {
                            attr {
                                autoDarkEnable(false)
                                if (ctx.attr.contentWidth > 0f) {
                                    width(ctx.attr.contentWidth)
                                }
                            }
                            vbind({
                                val live = block.blockIndex == ctx.blockCount - 1
                                if (live) ctx.liveKey else block.id
                            }) {
                                View {
                                    attr {
                                        autoDarkEnable(false)
                                        if (ctx.attr.contentWidth > 0f) {
                                            width(ctx.attr.contentWidth)
                                        }
                                    }
                                    KuiklyStreamingMarkdown(
                                        state = ctx.streamingState,
                                        block = block,
                                        config = ctx.markdownConfig(),
                                    )
                                    vif({
                                        val streamingNow = ctx.attr.streamingProvider?.invoke() ?: ctx.attr.streaming
                                        val live = streamingNow && block.blockIndex == ctx.blockCount - 1
                                        val fence = dshParseCodeFence(block.blockContent)
                                        fence != null && fence.body.isNotEmpty() && !live
                                    }) {
                                        View {
                                            attr {
                                                absolutePosition(top = 8f, right = 8f)
                                                height(22f)
                                                paddingLeft(8f)
                                                paddingRight(8f)
                                                borderRadius(11f)
                                                allCenter()
                                                zIndex(4)
                                                selectable(SelectableOption.DISABLE)
                                                backgroundColor(tokens.surfaceElevated)
                                            }
                                            Text {
                                                attr {
                                                    text("复制")
                                                    fontSize(11f)
                                                    fontWeightMedium()
                                                    color(tokens.primary)
                                                }
                                            }
                                            DshHitButton {
                                                val body = dshParseCodeFence(block.blockContent)?.body.orEmpty()
                                                if (body.isNotEmpty()) ctx.attr.onCopyCode(body)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun viewDidLoad() {
        super.viewDidLoad()
        renderedThemeRevision = theme.revision
        ReactiveObserver.bindValueChange(this) {
            val live = attr.liveContent?.invoke().orEmpty()
            val content = live.ifEmpty { attr.content }
            val streaming = attr.streamingProvider?.invoke() ?: attr.streaming
            ReactiveObserver.addLazyTaskUtilEndCollectDependency {
                scheduleBlocksUpdate(content, streaming)
            }
        }
        // markdownConfig() is captured when a block mounts. Remount on theme
        // change so the new palette applies, but keep the parsed tree so a
        // live reply is not wiped to an empty native markdown view.
        ReactiveObserver.bindValueChange(this) {
            val revision = theme.revision
            ReactiveObserver.addLazyTaskUtilEndCollectDependency {
                if (revision != renderedThemeRevision) {
                    renderedThemeRevision = revision
                    cachedConfig = null
                    if (blockList.isEmpty() && lastContent.isEmpty()) return@addLazyTaskUtilEndCollectDependency
                    treeEpoch += 1
                    flexNode.markDirty()
                }
            }
        }
    }

    override fun viewWillUnload() {
        ReactiveObserver.unbindValueChange(this)
        super.viewWillUnload()
    }

    private fun scheduleBlocksUpdate(content: String, streaming: Boolean) {
        pendingContent = content
        pendingStreaming = streaming
        if (!streaming || lastContent.isEmpty() || lastContent == DshStreamingMarkdown.PLACEHOLDER) {
            flushBlocksUpdate()
            return
        }
        if (flushScheduled) return
        flushScheduled = true
        setTimeout(pagerId, DshStreamingMarkdown.FRAME_MS) {
            flushScheduled = false
            flushBlocksUpdate()
        }
    }

    private fun flushBlocksUpdate() {
        val content = pendingContent
        val streaming = pendingStreaming
        if (content == lastContent && streaming == lastStreaming) return
        val endingStream = lastStreaming && !streaming
        if (content.isEmpty() && lastContent.isNotEmpty()) {
            DshStreamLog.i(
                "render.skip empty-wipe streaming=$streaming prevChars=${lastContent.length} prevBlocks=${blockList.size}",
            )
            lastStreaming = streaming
            return
        }
        if (
            lastContent.isNotEmpty() &&
            content.length < lastContent.length &&
            lastContent.startsWith(content)
        ) {
            DshStreamLog.i(
                "render.skip shrink streaming=$streaming prevChars=${lastContent.length} nextChars=${content.length}",
            )
            lastStreaming = streaming
            return
        }
        if (streaming && !lastStreaming) {
            DshStreamLog.i("render.stream-start chars=${content.length}")
            streamingState.reset()
            blockList.clear()
            blockCount = 0
            liveKey = ""
            treeEpoch += 1
        }
        lastContent = content
        lastStreaming = streaming
        val input = if (content.isEmpty() && streaming) DshStreamingMarkdown.PLACEHOLDER else content
        val toParse = if (streaming) DshStreamingMarkdown.closeOpenFence(input) else input
        val next = streamingState.update(toParse, force = !streaming)
        if (next == null) {
            DshStreamLog.i("render.skip parser-null streaming=$streaming chars=${content.length}")
            return
        }
        val previousCount = blockList.size
        blockList.diffUpdate(next) { old, new -> old.id == new.id }
        val countChanged = blockCount != blockList.size
        if (countChanged) {
            blockCount = blockList.size
        }
        val newLiveKey = next.lastOrNull()?.id.orEmpty()
        if (liveKey != newLiveKey) {
            liveKey = newLiveKey
        }
        if (countChanged || endingStream) {
            treeEpoch += 1
        }
        flexNode.markDirty()
        DshStreamLog.i(
            "render.apply streaming=$streaming uiBlocks=$previousCount→${blockList.size} ${DshStreamLog.blocks(next)}",
        )
    }

    private fun markdownConfig(): MarkdownConfig {
        val snapshot = theme
        val streaming = attr.streaming
        val cached = cachedConfig
        if (cached != null && cachedConfigRevision == snapshot.revision && cachedConfigStreaming == streaming) {
            return cached
        }
        val dark = snapshot.isDark
        val c = snapshot.codeColors
        val text = c.text
        val config = MarkdownConfig(
            colors = MarkdownColors(
                text = text,
                codeBackground = c.codeBlockBackground,
                inlineCodeBackground = c.inlineCodeBackground,
                dividerColor = c.divider,
                tableBackground = c.tableBackground,
                blockQuoteBar = c.quoteBar,
                blockQuoteBackground = c.quoteBackground,
                linkColor = c.link,
                codeText = c.codeText,
            ),
            typography = MarkdownTypography(
                text = TextStyleConfig(fontSize = 15f, color = text, lineHeight = 23f),
                paragraph = TextStyleConfig(fontSize = 15f, color = text, lineHeight = 23f),
                code = TextStyleConfig(fontSize = 13f, fontFamily = "monospace", lineHeight = 19f),
                inlineCode = TextStyleConfig(fontSize = 13f, fontFamily = "monospace"),
                h1 = TextStyleConfig(fontSize = 24f, fontWeight = FontWeight.Bold, color = text, lineHeight = 30f),
                h2 = TextStyleConfig(fontSize = 20f, fontWeight = FontWeight.Bold, color = text, lineHeight = 26f),
                h3 = TextStyleConfig(fontSize = 18f, fontWeight = FontWeight.SemiBold, color = text, lineHeight = 24f),
                h4 = TextStyleConfig(fontSize = 16f, fontWeight = FontWeight.SemiBold, color = text, lineHeight = 22f),
                h5 = TextStyleConfig(fontSize = 15f, fontWeight = FontWeight.SemiBold, color = text, lineHeight = 21f),
                h6 = TextStyleConfig(fontSize = 15f, fontWeight = FontWeight.SemiBold, color = text, lineHeight = 21f),
                quote = TextStyleConfig(fontSize = 15f, color = c.quoteText, lineHeight = 22f),
                ordered = TextStyleConfig(fontSize = 15f, color = text, lineHeight = 23f),
                bullet = TextStyleConfig(fontSize = 15f, color = text, lineHeight = 23f),
                list = TextStyleConfig(fontSize = 15f, color = text, lineHeight = 23f),
                table = TextStyleConfig(fontSize = 13f, color = text, lineHeight = 19f),
                textLink = TextStyleConfig(fontSize = 15f, color = c.link, lineHeight = 23f),
            ),
            dimens = MarkdownDimens(
                dividerThickness = 1f,
                codeBackgroundCornerSize = 8f,
                blockQuoteThickness = 3f,
                blockQuoteCornerSize = 6f,
                tableCellWidth = 136f,
                tableCellPadding = 10f,
                tableCornerSize = 8f,
            ),
            codeHighlightDarkTheme = dark,
            codeHighlightEnabled = !streaming,
            padding = com.tencent.kuiklybase.config.MarkdownPadding(
                block = 6f,
                list = 6f,
                listItemTop = 2f,
                listItemBottom = 2f,
                listIndent = 18f,
                codeBlock = 12f,
                blockQuotePaddingLeft = 12f,
                blockQuoteBarPaddingLeft = 4f,
                blockQuoteTextVertical = 8f,
            ),
        )
        cachedConfig = config
        cachedConfigRevision = snapshot.revision
        cachedConfigStreaming = streaming
        return config
    }
}

internal class DshMarkdownAttr : ComposeAttr() {
    var contentWidth: Float by observable(0f)
    var content: String by observable("")
    var streaming: Boolean by observable(false)
    var darkMode: Boolean by observable(false)
    var liveContent: (() -> String)? = null
    var streamingProvider: (() -> Boolean)? = null
    var onCopyCode: (String) -> Unit by observable({})
}

internal fun ViewContainer<*, *>.DshMarkdown(init: DshMarkdownView.() -> Unit) {
    addChild(DshMarkdownView(), init)
}
