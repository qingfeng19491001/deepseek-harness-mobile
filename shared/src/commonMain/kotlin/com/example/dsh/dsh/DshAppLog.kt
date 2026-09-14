package com.example.dsh.dsh

/**
 * In-app ring buffer for connection / RPC / mux diagnostics.
 * Chunk bodies, tool JSON and attachment Base64 are never stored.
 */
internal enum class DshLogLevel(val label: String) {
    DEBUG("Debug"),
    INFO("Info"),
    WARN("Warn"),
    ERROR("Error"),
}

internal data class DshLogEntry(
    val id: Long,
    val timeMs: Long,
    val level: DshLogLevel,
    val eventType: String,
    val sessionId: String,
    val summary: String,
)

internal enum class DshLogFilter(val id: String, val label: String, val eventType: String? = null) {
    ALL("all", "全部"),
    ERROR("error", "Error"),
    CHUNK("chunk", "assistant/chunk", "assistant/chunk"),
    TOOL_CALL("tool-call", "tool/call", "tool/call"),
    TOOL_RESULT("tool-result", "tool/result", "tool/result"),
    TURN_END("turn-end", "turn/end", "turn/end"),
    RECONNECT("reconnect", "重连/重试"),
}

internal object DshAppLog {
    const val EVENT = "dshAppLogChanged"
    const val CRASH_KEY = "dsh_last_crash"
    const val CAPACITY = 1200

    var publisher: (() -> Unit)? = null

    private val buffer = ArrayDeque<DshLogEntry>(CAPACITY)
    private var nextId = 1L
    private var lastPublishMs = 0L

    fun record(
        level: DshLogLevel,
        eventType: String,
        summary: String,
        sessionId: String = "",
        timeMs: Long = nowMs(),
    ) {
        val sanitized = dshSanitizeLogText(summary)
        if (sanitized.isEmpty()) return
        val entry = DshLogEntry(
            id = nextId++,
            timeMs = timeMs,
            level = level,
            eventType = eventType.ifBlank { "app" },
            sessionId = sessionId,
            summary = sanitized,
        )
        synchronized(buffer) {
            if (buffer.size >= CAPACITY) buffer.removeFirst()
            buffer.addLast(entry)
        }
        publishThrottled()
    }

    fun ingestLine(level: DshLogLevel, message: String) {
        val classified = dshClassifyLogLine(message)
        val mergedLevel = when {
            classified.level == DshLogLevel.ERROR -> DshLogLevel.ERROR
            level == DshLogLevel.ERROR -> DshLogLevel.ERROR
            classified.level == DshLogLevel.WARN || level == DshLogLevel.WARN -> DshLogLevel.WARN
            classified.level == DshLogLevel.DEBUG -> DshLogLevel.DEBUG
            else -> level
        }
        record(mergedLevel, classified.eventType, classified.summary, classified.sessionId)
    }

    fun snapshot(): List<DshLogEntry> = synchronized(buffer) { buffer.toList() }

    fun filtered(
        filter: DshLogFilter,
        query: String,
        sessionQuery: String,
    ): List<DshLogEntry> {
        val needle = query.trim().lowercase()
        val sessionNeedle = sessionQuery.trim().lowercase()
        return snapshot().asReversed().filter { entry ->
            when (filter) {
                DshLogFilter.ALL -> true
                DshLogFilter.ERROR -> entry.level == DshLogLevel.ERROR
                DshLogFilter.RECONNECT -> dshIsReconnect(entry)
                else -> filter.eventType != null && entry.eventType == filter.eventType
            } &&
                (sessionNeedle.isEmpty() || entry.sessionId.lowercase().contains(sessionNeedle)) &&
                (
                    needle.isEmpty() ||
                        entry.summary.lowercase().contains(needle) ||
                        entry.eventType.lowercase().contains(needle) ||
                        entry.sessionId.lowercase().contains(needle) ||
                        entry.level.label.lowercase().contains(needle)
                    )
        }
    }

    fun exportText(
        entries: List<DshLogEntry>,
        connectionMode: String = "",
        appVersion: String = "",
        device: String = "",
    ): String = buildString {
        appendLine("DSH Mobile diagnostics")
        if (connectionMode.isNotEmpty()) appendLine("connection=$connectionMode")
        if (appVersion.isNotEmpty()) appendLine("app=$appVersion")
        if (device.isNotEmpty()) appendLine("device=$device")
        appendLine("count=${entries.size}")
        appendLine()
        entries.forEach { entry ->
            append(dshFormatLogLine(entry))
            append('\n')
        }
    }

    fun clear() {
        synchronized(buffer) { buffer.clear() }
        publisher?.invoke()
    }

    fun resetForTests() {
        synchronized(buffer) {
            buffer.clear()
            nextId = 1L
        }
        lastPublishMs = 0L
        publisher = null
    }

