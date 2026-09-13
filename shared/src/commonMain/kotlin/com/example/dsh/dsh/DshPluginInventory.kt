package com.example.dsh.dsh

import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

internal const val DSH_MOBILE_PLUGIN_PROTOCOL = "dsh-mobile-plugin/v1"
internal const val DSH_OFFICIAL_FAILED_HINT = "插件启动失败。当前 Host 只返回了失败状态，未提供错误详情。安装电脑侧管理插件后可查看原因。"

internal enum class DshPluginFiberPhase(val wire: String?) {
    PENDING("pending"),
    LOADING("loading"),
    ACTIVE("active"),
    FAILED("failed"),
    UNLOADING("unloading"),
    NONE(null),
    ;

    val label: String
        get() = when (this) {
            PENDING -> "等待加载"
            LOADING -> "正在加载"
            ACTIVE -> "运行中"
            FAILED -> "失败"
            UNLOADING -> "正在卸载"
            NONE -> "未挂载"
        }

    companion object {
        fun fromWire(value: String?): DshPluginFiberPhase = when (value) {
            "pending" -> PENDING
            "loading" -> LOADING
            "active" -> ACTIVE
            "failed" -> FAILED
            "unloading" -> UNLOADING
            else -> NONE
        }
    }
}

internal enum class DshPluginFilter(val id: String, val label: String) {
    ALL("all", "全部"),
    ACTIVE("active", "运行中"),
    DISABLED("disabled", "已停用"),
    FAILED("failed", "失败"),
    TRANSITION("transition", "加载中"),
    ;

    companion object {
        fun fromId(id: String): DshPluginFilter = entries.firstOrNull { it.id == id } ?: ALL
    }
}

internal data class DshPluginEntry(
    val entryId: String,
    val moduleName: String,
    val displayName: String,
    val enabled: Boolean,
    val fiberPhase: DshPluginFiberPhase,
    val configSummary: String,
    val configJson: String = "",
    val error: String = "",
    val protectedEntry: Boolean = false,
    val protectReason: String = "",
) {
    val failed: Boolean get() = fiberPhase == DshPluginFiberPhase.FAILED
    val canEnable: Boolean get() = !protectedEntry && !enabled
    val canDisable: Boolean get() = !protectedEntry && enabled
    val canReload: Boolean get() = !protectedEntry && enabled
}

internal data class DshPluginPreset(
    val id: String,
    val name: String,
    val trust: String = "user",
    val isDefault: Boolean = false,
    val broken: String = "",
    val rows: List<DshPluginEntry> = emptyList(),
)

internal data class DshPluginSnapshot(
    val writable: Boolean,
    val source: String,
    val protocol: String = "",
    val entries: List<DshPluginEntry> = emptyList(),
    val presets: List<DshPluginPreset> = emptyList(),
) {
    val official: Boolean get() = source == "official"
}

internal fun parsePluginSnapshot(value: JSONObject, source: String, writable: Boolean): DshPluginSnapshot {
    val entries = parsePluginEntries(value.optJSONArray("entries") ?: JSONArray())
    val presets = parsePluginPresets(value.optJSONArray("agentPresets") ?: JSONArray())
    return DshPluginSnapshot(
        writable = writable && source != "official",
        source = source,
        protocol = value.optString("protocol"),
        entries = entries,
        presets = presets,
    )
}

internal fun parseOfficialPluginSnapshot(value: JSONObject): DshPluginSnapshot {
    return parsePluginSnapshot(value, source = "official", writable = false).let { snapshot ->
        snapshot.copy(
            entries = snapshot.entries.map { entry ->
                entry.copy(
                    configSummary = when {
                        entry.configJson.isNotEmpty() -> entry.configSummary
                        entry.enabled -> "已启用"
                        else -> "已停用"
                    },
                    error = when {
                        !entry.failed -> ""
                        entry.error.isNotEmpty() -> entry.error
                        else -> DSH_OFFICIAL_FAILED_HINT
                    },
                )
            },
        )
    }
}

internal fun parsePluginEntries(items: JSONArray): List<DshPluginEntry> = buildList {
    for (index in 0 until items.length()) {
        parsePluginEntry(items.optJSONObject(index) ?: continue)?.let(::add)
    }
}

