package com.example.dsh.dsh

/**
 * Readable copy / HTML export for DSH messages.
 *
 * Whole-message copy is flattened plain text: no markdown chrome, no UI glyphs,
 * code fences emit only the code, cards become "name · status · path" lines.
 * Batch / HTML export keeps timeline order and adds 你 / 助手 role labels.
 */
internal enum class DshExportRole {
    USER,
    ASSISTANT,
    TOOL,
    ATTACHMENT,
}

internal data class DshExportBlock(
    val role: DshExportRole,
    val body: String,
    val htmlBody: String = dshEscapeHtml(body).replace("\n", "<br>"),
)

internal fun DshMessage.isCopyExportable(): Boolean {
    if (hidden) return false
    if (isReasoning || isContextInjection) return false
    if (role == DshMessageRole.ERROR) return false
    if (attachmentId != null) return true
    return when (role) {
        DshMessageRole.USER,
        DshMessageRole.ASSISTANT,
        DshMessageRole.TOOL,
        -> true
        DshMessageRole.ERROR -> false
    }
}

internal fun DshMessage.copyEnabled(streaming: Boolean): Boolean {
    if (!isCopyExportable()) return false
    if (streaming && role == DshMessageRole.ASSISTANT && attachmentId == null) return false
    return true
}

internal fun DshMessage.toReadableCopy(displayedContent: String = content): String {
    return toExportBlock(displayedContent)?.body.orEmpty()
}

internal fun DshMessage.toExportBlock(displayedContent: String = content): DshExportBlock? {
    if (!isCopyExportable()) return null
    val attachment = attachmentId
    if (attachment != null) {
        val body = dshAttachmentReadableText(attachment, displayedContent)
        return DshExportBlock(DshExportRole.ATTACHMENT, body, dshAttachmentHtml(attachment, displayedContent))
    }
    return when (role) {
        DshMessageRole.USER -> {
            val body = displayedContent.trim()
            if (body.isEmpty()) null else DshExportBlock(DshExportRole.USER, body)
        }
        DshMessageRole.ASSISTANT -> {
            val body = dshFlattenMarkdown(displayedContent).trim()
            if (body.isEmpty()) null else DshExportBlock(
                DshExportRole.ASSISTANT,
                body,
                dshMarkdownToHtml(displayedContent),
            )
        }
        DshMessageRole.TOOL -> {
            val body = dshToolReadableText(this, displayedContent).trim()
            if (body.isEmpty()) null else DshExportBlock(
                DshExportRole.TOOL,
                body,
                dshToolHtml(this, displayedContent),
            )
        }
        DshMessageRole.ERROR -> null
    }
}

internal fun dshSelectedMessagesReadable(
    messages: List<DshMessage>,
    selectedIds: Set<String>?,
    displayedContent: (DshMessage) -> String = { it.content },
    labeled: Boolean,
): String {
    val blocks = dshSelectedExportBlocks(messages, selectedIds, displayedContent)
    if (blocks.isEmpty()) return ""
    if (!labeled && blocks.size == 1) return blocks.single().body
    return buildString {
        blocks.forEachIndexed { index, block ->
            if (index > 0) append("\n\n")
            append(block.role.label)
            append('\n')
            append(block.body)
        }
    }
}

internal fun dshSelectedExportBlocks(
    messages: List<DshMessage>,
    selectedIds: Set<String>?,
    displayedContent: (DshMessage) -> String = { it.content },
): List<DshExportBlock> {
    val wanted = if (selectedIds == null) {
        messages.filter { it.isCopyExportable() }
    } else {
        messages.filter { selectedIds.contains(it.id) && it.isCopyExportable() }
    }
    return wanted.mapNotNull { it.toExportBlock(displayedContent(it)) }
}