    private fun publishThrottled() {
        val now = nowMs()
        if (now - lastPublishMs < 80L) return
        lastPublishMs = now
        publisher?.invoke()
    }
}

internal fun dshFormatLogLine(entry: DshLogEntry): String {
    val session = if (entry.sessionId.isEmpty()) "-" else entry.sessionId
    return "${entry.timeMs} ${entry.level.label} ${entry.eventType} session=$session ${entry.summary}"
}

internal fun dshSanitizeLogText(raw: String): String {
    var text = raw.replace("\r", "\\r").replace("\n", "\\n")
    text = PREVIEW_PATTERN.replace(text, "preview=<redacted>")
    text = BASE64_PATTERN.replace(text, "<base64-omitted>")
    text = BEARER_PATTERN.replace(text, "Bearer <redacted>")
    SECRET_PATTERNS.forEach { pattern ->
        text = pattern.replace(text) { match ->
            val label = match.groupValues.getOrNull(1)?.ifEmpty { "secret" } ?: "secret"
            "$label=<redacted>"
        }
    }
    if (text.length > 480) text = text.take(480) + "…(truncated)"
    return text.trim()
}

internal data class DshClassifiedLog(
    val level: DshLogLevel,
    val eventType: String,
    val sessionId: String,
    val summary: String,
)

internal fun dshClassifyLogLine(message: String): DshClassifiedLog {
    val sessionId = SESSION_PATTERN.find(message)?.groupValues?.getOrNull(1).orEmpty()
    val lower = message.lowercase()
    val eventType = when {
        lower.contains("assistant/chunk") || lower.contains("render.stream") ||
            (lower.contains("chunk") && !lower.contains("tool")) -> "assistant/chunk"
        lower.contains("tool/result") || lower.contains("tool.result") -> "tool/result"
        lower.contains("tool/call") || lower.contains("tool.call") -> "tool/call"
        lower.contains("turn/end") || lower.contains("turn.end") || lower.contains("ui.complete") -> "turn/end"
        lower.contains("reconnect") || lower.contains("retry") -> "reconnect"
        lower.contains("disconnect") || lower.contains("ws.closed") || lower.contains("closed") && lower.contains("ws") -> "ws.closed"
        lower.contains("ws.open") || lower.contains("connect requested") || lower.contains("connected") -> "ws.open"
        lower.contains("rpc") -> "rpc"
        lower.contains("ui.error") || lower.contains("history-fail") -> "session.error"
        else -> message.substringBefore(' ').substringBefore('.').ifEmpty { "app" }
    }
    val level = when {
        lower.contains("error") || lower.contains("fail") || lower.contains("exception") -> DshLogLevel.ERROR
        eventType == "assistant/chunk" || lower.startsWith("render.") -> DshLogLevel.DEBUG
        lower.contains("retry") || lower.contains("warn") -> DshLogLevel.WARN
        else -> DshLogLevel.INFO
    }
    val summary = if (eventType == "assistant/chunk") {
        buildString {
            append("chunk")
            val chars = CHARS_PATTERN.find(message)?.groupValues?.getOrNull(1)
            val seq = SEQ_PATTERN.find(message)?.groupValues?.getOrNull(1)
            if (chars != null) append(" bytes=$chars")
            if (seq != null) append(" seq=$seq")
            if (sessionId.isNotEmpty()) append(" session=$sessionId")
        }
    } else {
        message
    }
    return DshClassifiedLog(level, eventType, sessionId, summary)
}

internal fun dshIsReconnect(entry: DshLogEntry): Boolean {
    if (entry.eventType == "reconnect" || entry.eventType == "ws.open" || entry.eventType == "ws.closed") return true
    val text = "${entry.eventType} ${entry.summary}".lowercase()
    return text.contains("retry") || text.contains("reconnect") || text.contains("disconnect")
}

private fun nowMs(): Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()

private val SESSION_PATTERN = Regex("""session(?:Id)?=([^\s'",]+)""", RegexOption.IGNORE_CASE)
private val CHARS_PATTERN = Regex("""chars=(\d+)""", RegexOption.IGNORE_CASE)
private val SEQ_PATTERN = Regex("""seq=(\d+)""", RegexOption.IGNORE_CASE)
private val PREVIEW_PATTERN = Regex("""preview='(?:\\'|[^'])*'""")
private val BASE64_PATTERN = Regex("""(?:data:image\/[a-zA-Z0-9.+-]+;base64,)?[A-Za-z0-9+/]{80,}={0,2}""")
private val BEARER_PATTERN = Regex("""(?i)bearer\s+[A-Za-z0-9._\-+/=]+""")
private val SECRET_PATTERNS = listOf(
    Regex("""(?i)["']?(authorization|api[_-]?key|access[_-]?ticket|clienttoken|hosttoken|token)["']?\s*[:=]\s*["']?([^\s'",}]+)"""),
)
