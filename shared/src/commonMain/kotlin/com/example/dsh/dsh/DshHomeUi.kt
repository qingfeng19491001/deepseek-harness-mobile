package com.example.dsh.dsh

import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.views.View

internal enum class DshSessionSort {
    RECENT,
    TITLE,
}

internal fun dshSortedSessions(
    sessions: List<DshSession>,
    sort: DshSessionSort,
): List<DshSession> = when (sort) {
    DshSessionSort.RECENT -> sessions.sortedWith(
        compareByDescending<DshSession> { it.updatedAt }.thenBy { it.title.lowercase() },
    )
    DshSessionSort.TITLE -> sessions.sortedBy { it.title.lowercase() }
}

internal fun dshParseUpdatedAt(rawLong: Long, rawText: String = ""): Long {
    if (rawLong > 1_000_000_000_000L) return rawLong
    if (rawLong > 1_000_000_000L) return rawLong * 1_000L
    rawText.trim().toLongOrNull()?.let { parsed ->
        if (parsed > 1_000_000_000_000L) return parsed
        if (parsed > 1_000_000_000L) return parsed * 1_000L
    }
    return 0L
}

internal fun dshSessionLifecycleUnsupported(code: String, message: String = ""): Boolean {
    val normalized = "$code $message".lowercase()
    return code == "method-not-found" ||
        code == "unknown-method" ||
        code == "not-found" ||
        code.startsWith("transport-404") ||
        normalized.contains("unknown method") ||
        normalized.contains("no such method") ||
        normalized.contains("method not found")
}

internal fun syncVisibleSessions(
    source: ObservableList<DshSession>,
    dest: ObservableList<DshSession>,
    archivedIds: Set<String> = emptySet(),
    sort: DshSessionSort = DshSessionSort.RECENT,
) {
    val next = dshSortedSessions(
        source.toList().filterNot { it.blank || archivedIds.contains(it.id) },
        sort,
    )
    dest.diffUpdate(next) { old, new -> old.id == new.id }
    val count = minOf(dest.size, next.size)
    for (index in 0 until count) {
        if (dest[index] != next[index]) {
            dest[index] = next[index]
        }
    }
}

internal fun dshNextUnarchivedSession(
    sessions: List<DshSession>,
    archivedIds: Set<String>,
    excludedId: String,
): DshSession? =
    sessions.firstOrNull { !it.blank && it.id != excludedId && !archivedIds.contains(it.id) }
        ?: sessions.firstOrNull { it.blank && it.id != excludedId && !archivedIds.contains(it.id) }

internal fun ViewContainer<*, *>.DshHitButton(onClick: () -> Unit) {
    View {
        attr {
            absolutePositionAllZero()
            backgroundColor(Color(0x00000000))
        }
        event { click { onClick() } }
    }
}

internal fun isConnectionReadyLabel(label: String): Boolean {
    return label.startsWith("已连接") ||
        label.endsWith("已连接") ||
        label.endsWith("已就绪") ||
        label == "连接成功"
}

internal fun isReconnectLabel(label: String): Boolean {
    return label == "远程连接重建中" ||
        label == "扫码连接重建中" ||
        label == "扫码连接重试中" ||
        label == "本地 DSH 连接重建中"
}

internal fun topBarConnectingText(label: String): String {
    val value = label.trim()
    if (value.isEmpty()) return "连接中"
    return value
}

internal const val COMPOSER_HEIGHT = 142f
internal const val CHAT_INITIAL_RENDER_COUNT = 48
internal const val CHAT_MAX_RENDERED_MESSAGES = 128