internal fun dshExportHtml(
    title: String,
    blocks: List<DshExportBlock>,
): String {
    val safeTitle = dshEscapeHtml(title.ifBlank { "会话导出" })
    val articles = blocks.joinToString("\n") { block ->
        """
        |<article class="msg ${block.role.css}">
        |  <div class="role">${dshEscapeHtml(block.role.label)}</div>
        |  <div class="body">${block.htmlBody}</div>
        |</article>
        """.trimMargin()
    }
    return """
        |<!DOCTYPE html>
        |<html lang="zh-CN">
        |<head>
        |<meta charset="utf-8"/>
        |<meta name="viewport" content="width=device-width, initial-scale=1"/>
        |<title>$safeTitle</title>
        |<style>
        |  :root { color-scheme: light dark; }
        |  body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; margin: 24px auto; max-width: 720px; padding: 0 16px 48px; line-height: 1.55; color: #1b1b1d; }
        |  h1 { font-size: 20px; margin: 0 0 8px; }
        |  .hint { font-size: 12px; color: #6b7178; margin-bottom: 24px; }
        |  .msg { margin: 0 0 20px; padding: 12px 14px; border-radius: 12px; background: #f4f6f8; }
        |  .msg.user { background: #edf3fe; }
        |  .msg.tool, .msg.attachment { background: #f1f3f5; border: 1px solid rgba(0,0,0,.08); }
        |  .role { font-size: 11px; font-weight: 600; letter-spacing: .04em; color: #6b7178; margin-bottom: 6px; }
        |  .body { font-size: 15px; white-space: normal; overflow-wrap: anywhere; }
        |  pre { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 13px; line-height: 1.45; background: #17181a; color: #f3f4f6; padding: 12px; border-radius: 8px; overflow-x: auto; white-space: pre-wrap; }
        |  code { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 13px; }
        |  a { color: #315fc7; }
        |  .card-meta { font-size: 13px; color: #3d444c; margin-bottom: 8px; }
        |  @media (prefers-color-scheme: dark) {
        |    body { color: #f3f4f6; background: #111113; }
        |    .msg { background: #232324; }
        |    .msg.user { background: #1c2d49; }
        |    .msg.tool, .msg.attachment { background: #2c2c2e; border-color: rgba(255,255,255,.08); }
        |    .role, .hint, .card-meta { color: #adb2b8; }
        |    a { color: #8bb4ff; }
        |  }
        |  @media print {
        |    body { max-width: none; margin: 0; color: #111; }
        |    .hint { display: none; }
        |    pre { background: #f4f4f5; color: #111; }
        |  }
        |</style>
        |</head>
        |<body>
        |<h1>$safeTitle</h1>
        |<p class="hint">可用系统分享里的「打印」导出为 PDF。</p>
        |$articles
        |</body>
        |</html>
    """.trimMargin()
}

internal fun dshExportFileName(title: String): String {
    val stem = title.trim()
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .replace("..", "-")
        .replace(Regex("""[\\/:*?"<>|\u0000-\u001f]"""), "-")
        .replace(Regex("\\s+"), "-")
        .trim('-')
        .ifBlank { "dsh-session" }
        .take(48)
    return "$stem.html"
}

internal data class DshCodeFence(
    val language: String,
    val body: String,
)

internal fun dshLooksLikeCodeFence(content: String): Boolean {
    val line = content.trimStart()
    return line.startsWith("```") || line.startsWith("~~~")
}

internal fun dshParseCodeFence(content: String): DshCodeFence? {
    val normalized = content.replace("\r\n", "\n").trim('\n')
    if (!dshLooksLikeCodeFence(normalized)) return null
    val lines = normalized.lines()
    val open = lines.first().trimStart()
    val marker = if (open.startsWith("```")) "```" else "~~~"
    val language = open.removePrefix(marker).trim()
    var closeIndex = -1
    for (index in lines.lastIndex downTo 1) {
        if (lines[index].trim().startsWith(marker)) {
            closeIndex = index
            break
        }
    }
    val inner = if (closeIndex > 0) lines.subList(1, closeIndex) else lines.drop(1)
    return DshCodeFence(language, inner.joinToString("\n").trimEnd())
}

internal fun dshFlattenMarkdown(source: String): String {
    if (source.isEmpty()) return ""
    val fences = mutableListOf<String>()
    val withSlots = CODE_FENCE_PATTERN.replace(source) { match ->
        val fence = dshParseCodeFence(match.value)
        val body = fence?.body ?: match.value
        fences += body
        "\n\n${FENCE_SLOT}${fences.lastIndex}${FENCE_SLOT}\n\n"
    }
    val flattened = withSlots
        .replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")
        .replace(Regex("^>\\s?", RegexOption.MULTILINE), "")
        .replace(Regex("^\\s*[-*+]\\s+", RegexOption.MULTILINE), "• ")
        .replace(Regex("^\\s*\\d+\\.\\s+", RegexOption.MULTILINE), "")
        .replace(Regex("!\\[([^\\]]*)]\\(([^)]+)\\)")) { match ->
            val alt = match.groupValues[1].ifBlank { "图片" }
            val url = match.groupValues[2]
            "$alt（$url）"
        }
        .replace(Regex("\\[([^\\]]+)]\\(([^)]+)\\)")) { match ->
            val text = match.groupValues[1]
            val url = match.groupValues[2]
            if (text == url) url else "$text（$url）"
        }
        .replace(Regex("(\\*\\*|__)(.+?)\\1"), "$2")
        .replace(Regex("(\\*|_)(.+?)\\1"), "$2")
        .replace(Regex("`([^`]+)`"), "$1")
        .replace(Regex("^\\s*([-*_]){3,}\\s*$", RegexOption.MULTILINE), "")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
    return FENCE_SLOT_PATTERN.replace(flattened) { match ->
        fences.getOrNull(match.groupValues[1].toInt()) ?: ""
    }.replace(Regex("\n{3,}"), "\n\n").trim()
}