internal fun parsePluginEntry(item: JSONObject): DshPluginEntry? {
    val entryId = item.optString("entryId")
    val moduleName = item.optString("moduleName")
    if (entryId.isEmpty() && moduleName.isEmpty()) return null
    val displayName = item.optString("displayName").ifEmpty { pluginDisplayName(moduleName, entryId) }
    val enabled = item.optBoolean("enabled")
    val phase = DshPluginFiberPhase.fromWire(item.optString("fiberPhase").ifEmpty { null })
    val configObject = item.optJSONObject("config")
    val configJson = configObject?.toString()?.takeIf { it.isNotEmpty() && it != "null" }.orEmpty()
    val summary = item.optString("configSummary").ifEmpty {
        when {
            configJson.isNotEmpty() -> dshJsonPreview(configJson)
            enabled -> "已启用 · 无额外配置"
            else -> "已停用"
        }
    }
    val error = item.optString("error").ifEmpty {
        if (phase == DshPluginFiberPhase.FAILED) DSH_OFFICIAL_FAILED_HINT else ""
    }
    return DshPluginEntry(
        entryId = entryId.ifEmpty { moduleName },
        moduleName = moduleName,
        displayName = displayName,
        enabled = enabled,
        fiberPhase = phase,
        configSummary = summary,
        configJson = configJson,
        error = error,
        protectedEntry = item.optBoolean("protected"),
        protectReason = item.optString("protectReason"),
    )
}

internal fun parsePluginPresets(items: JSONArray): List<DshPluginPreset> = buildList {
    for (index in 0 until items.length()) {
        val item = items.optJSONObject(index) ?: continue
        val id = item.optString("id")
        if (id.isEmpty()) continue
        add(
            DshPluginPreset(
                id = id,
                name = item.optString("name").ifEmpty { id },
                trust = item.optString("trust").ifEmpty { "user" },
                isDefault = item.optBoolean("isDefault"),
                broken = item.optString("broken"),
                rows = parsePluginEntries(item.optJSONArray("rows") ?: JSONArray()),
            ),
        )
    }
}

internal fun pluginDisplayName(moduleName: String, entryId: String = ""): String {
    val unscoped = if (moduleName.startsWith("@") && moduleName.contains("/")) {
        moduleName.substringAfter("/")
    } else {
        moduleName
    }
    val shortened = unscoped
        .removePrefix("cordis:")
        .replace(Regex("^cordis-plugin-"), "")
        .replace(Regex("^dsh-(?:host-|client-)?"), "")
    return shortened.ifEmpty { entryId.ifEmpty { "未命名插件" } }
}

internal fun capabilitiesAllow(value: JSONObject?, feature: String): Boolean {
    if (value == null) return false
    val features = value.optJSONArray("features") ?: return false
    for (index in 0 until features.length()) {
        if (features.optString(index) == feature) return true
    }
    return false
}

internal fun filterPluginEntries(
    entries: List<DshPluginEntry>,
    query: String,
    filter: DshPluginFilter,
): List<DshPluginEntry> {
    val needle = query.trim().lowercase()
    val filtered = entries.filter { entry ->
        matchesPluginQuery(entry, needle) && matchesPluginFilter(entry, filter)
    }
    return if (filter == DshPluginFilter.ALL) {
        filtered.sortedWith(compareByDescending<DshPluginEntry> { it.failed }.thenBy { it.displayName.lowercase() })
    } else {
        filtered
    }
}

internal fun matchesPluginQuery(entry: DshPluginEntry, needle: String): Boolean {
    if (needle.isEmpty()) return true
    return listOf(entry.displayName, entry.moduleName, entry.entryId, entry.configSummary, entry.error)
        .any { it.lowercase().contains(needle) }
}

internal fun matchesPluginFilter(entry: DshPluginEntry, filter: DshPluginFilter): Boolean = when (filter) {
    DshPluginFilter.ALL -> true
    DshPluginFilter.ACTIVE -> entry.enabled && entry.fiberPhase == DshPluginFiberPhase.ACTIVE
    DshPluginFilter.DISABLED -> !entry.enabled
    DshPluginFilter.FAILED -> entry.failed
    DshPluginFilter.TRANSITION -> entry.fiberPhase == DshPluginFiberPhase.PENDING ||
        entry.fiberPhase == DshPluginFiberPhase.LOADING ||
        entry.fiberPhase == DshPluginFiberPhase.UNLOADING
}

internal fun pluginControlLabel(action: String): String = when (action) {
    "enable" -> "启用"
    "disable" -> "停用"
    "reload" -> "重载"
    else -> "操作"
}

internal fun pluginControlPrompt(action: String, displayName: String): String = when (action) {
    "enable" -> "启用「$displayName」后，Host 会按当前配置重新挂载这个插件。"
    "disable" -> "停用「$displayName」后，依赖它的能力会立即失效。核心和连通性插件不可停用。"
    "reload" -> "重载「$displayName」会短暂中断它提供的能力，然后用当前配置重新启动。"
    else -> "确认对「$displayName」执行该操作？"
}