internal fun dshMarkdownToHtml(source: String): String {
    if (source.isEmpty()) return ""
    val fences = mutableListOf<DshCodeFence>()
    val withSlots = CODE_FENCE_PATTERN.replace(source) { match ->
        val fence = dshParseCodeFence(match.value) ?: DshCodeFence("", match.value)
        fences += fence
        "\n\n${FENCE_SLOT}${fences.lastIndex}${FENCE_SLOT}\n\n"
    }
    val blocks = withSlots.split(Regex("\n{2,}")).map { it.trim() }.filter { it.isNotEmpty() }
    return buildString {
        blocks.forEach { block ->
            val slot = FENCE_SLOT_PATTERN.matchEntire(block)
            if (slot != null) {
                val fence = fences.getOrNull(slot.groupValues[1].toInt())
                if (fence != null) {
                    append("<pre><code>")
                    append(dshEscapeHtml(fence.body))
                    append("</code></pre>")
                }
                return@forEach
            }
            append("<p>")
            append(dshInlineMarkdownHtml(block))
            append("</p>")
        }
    }
}

internal fun dshToolReadableText(message: DshMessage, displayedContent: String): String {
    val remote = message.remoteTool
    val header = dshToolHeaderLine(message)
    val body = dshToolBody(message, displayedContent)
    return listOf(header, body).filter { it.isNotEmpty() }.joinToString("\n")
}

internal fun dshToolHeaderLine(message: DshMessage): String {
    val remote = message.remoteTool
    val name = when {
        remote != null && !remote.title.dshLooksLikeJson() -> remote.title
        !message.toolName.isNullOrEmpty() && !message.toolName.dshLooksLikeJson() -> message.toolName
        else -> remote?.toolName?.takeIf { it.isNotEmpty() } ?: "工具"
    }
    val status = when {
        message.toolError || remote?.error != null -> "失败"
        message.toolRunning || remote?.running == true -> "运行中"
        else -> "完成"
    }
    val path = remote?.filePath?.takeIf { it.isNotEmpty() }
        ?: remote?.summary?.takeUnless { it.dshLooksLikeJson() || it == status }
    return listOf(name, status, path).filter { !it.isNullOrEmpty() }.joinToString(" · ")
}

private fun dshToolBody(message: DshMessage, displayedContent: String): String {
    val remote = message.remoteTool
    if (remote?.kind == DshRemoteToolKind.ASK_QUESTION) {
        return dshAskReadableBody(remote.input, remote.output.ifEmpty { displayedContent }).ifEmpty { "已回答" }
    }
    val raw = remote?.output?.takeIf { it.isNotEmpty() }
        ?: remote?.body?.takeIf { it.isNotEmpty() }
        ?: remote?.input?.takeIf { it.isNotEmpty() }
        ?: displayedContent
    return raw.trim()
}

private fun dshToolHtml(message: DshMessage, displayedContent: String): String {
    val header = dshEscapeHtml(dshToolHeaderLine(message))
    val body = dshEscapeHtml(dshToolBody(message, displayedContent)).replace("\n", "<br>")
    return """<div class="card-meta">$header</div><div>$body</div>"""
}

internal fun dshAttachmentReadableText(attachmentId: String, nameHint: String = ""): String {
    val name = nameHint.trim().ifEmpty { "图片" }
    return "附件 · $name\n引用：$attachmentId"
}

private fun dshAttachmentHtml(attachmentId: String, nameHint: String): String {
    val name = dshEscapeHtml(nameHint.trim().ifEmpty { "图片" })
    val id = dshEscapeHtml(attachmentId)
    return """<div class="card-meta">附件 · $name</div><div>引用：<code>$id</code></div>"""
}

private fun dshInlineMarkdownHtml(block: String): String {
    var text = dshEscapeHtml(block)
        .replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")
        .replace(Regex("^&gt;\\s?", RegexOption.MULTILINE), "")
        .replace("\n", "<br>")
    text = Regex("\\[([^\\]]+)]\\(([^)]+)\\)").replace(text) { match ->
        val label = match.groupValues[1]
        val href = match.groupValues[2]
        """<a href="${dshEscapeHtml(href)}">$label</a>"""
    }
    text = Regex("`([^`]+)`").replace(text) { "<code>${it.groupValues[1]}</code>" }
    text = Regex("\\*\\*(.+?)\\*\\*").replace(text) { "<strong>${it.groupValues[1]}</strong>" }
    return text
}

internal fun dshEscapeHtml(value: String): String = buildString(value.length) {
    value.forEach { ch ->
        when (ch) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(ch)
        }
    }
}

private val DshExportRole.label: String
    get() = when (this) {
        DshExportRole.USER -> "你"
        DshExportRole.ASSISTANT -> "助手"
        DshExportRole.TOOL -> "工具"
        DshExportRole.ATTACHMENT -> "附件"
    }

private val DshExportRole.css: String
    get() = when (this) {
        DshExportRole.USER -> "user"
        DshExportRole.ASSISTANT -> "assistant"
        DshExportRole.TOOL -> "tool"
        DshExportRole.ATTACHMENT -> "attachment"
    }

private const val FENCE_SLOT = "\uE000DSHFENCE"
private val FENCE_SLOT_PATTERN = Regex("${Regex.escape(FENCE_SLOT)}(\\d+)${Regex.escape(FENCE_SLOT)}")
private val CODE_FENCE_PATTERN = Regex(
    """^[ \t]*(```|~~~)[^\n]*\n[\s\S]*?^[ \t]*\1[ \t]*$""",
    setOf(RegexOption.MULTILINE),
)
