package com.example.dsh.dsh

import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.timer.setTimeout

/** Browser Host paths mirrored by the native client. */
internal object DshHostProtocol {
    const val API_PREFIX = "/api"
    const val MUX_EVENTS_PATH = "$API_PREFIX/events.mux"
    const val HOST_EVENTS_PATH = "$API_PREFIX/events.host"
    const val HOST_DESCRIBE = "host.describe"
    const val HOST_LIST_DIRECTORY = "host.listDirectory"
    const val HOST_CREATE_DIRECTORY = "host.createDirectory"
    const val WORKSPACE_LIST = "workspace.list"
    const val WORKSPACE_CREATE = "workspace.create"
    const val WORKSPACE_RENAME = "workspace.rename"
    const val WORKSPACE_DELETE = "workspace.delete"
    const val WORKSPACE_INSERT_BEFORE = "workspace.insertBefore"
    const val SESSION_LIST = "session.list"
    const val SESSION_CREATE = "session.create"
    const val SESSION_HISTORY = "session.history"
    const val SESSION_MODELS = "session.models"
    const val SESSION_SELECT_MODEL = "session.selectModel"
    const val SESSION_PROMPT = "session.prompt"
    const val SESSION_CANCEL = "session.cancel"
    const val SESSION_UPDATE_QUEUE = "session.updateQueue"
    const val SESSION_RENAME = "session.rename"
    const val SESSION_FORK = "session.fork"
    const val SESSION_ATTACHMENT = "session.attachment"
    const val WORKSPACE_ARCHIVE_SESSION = "workspace.archiveSession"
    const val WORKSPACE_UNARCHIVE_SESSION = "workspace.unarchiveSession"
    const val WORKSPACE_DELETE_SESSION = "workspace.deleteSession"
    const val SETTINGS_DESCRIBE = "settings.describe"
    const val CREDENTIALS_DESCRIBE = "credentials.describe"
    const val CREDENTIALS_SET = "credentials.set"
    const val LLM_PROVIDERS = "llm.providers"
    const val SKILL_LIST = "skill.list"
    const val AGENT_PRESET_LIST = "agentPreset.list"
    const val GOAL_EDIT = "goal.edit"
    const val GOAL_PAUSE = "goal.pause"
    const val GOAL_RESUME = "goal.resume"
    const val GOAL_CLEAR = "goal.clear"
    const val RESPOND_PATH = "$API_PREFIX/respond"
    const val SESSION_EXPORT_PATH = "$API_PREFIX/session.export"
    const val PLUGIN_INVENTORY_LIST = "pluginInventory/list"
    const val MOBILE_PLUGIN_RPC_PATH = "/dsh-mobile/rpc"
    const val MOBILE_CAPABILITIES = "dsh.mobile.capabilities"
    const val MOBILE_PLUGIN_INVENTORY = "dsh.plugin.inventory"
    const val MOBILE_PLUGIN_GET = "dsh.plugin.get"
    const val MOBILE_PLUGIN_CONTROL = "dsh.plugin.control"

}

internal data class DshHostConnection(val baseUrl: String, val token: String = "")

internal fun parseHostRpcBody(
    data: JSONObject,
    method: String,
    allowBareValue: Boolean = false,
): Pair<JSONObject?, DshRpcError?> {
    val result = data.optJSONObject("result")
    if (result != null) {
        if (!result.optBoolean("ok")) {
            val error = result.optJSONObject("error")
            return null to DshRpcError(
                error?.optString("code").orEmpty().ifEmpty { "internal" },
                error?.optString("message").orEmpty().ifEmpty { "$method 失败" },
                error?.optJSONObject("details")?.toString() ?: "{}",
            )
        }
        return result.optJSONObject("value") to null
    }
    if (allowBareValue) {
        if (data.optJSONArray("entries") != null) return data to null
        data.optJSONObject("value")?.let { return it to null }
    }
    return null to DshRpcError("bad-response", "$method 返回了非法 RPC 信封")
}

internal object DshWebTimelineParser {
    fun parseWebTimeline(events: JSONArray): List<DshWebTimelineItem> {
        val result = mutableListOf<DshWebTimelineItem>()
        val toolModels = mutableMapOf<String, DshRemoteToolCallModel>()
        val partials = linkedMapOf<String, StringBuilder>()
        for (index in 0 until events.length()) {
            val entry = events.optJSONObject(index) ?: continue
            val event = entry.optJSONObject("event") ?: entry
            val seq = event.optInt("seq", index)
            val type = event.optString("type")
            val data = event.optJSONObject("data") ?: continue
            when (type) {
                "user/message" -> {
                    val content = data.optJSONArray("content")
                    val text = textFromBlocks(content)
                    val images = dshImageRefsFromBlocks(content)
                    if (text.isEmpty() && images.isEmpty()) continue
                    val source = data.optJSONObject("source")
                    val sourceKind = source?.optString("kind").orEmpty()
                    if (sourceKind == "user") {
                        result += DshWebTimelineItem(
                            "user-$seq",
                            DshWebTimelineItem.Kind.USER,
                            text,
                            attachments = images,
                        )
                    } else {
                        result += DshWebTimelineItem(
                            key = "context-$seq",
                            kind = DshWebTimelineItem.Kind.CONTEXT,
                            text = text,
                            sourceLabel = contextSummary(source),
                            source = source,
                        )
                    }
                }
                "assistant/message" -> {
                    val message = data.optJSONObject("message") ?: data
                    val key = "${data.optInt("turn")}:${data.optInt("step")}"
                    val blocks = message.optJSONArray("content") ?: JSONArray()
                    appendAssistantBlocks(result, seq, blocks)
                    partials.remove(key)
                }
                "assistant/chunk" -> {
                    val key = "${data.optInt("turn")}:${data.optInt("step")}"
                    val chunk = data.optJSONObject("chunk") ?: JSONObject()
                    val text = chunk.optString("text").ifEmpty { chunk.optString("delta") }
                    if (text.isNotEmpty() &&
                        chunk.optString("type") in setOf("", "text", "text-delta", "text_delta")
                    ) {
                        partials.getOrPut(key) { StringBuilder() }.append(text)
                    }
                }
                "tool/call" -> {
                    val remoteTool = DshRemoteToolCallModels.fromHistoryCall(entry) ?: continue
                    if (remoteTool.callId.isNotEmpty()) toolModels[remoteTool.callId] = remoteTool
                    result += DshWebTimelineItem(
                        key = "tool-$seq",
                        kind = DshWebTimelineItem.Kind.TOOL,
                        toolName = remoteTool.toolName,
                        input = remoteTool.input,
                        running = remoteTool.running,
                        callId = remoteTool.callId,
                        callSeq = seq,
                        cardType = remoteTool.cardType,
                        cardTitle = remoteTool.title,
                        cardBody = remoteTool.body,
                        remoteTool = remoteTool,
                    )
                }
                "tool/result" -> {
                    val message = data.optJSONObject("message")
                    val resultBlock = message?.optJSONArray("content")?.optJSONObject(0)
                    val callId = resultBlock?.optString("toolCallId")
                        ?: message?.optJSONObject("source")?.optString("callId")
                        ?: data.optString("callId")
                    val previous = toolModels[callId]
                    val settled = DshRemoteToolCallModels.settleHistoryResult(previous, entry) ?: continue
                    if (settled.callId.isNotEmpty()) toolModels[settled.callId] = settled
                    val call = result.lastOrNull {
                        it.kind == DshWebTimelineItem.Kind.TOOL &&
                            it.callSeq < seq &&
                            (settled.callId.isEmpty() || it.callId == settled.callId)
                    }
                    if (call != null) {
                        val callIndex = result.indexOf(call)
                        result[callIndex] = call.copy(
                            toolName = settled.toolName,
                            input = settled.input,
                            output = settled.output,
                            running = settled.running,
                            error = settled.error,
                            cardType = settled.cardType,
                            cardTitle = settled.title,
                            cardBody = settled.body,
                            remoteTool = settled,
                        )
                    } else {
                        result += DshWebTimelineItem(
                            key = "tool-$seq",
                            kind = DshWebTimelineItem.Kind.TOOL,
                            toolName = settled.toolName,
                            input = settled.input,
                            output = settled.output,
                            running = settled.running,
                            callId = settled.callId,
                            callSeq = seq,
                            error = settled.error,
                            cardType = settled.cardType,
                            cardTitle = settled.title,
                            cardBody = settled.body,
                            remoteTool = settled,
                        )
                    }
                }
                "turn/end" -> {
                    data.optJSONObject("reason")?.optJSONObject("error")?.optString("message")
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { result += DshWebTimelineItem("turn-error-$seq", DshWebTimelineItem.Kind.ERROR, it) }
                }
            }
        }
        partials.forEach { (key, text) ->
            if (text.isNotEmpty()) {
                result += DshWebTimelineItem(
                    key = "partial-$key",
                    kind = DshWebTimelineItem.Kind.ASSISTANT,
                    text = text.toString(),
                )
            }
        }
        return result.filterNot { it.kind == DshWebTimelineItem.Kind.USER && it.isRuntimeContextSnapshot() }
    }
}

private fun DshWebTimelineItem.isRuntimeContextSnapshot(): Boolean {
    return text.startsWith("Current runtime context. This snapshot supersedes earlier runtime-context snapshots.")
}

internal fun contextSourceLabel(source: JSONObject?): String {
    if (source == null) return "未知来源"
    val kind = source.optString("kind")
    return when (kind) {
        "skill-invocation" -> source.optString("name").takeIf { it.isNotEmpty() } ?: kind
        "plugin" -> source.optString("plugin").takeIf { it.isNotEmpty() } ?: kind
        "session-reference" -> sourceLabels(source, "references", "label").takeIf { it.isNotEmpty() } ?: kind
        "agent-instructions" -> sourceLabels(source, "changes", "path").takeIf { it.isNotEmpty() } ?: kind
        else -> kind.takeIf { it.isNotEmpty() } ?: source.optString("name").takeIf { it.isNotEmpty() } ?: "未知来源"
    }
}

internal fun contextSummary(source: JSONObject?): String {
    if (source?.optString("form") == "notice") {
        source.optString("summary").takeIf { it.isNotEmpty() }?.let { return it }
    }
    return contextSourceLabel(source)
}

private fun sourceLabels(source: JSONObject, member: String, field: String): String {
    val values = source.optJSONArray(member) ?: return ""
    val labels = mutableListOf<String>()
    for (index in 0 until values.length()) {
        val value = values.optJSONObject(index) ?: continue
        val label = value.optString(field).takeIf { it.isNotEmpty() } ?: continue
        if (!labels.contains(label)) labels += label
    }
    return labels.joinToString(", ")
}

private fun parseArchivedSessionIds(items: JSONArray): Set<String> = buildSet {
    for (index in 0 until items.length()) {
        items.optString(index)?.takeIf { it.isNotEmpty() }?.let(::add)
    }
}

internal fun toolInputSummary(value: Any?): String = when (value) {
    null -> ""
    is String -> value
    else -> value.toString()
}

internal fun toolOutputSummary(value: Any?): String = when (value) {
    null -> ""
    is String -> value
    is JSONArray -> textFromBlocks(value)
    else -> value.toString()
}

internal fun toolCardType(view: JSONObject): DshToolCardType = when (view.optString("card")) {
    "terminal" -> DshToolCardType.TERMINAL
    "read" -> DshToolCardType.READ
    "diff" -> DshToolCardType.DIFF
    "search" -> DshToolCardType.SEARCH
    "web" -> DshToolCardType.WEB
    else -> DshToolCardType.GENERIC
}

internal fun diffBody(view: JSONObject): String {
    val diffs = view.optJSONArray("diffs") ?: JSONArray()
    return buildString {
        for (index in 0 until diffs.length()) {
            val diff = diffs.optJSONObject(index) ?: continue
            appendLine(diff.optString("path"))
            appendLine("--- old")
            appendLine("+++ new")
            appendLine(diff.optString("oldText"))
            appendLine(diff.optString("newText"))
        }
    }.trim()
}

internal fun toolResultBody(type: DshToolCardType, view: JSONObject, fallback: String): String {
    return when (type) {
        DshToolCardType.TERMINAL -> view.optString("output").ifEmpty { fallback }
        DshToolCardType.READ -> readBody(view)
        DshToolCardType.DIFF -> diffBody(view)
        DshToolCardType.SEARCH -> searchBody(view)
        DshToolCardType.WEB -> webBody(view)
        else -> fallback
    }
}

internal fun readBody(view: JSONObject): String {
    val lines = view.optJSONArray("lines") ?: JSONArray()
    return buildString {
        for (index in 0 until lines.length()) {
            val line = lines.optJSONObject(index) ?: continue
            appendLine("${line.optInt("number")}\t${line.optString("text")}")
        }
    }.trim()
}

internal fun searchBody(view: JSONObject): String {
    return when (view.optString("shape")) {
        "paths" -> {
            val paths = view.optJSONArray("paths") ?: JSONArray()
            buildString {
                for (index in 0 until paths.length()) appendLine(paths.optString(index))
            }.trim()
        }
        else -> {
            val files = view.optJSONArray("files") ?: JSONArray()
            buildString {
                for (index in 0 until files.length()) {
                    val file = files.optJSONObject(index) ?: continue
                    appendLine(file.optString("path"))
                    val matches = file.optJSONArray("matches") ?: JSONArray()
                    for (matchIndex in 0 until matches.length()) {
                        val match = matches.optJSONObject(matchIndex) ?: continue
                        appendLine("${match.optInt("lineNumber")}\t${match.optString("line")}")
                    }
                }
            }.trim()
        }
    }
}

internal fun webBody(view: JSONObject): String {
    return when (view.optString("kind")) {
        "fetch" -> "${view.optString("url")}\nHTTP ${view.optInt("statusCode")}"
        else -> {
            val sources = view.optJSONArray("sources") ?: JSONArray()
            buildString {
                appendLine(view.optString("answer"))
                for (index in 0 until sources.length()) {
                    val source = sources.optJSONObject(index) ?: continue
                    appendLine("- ${source.optString("title").ifEmpty { source.optString("url") }} ${source.optString("url")}")
                }
            }.trim()
        }
    }
}

internal fun textFromBlocks(blocks: JSONArray?): String {
    if (blocks == null) return ""
    return buildString {
        for (index in 0 until blocks.length()) {
            val block = blocks.optJSONObject(index) ?: continue
            if (block.optString("type") == "text") append(block.optString("text"))
        }
    }
}

internal fun appendAssistantBlocks(
    result: MutableList<DshWebTimelineItem>,
    seq: Int,
    blocks: JSONArray?,
) {
    if (blocks == null) return
    for (blockIndex in 0 until blocks.length()) {
        val block = blocks.optJSONObject(blockIndex) ?: continue
        when (block.optString("type")) {
            "text" -> block.optString("text").takeIf { it.isNotEmpty() }?.let {
                result += DshWebTimelineItem("text-$seq-$blockIndex", DshWebTimelineItem.Kind.ASSISTANT, it)
            }
            "reasoning" -> block.optString("text").takeIf { it.isNotEmpty() }?.let {
                result += DshWebTimelineItem(
                    "reasoning-$seq-$blockIndex",
                    DshWebTimelineItem.Kind.REASONING,
                    it,
                )
            }
            "image" -> dshParseImageRef(block.optJSONObject("attachment"))?.let { ref ->
                result += DshWebTimelineItem(
                    "image-$seq-$blockIndex",
                    DshWebTimelineItem.Kind.IMAGE,
                    attachmentId = ref.attachmentId,
                    attachments = listOf(ref),
                )
            }
            "tool-call" -> Unit
            else -> result += DshWebTimelineItem(
                "block-$seq-$blockIndex",
                DshWebTimelineItem.Kind.UNKNOWN_BLOCK,
                block.toString(),
            )
        }
    }
}

internal data class DshRpcCall(
    val rpcId: String,
    private val cancelAction: () -> Unit = {},
) {
    fun cancel() = cancelAction()
}

private data class QueuedRpc(
    val generation: Long,
    val method: String,
    val payload: JSONObject,
    val rpcId: String,
    val timeoutSeconds: Int = 30,
    val callback: (JSONObject?, DshRpcError?, String) -> Unit,
)

/** Owns one long-lived mux/host WebSocket connection generation. */
internal class DshHostConnectionRuntime(
    private val network: NetworkModule,
    private val webSocket: DshWebSocketModule,
    private val connection: DshHostConnection,
    private val pagerId: String,
    private val onFrame: (DshDownlinkFrame) -> Unit,
    private val onState: (DshHostRuntimeState) -> Unit = {},
    private val onWorkspaceBaseline: (JSONObject) -> Unit = {},
    private val onSessionBaseline: (JSONObject) -> Unit = {},
    private val onQueueSnapshot: (String) -> Unit = {},
    private val onJobsSnapshot: (String) -> Unit = {},
    private val onSessionStatus: (String, Boolean) -> Unit = { _, _ -> },
    private val onSessionEvent: (String, DshRawSessionEvent) -> Unit = { _, _ -> },
    private val onRemoteEvent: (String) -> Unit = {},
    private val onProjection: (String, String, String, Int) -> Unit = { _, _, _, _ -> },
) {
    private var generation = 0L
    private var rpcSequence = 0L
    private var muxOpen = false
    private var hostOpen = false
    private var hostDescribed = false
    private var productReady = false
    private var stopped = false
    private var starting = false
    private var muxHandle: DshWebSocketHandle? = null
    private var eventsStreamId: String = ""
    private var eventsClientId: String = ""
    private var workspaceStreamId: String = ""
    private var controlStreamId: String = ""
    private val sessionFollows = mutableMapOf<String, String>()
    private val sessionSnapshots = mutableMapOf<String, JSONObject>()
    private val sessionFollowWaiters = mutableMapOf<String, MutableList<(JSONArray?, String?) -> Unit>>()
    private val bufferedFrames = mutableListOf<DshDownlinkFrame>()
    private val queued = mutableListOf<QueuedRpc>()

    init { start() }

    fun currentState(): DshHostRuntimeState = DshHostRuntimeState(
        phase = when {
            stopped -> DshHostRuntimePhase.STOPPED
            productReady -> DshHostRuntimePhase.READY
            hostDescribed -> DshHostRuntimePhase.SYNCING
            muxOpen || hostOpen -> DshHostRuntimePhase.HOST_HANDSHAKE
            starting -> DshHostRuntimePhase.CONNECTING
            else -> DshHostRuntimePhase.DISCONNECTED
        },
        generation = generation,
        muxOpen = muxOpen,
        hostOpen = hostOpen,
    )

    fun start() {
        if (stopped || starting || productReady) return
        starting = true
        generation += 1
        val myGeneration = generation
        muxOpen = false
        hostOpen = false
        hostDescribed = false
        productReady = false
        eventsStreamId = ""
        eventsClientId = ""
        workspaceStreamId = ""
        controlStreamId = ""
        sessionFollows.clear()
        sessionSnapshots.clear()
        sessionFollowWaiters.clear()
        bufferedFrames.clear()
        publish(DshHostRuntimePhase.CONNECTING, "正在打开 DSH 事件流")
        muxHandle = webSocket.connect(webSocketUrl(DshRemoteMux.PATH), connection.token) { event ->
            handleSocketEvent(myGeneration, event)
        }
    }

    fun stop() {
        stopped = true
        generation += 1
        starting = false
        productReady = false
        bufferedFrames.clear()
        queued.clear()
        failAllSessionFollows("连接已停止")
        muxHandle?.close()
        muxHandle = null
        publish(DshHostRuntimePhase.STOPPED, "连接已停止")
    }

    fun call(
        method: String,
        payload: JSONObject,
        timeoutSeconds: Int = REQUEST_TIMEOUT_SECONDS,
        callback: (JSONObject?, DshRpcError?, String) -> Unit,
    ): DshRpcCall {
        val myGeneration = generation
        val rpcId = nextRpcId(myGeneration)
        val request = QueuedRpc(myGeneration, method, payload, rpcId, timeoutSeconds, callback)
        if (stopped) callback(null, DshRpcError("cancelled", "连接已停止"), rpcId)
        else if (productReady) dispatch(request)
        else queued += request
        return DshRpcCall(rpcId) { queued.removeAll { it.rpcId == rpcId } }
    }

    fun callPath(
        path: String,
        method: String,
        payload: JSONObject,
        callback: (JSONObject?, DshRpcError?, String) -> Unit,
    ): DshRpcCall {
        val myGeneration = generation
        val rpcId = nextRpcId(myGeneration)
        if (stopped) {
            callback(null, DshRpcError("cancelled", "连接已停止"), rpcId)
        } else if (!productReady) {
            callback(null, DshRpcError("not-ready", "连接尚未就绪"), rpcId)
        } else {
            postRpc(myGeneration, path, method, payload, rpcId, typert = false, callback)
        }
        return DshRpcCall(rpcId)
    }

    fun callTypert(
        method: String,
        args: JSONObject,
        callback: (JSONObject?, DshRpcError?, String) -> Unit,
    ): DshRpcCall {
        val myGeneration = generation
        val rpcId = nextRpcId(myGeneration)
        if (stopped) {
            callback(null, DshRpcError("cancelled", "连接已停止"), rpcId)
        } else if (!productReady) {
            callback(null, DshRpcError("not-ready", "连接尚未就绪"), rpcId)
        } else {
            postRpc(myGeneration, "${DshHostProtocol.API_PREFIX}/$method", method, args, rpcId, typert = true, callback)
        }
        return DshRpcCall(rpcId)
    }

    /** POST /api/$events/result answers one Host waterfall on the current event generation. */
    fun respond(rpcId: String, value: JSONObject, callback: (Boolean, String) -> Unit) {
        if (rpcId.isEmpty()) {
            DshStreamLog.question("respond.http.skip empty-rpcId session=${value.optString("sessionId")}")
            callback(false, "缺少请求编号")
            return
        }
        if (!productReady || eventsClientId.isEmpty()) {
            DshStreamLog.question("respond.http.skip not-ready rpcId=$rpcId")
            callback(false, "连接尚未就绪")
            return
        }
        val myGeneration = generation
        val resultRpcId = nextRpcId(myGeneration)
        val body = JSONObject().apply {
            put("type", "client-request")
            put("rpcId", resultRpcId)
            put("method", DshRemoteMux.EVENTS_RESULT)
            put("payload", JSONObject().put("args", JSONObject().apply {
                put("clientId", eventsClientId)
                put("eventId", rpcId)
                put("outcome", JSONObject().apply {
                    put("kind", "result")
                    put("value", value)
                })
            }))
        }
        val headers = JSONObject().apply {
            put("Content-Type", "application/json")
            if (connection.token.isNotEmpty()) put("Authorization", "Bearer ${connection.token}")
        }
        val url = "${connection.baseUrl.trimEnd('/')}${DshHostProtocol.API_PREFIX}/${DshRemoteMux.EVENTS_RESULT}"
        DshStreamLog.question(
            "respond.http.start rpcId=$rpcId session=${value.optString("sessionId")} url=$url body='${DshStreamLog.preview(body.toString(), 400)}'",
        )
        network.httpRequest(
            url, true, body, headers, null, REQUEST_TIMEOUT_SECONDS,
        ) { data, success, errorMsg, response ->
            if (stopped || myGeneration != generation) {
                DshStreamLog.question("respond.http.cancel rpcId=$rpcId")
                callback(false, "generation-cancelled")
                return@httpRequest
            }
            if (!success) {
                DshStreamLog.question(
                    "respond.http.fail rpcId=$rpcId status=${response.statusCode ?: 0} error='$errorMsg' body='${DshStreamLog.preview(data.toString(), 400)}'",
                )
                callback(false, "respond failed (${response.statusCode ?: 0}): $errorMsg")
                return@httpRequest
            }
            val (accepted, reason) = parseRespondReceipt(data)
            DshStreamLog.question(
                "respond.http.done rpcId=$rpcId accepted=$accepted reason='$reason' status=${response.statusCode ?: 0} body='${DshStreamLog.preview(data.toString(), 400)}'",
            )
            callback(accepted, reason)
        }
    }

    private fun handleSocketEvent(myGeneration: Long, event: DshWebSocketEvent) {
        if (stopped || myGeneration != generation) return
        when (event.kind) {
            DshWebSocketEventKind.OPEN -> {
                eventsStreamId = nextStreamId(myGeneration)
                muxHandle?.send(DshRemoteMux.openMessage(eventsStreamId, DshRemoteMux.EVENTS_ENDPOINT))
                publish(DshHostRuntimePhase.HOST_HANDSHAKE, "正在打开 DSH 事件流")
            }
            DshWebSocketEventKind.FRAME -> handleMuxServerMessage(myGeneration, event.data)
            DshWebSocketEventKind.ERROR, DshWebSocketEventKind.CLOSED -> invalidateGeneration(
                myGeneration, event.message.ifEmpty { "DSH 事件流已断开" },
            )
        }
    }

    private fun handleMuxServerMessage(myGeneration: Long, raw: String) {
        val message = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val streamId = message.optString("streamId")
        when (message.optString("type")) {
            "item" -> handleMuxItem(myGeneration, streamId, message.optJSONObject("value") ?: JSONObject())
            "end" -> handleMuxEnd(myGeneration, streamId)
            "error" -> handleMuxError(
                myGeneration,
                streamId,
                message.optJSONObject("error")?.optString("message").orEmpty().ifEmpty { "DSH 事件流失败" },
            )
        }
    }

    private fun handleMuxItem(myGeneration: Long, streamId: String, value: JSONObject) {
        if (streamId == eventsStreamId) {
            if (value.optString("type") == "ready") {
                eventsClientId = value.optString("clientId")
                muxOpen = true
                hostOpen = true
                publish(DshHostRuntimePhase.HOST_HANDSHAKE, "事件流已连接")
                if (!hostDescribed) completeHandshake(myGeneration)
                return
            }
            val mapped = DshRemoteMux.mapEventsItem(value) ?: return
            emitMappedFrame(myGeneration, mapped.first, mapped.second)
            return
        }
        if (streamId == workspaceStreamId) {
            val mapped = DshRemoteMux.mapWorkspaceItem(value) ?: return
            if (mapped.first == "baseline") {
                finishWorkspaceBaseline(myGeneration, mapped.second, null)
            } else {
                emitMappedFrame(myGeneration, DshEventStream.HOST, mapped.second)
            }
            return
        }
        if (streamId == controlStreamId) {
            handleControlItem(myGeneration, value)
            return
        }
        val sessionId = sessionFollows.entries.firstOrNull { it.value == streamId }?.key ?: return
        if (value.optString("type") == "snapshot") {
            sessionSnapshots[sessionId] = value
            val events = DshRemoteMux.snapshotRecords(value)
            val waiters = sessionFollowWaiters.remove(sessionId).orEmpty()
            waiters.forEach { it(events, null) }
            return
        }
        val payload = DshRemoteMux.mapFollowItem(sessionId, value) ?: return
        emitMappedFrame(myGeneration, DshEventStream.MUX, payload)
    }

    private fun handleMuxEnd(myGeneration: Long, streamId: String) {
        when (streamId) {
            eventsStreamId -> invalidateGeneration(myGeneration, "DSH 事件流已结束")
            workspaceStreamId -> finishWorkspaceBaseline(myGeneration, DshRemoteMux.emptyWorkspaceBaseline(), null)
            controlStreamId -> Unit
            else -> failSessionFollow(streamId, "会话事件流已结束")
        }
    }

    private fun handleMuxError(myGeneration: Long, streamId: String, message: String) {
        if (streamId == eventsStreamId) {
            invalidateGeneration(myGeneration, message)
            return
        }
        if (streamId == workspaceStreamId) {
            finishWorkspaceBaseline(myGeneration, DshRemoteMux.emptyWorkspaceBaseline(), null)
            return
        }
        if (streamId == controlStreamId) return
        failSessionFollow(streamId, message)
    }

    private fun emitMappedFrame(myGeneration: Long, stream: DshEventStream, payload: JSONObject) {
        bufferedFrames += DshDownlinkFrame(myGeneration, stream, DshRemoteMux.muxEnvelope(payload))
        if (productReady) flushFrames()
    }

    private fun handleControlItem(myGeneration: Long, value: JSONObject) {
        when (value.optString("type")) {
            "baseline" -> {
                val baseline = value.optJSONObject("value") ?: return
                emitControlMap(myGeneration, baseline.optJSONObject("queues"), "session/queue", "items")
                emitControlMap(myGeneration, baseline.optJSONObject("jobs"), "session/jobs", "jobs")
            }
            else -> {
                val sessionId = value.optString("sessionId")
                val payload = DshRemoteMux.mapFollowItem(sessionId, value) ?: return
                emitMappedFrame(myGeneration, DshEventStream.MUX, payload)
            }
        }
    }

    private fun emitControlMap(myGeneration: Long, map: JSONObject?, type: String, field: String) {
        if (map == null) return
        for (sessionId in map.keySet()) {
            emitMappedFrame(
                myGeneration,
                DshEventStream.MUX,
                JSONObject().apply {
                    put("type", type)
                    put("sessionId", sessionId)
                    put(field, map.optJSONArray(sessionId) ?: JSONArray())
                },
            )
        }
    }

    private var handshakeWorkspace: JSONObject? = null
    private var handshakeSessions: JSONObject? = null
    private var handshakeWorkspaceError: DshRpcError? = null
    private var handshakeSessionError: DshRpcError? = null

    private fun completeHandshake(myGeneration: Long) {
        if (hostDescribed) return
        hostDescribed = true
        handshakeWorkspace = null
        handshakeSessions = null
        handshakeWorkspaceError = null
        handshakeSessionError = null
        publish(DshHostRuntimePhase.SYNCING, "正在同步远程会话")
        workspaceStreamId = nextStreamId(myGeneration)
        controlStreamId = nextStreamId(myGeneration)
        muxHandle?.send(DshRemoteMux.openMessage(workspaceStreamId, DshRemoteMux.WORKSPACE_FOLLOW))
        muxHandle?.send(DshRemoteMux.openMessage(controlStreamId, DshRemoteMux.SESSION_CONTROL))
        setTimeout(pagerId, WORKSPACE_BASELINE_TIMEOUT_MS) {
            if (myGeneration == generation && handshakeWorkspace == null) {
                finishWorkspaceBaseline(myGeneration, DshRemoteMux.emptyWorkspaceBaseline(), null)
            }
        }
        directCall(myGeneration, DshHostProtocol.SESSION_LIST, JSONObject()) { sessionValue, errorValue ->
            handshakeSessions = sessionValue ?: JSONObject().put("items", JSONArray())
            handshakeSessionError = errorValue
            finishHandshake(myGeneration)
        }
    }

    private fun finishWorkspaceBaseline(myGeneration: Long, value: JSONObject, error: DshRpcError?) {
        if (handshakeWorkspace != null || myGeneration != generation) return
        handshakeWorkspace = value
        handshakeWorkspaceError = error
        onWorkspaceBaseline(value)
        finishHandshake(myGeneration)
    }

    private fun finishHandshake(myGeneration: Long) {
        if (myGeneration != generation || stopped) return
        val workspace = handshakeWorkspace ?: return
        val sessions = handshakeSessions ?: return
        if (handshakeSessionError != null) {
            invalidateGeneration(myGeneration, handshakeSessionError?.message ?: "session.list 失败")
            return
        }
        if (handshakeWorkspaceError != null) onWorkspaceBaseline(workspace)
        onSessionBaseline(sessions)
        productReady = true
        starting = false
        flushFrames()
        publish(DshHostRuntimePhase.READY, "DSH 已就绪")
        val pending = queued.toList()
        queued.clear()
        pending.filter { it.generation == myGeneration }.forEach(::dispatch)
    }

    private fun flushFrames() {
        if (!productReady) return
        val frames = bufferedFrames.toList()
        bufferedFrames.clear()
        frames.forEach(onFrame)
    }

    private fun invalidateGeneration(myGeneration: Long, message: String) {
        if (stopped || myGeneration != generation) return
        generation += 1
        val reconnectGeneration = generation
        muxHandle?.close()
        muxHandle = null
        muxOpen = false
        hostOpen = false
        hostDescribed = false
        productReady = false
        starting = false
        eventsStreamId = ""
        eventsClientId = ""
        workspaceStreamId = ""
        controlStreamId = ""
        handshakeWorkspace = null
        handshakeSessions = null
        failAllSessionFollows(message)
        bufferedFrames.clear()
        val cancelled = queued.filter { it.generation == myGeneration }
        queued.removeAll { it.generation == myGeneration }
        cancelled.forEach { request ->
            request.callback(null, DshRpcError("generation-cancelled", message), request.rpcId)
        }
        publish(DshHostRuntimePhase.RECONNECTING, message)
        setTimeout(pagerId, RECONNECT_DELAY_MS) {
            if (!stopped && generation == reconnectGeneration) start()
        }
    }

    private fun dispatch(request: QueuedRpc) {
        if (request.generation != generation || stopped) return
        val sessionId = request.payload.optString("sessionId")
        if (sessionId.isNotEmpty() && request.method in FOLLOWED_SESSION_METHODS) {
            ensureSessionFollow(request.generation, sessionId)
        }
        if (request.method == DshHostProtocol.SESSION_HISTORY) {
            awaitSessionSnapshot(sessionId) { events, error ->
                if (request.generation != generation || stopped) {
                    request.callback(null, DshRpcError("generation-cancelled", "请求所属连接世代已失效"), request.rpcId)
                    return@awaitSessionSnapshot
                }
                if (error != null) {
                    request.callback(null, DshRpcError("session-follow", error), request.rpcId)
                    return@awaitSessionSnapshot
                }
                request.callback(JSONObject().put("events", events ?: JSONArray()), null, request.rpcId)
            }
            return
        }
        val endpoint = DshRemoteMux.httpEndpoint(request.method)
        val payload = DshRemoteMux.httpPayload(request.method, request.payload, request.rpcId)
        val body = JSONObject().apply {
            put("type", "client-request")
            put("rpcId", request.rpcId)
            put("method", endpoint)
            put("payload", payload)
        }
        val headers = JSONObject().apply {
            put("Content-Type", "application/json")
            if (connection.token.isNotEmpty()) put("Authorization", "Bearer ${connection.token}")
        }
        network.httpRequest(
            "${connection.baseUrl.trimEnd('/')}${DshHostProtocol.API_PREFIX}/$endpoint",
            true, body, headers, null, REQUEST_TIMEOUT_SECONDS,
        ) { data, success, errorMsg, response ->
            if (request.generation != generation || stopped) {
                request.callback(null, DshRpcError("generation-cancelled", "请求所属连接世代已失效"), request.rpcId)
                return@httpRequest
            }
            if (!success) {
                request.callback(null, DshRpcError(
                    "transport-${response.statusCode ?: 0}",
                    "${request.method} failed (${response.statusCode ?: 0}): $errorMsg",
                ), request.rpcId)
                return@httpRequest
            }
            val result = data.optJSONObject("result")
            if (result == null) {
                request.callback(null, DshRpcError("bad-response", "${request.method} 返回了非法 RPC 信封"), request.rpcId)
                return@httpRequest
            }
            if (!result.optBoolean("ok")) {
                val error = result.optJSONObject("error")
                request.callback(null, DshRpcError(
                    error?.optString("code").orEmpty().ifEmpty { "internal" },
                    error?.optString("message").orEmpty().ifEmpty { "${request.method} 失败" },
                    error?.optJSONObject("details")?.toString() ?: "{}",
                ), request.rpcId)
                return@httpRequest
            }
            request.callback(
                DshRemoteMux.adaptHttpValue(request.method, result.optJSONObject("value"), result.optJSONArray("value")),
                null,
                request.rpcId,
            )
        }
    }

    private fun ensureSessionFollow(myGeneration: Long, sessionId: String) {
        if (sessionId.isEmpty() || sessionFollows.containsKey(sessionId)) return
        val streamId = nextStreamId(myGeneration)
        sessionFollows[sessionId] = streamId
        muxHandle?.send(DshRemoteMux.openMessage(streamId, DshRemoteMux.SESSION_FOLLOW, DshRemoteMux.sessionFollowArgs(sessionId)))
    }

    private fun awaitSessionSnapshot(sessionId: String, callback: (JSONArray?, String?) -> Unit) {
        val snapshot = sessionSnapshots[sessionId]
        if (snapshot != null) {
            callback(DshRemoteMux.snapshotRecords(snapshot), null)
            return
        }
        sessionFollowWaiters.getOrPut(sessionId) { mutableListOf() }.add(callback)
        ensureSessionFollow(generation, sessionId)
    }

    private fun failSessionFollow(streamId: String, message: String) {
        val sessionId = sessionFollows.entries.firstOrNull { it.value == streamId }?.key ?: return
        sessionFollows.remove(sessionId)
        val waiters = sessionFollowWaiters.remove(sessionId).orEmpty()
        waiters.forEach { it(null, message) }
    }

    private fun failAllSessionFollows(message: String) {
        sessionFollowWaiters.values.flatten().forEach { it(null, message) }
        sessionFollowWaiters.clear()
        sessionFollows.clear()
        sessionSnapshots.clear()
    }

    private fun nextStreamId(myGeneration: Long): String = "dsh-s${myGeneration}-${++rpcSequence}"

    private fun postRpc(
        myGeneration: Long,
        path: String,
        method: String,
        payload: JSONObject,
        rpcId: String,
        typert: Boolean,
        callback: (JSONObject?, DshRpcError?, String) -> Unit,
        timeoutSeconds: Int = REQUEST_TIMEOUT_SECONDS,
    ) {
        val body = if (typert) {
            JSONObject().apply { put("args", payload) }
        } else {
            JSONObject().apply {
                put("type", "client-request")
                put("rpcId", rpcId)
                put("method", method)
                put("payload", payload)
            }
        }
        val headers = JSONObject().apply {
            put("Content-Type", "application/json")
            if (connection.token.isNotEmpty()) put("Authorization", "Bearer ${connection.token}")
        }
        network.httpRequest(
            "${connection.baseUrl.trimEnd('/')}$path",
            true, body, headers, null, timeoutSeconds,
        ) { data, success, errorMsg, response ->
            if (myGeneration != generation || stopped) {
                callback(null, DshRpcError("generation-cancelled", "请求所属连接世代已失效"), rpcId)
                return@httpRequest
            }
            if (!success) {
                callback(
                    null,
                    DshRpcError(
                        "transport-${response.statusCode ?: 0}",
                        "$method failed (${response.statusCode ?: 0}): $errorMsg",
                    ),
                    rpcId,
                )
                return@httpRequest
            }
            val (value, error) = parseHostRpcBody(data, method, allowBareValue = typert)
            callback(value, error, rpcId)
        }
    }

    private fun directCall(myGeneration: Long, method: String, payload: JSONObject, callback: (JSONObject?, DshRpcError?) -> Unit) {
        if (myGeneration != generation || stopped) return
        dispatch(QueuedRpc(myGeneration, method, payload, nextRpcId(myGeneration)) { value, error, _ -> callback(value, error) })
    }

    private fun webSocketUrl(path: String): String {
        val base = connection.baseUrl.trimEnd('/')
        val wsBase = when {
            base.startsWith("https://") -> "wss://${base.removePrefix("https://")}"
            base.startsWith("http://") -> "ws://${base.removePrefix("http://")}"
            else -> base
        }
        return "$wsBase$path"
    }

    private fun nextRpcId(myGeneration: Long): String = "dsh-g${myGeneration}-${++rpcSequence}"

    private fun publish(phase: DshHostRuntimePhase, message: String) {
        onState(DshHostRuntimeState(phase, generation, muxOpen, hostOpen, message))
    }

    private companion object {
        const val REQUEST_TIMEOUT_SECONDS = 30
        const val RECONNECT_DELAY_MS = 1_000
        const val WORKSPACE_BASELINE_TIMEOUT_MS = 3_000
        val FOLLOWED_SESSION_METHODS = setOf(
            DshHostProtocol.SESSION_HISTORY,
            DshHostProtocol.SESSION_PROMPT,
            DshHostProtocol.SESSION_CANCEL,
            DshHostProtocol.SESSION_UPDATE_QUEUE,
            DshHostProtocol.SESSION_ATTACHMENT,
            DshHostProtocol.SKILL_LIST,
        )
    }
}

/** API facade used by the current Kuikly page while the raw timeline evolves. */
internal class DshRemoteHostRepository(
    network: NetworkModule,
    webSocket: DshWebSocketModule,
    private val connection: DshHostConnection,
    pagerId: String,
    onState: (DshHostRuntimeState) -> Unit = {},
    onQueueSnapshot: (String) -> Unit = {},
    onJobsSnapshot: (String) -> Unit = {},
    onSessionStatus: (String, Boolean) -> Unit = { _, _ -> },
    onProjection: (String, String, String, Int) -> Unit = { _, _, _, _ -> },
    onSessionEvent: (String, DshRawSessionEvent) -> Unit = { _, _ -> },
    onRemoteEvent: (String) -> Unit = {},
    onArchivedSessionsChanged: () -> Unit = {},
    onPendingInteraction: (String) -> Unit = {},
) : DshRepository {
    internal val store = DshHostStore()
    private val onQueueSnapshotHandler = onQueueSnapshot
    private val onJobsSnapshotHandler = onJobsSnapshot
    private val onSessionStatusHandler = onSessionStatus
    private val onProjectionHandler = onProjection
    private val onSessionEventHandler = onSessionEvent
    private val onRemoteEventHandler = onRemoteEvent
    private val onArchivedSessionsChangedHandler = onArchivedSessionsChanged
    private val onPendingInteractionHandler = onPendingInteraction
    private var mobilePluginAdmin: Boolean? = null
    private var mobilePluginWritable: Boolean = false
    private val runtime = DshHostConnectionRuntime(
        network = network,
        webSocket = webSocket,
        connection = connection,
        pagerId = pagerId,
        onFrame = ::handleFrame,
        onState = onState,
        onWorkspaceBaseline = ::applyWorkspaceBaseline,
        onSessionBaseline = { value -> store.replaceSessions(parseSessions(value)) },
        onQueueSnapshot = onQueueSnapshot,
        onJobsSnapshot = onJobsSnapshot,
        onSessionStatus = onSessionStatus,
        onProjection = onProjectionHandler,
        onSessionEvent = onSessionEventHandler,
        onRemoteEvent = onRemoteEvent,
    )
    private val activeStreams = mutableMapOf<String, ActiveStream>()

    private data class ActiveStream(
        val sessionId: String,
        val promptRpcId: String,
        val onDelta: (String, Boolean) -> Unit,
        val onComplete: (String) -> Unit,
        val onError: (String) -> Unit,
        var observed: Boolean = true,
        val accumulated: StringBuilder = StringBuilder(),
        var finalMessage: String = "",
        var failure: String = "",
    )

    fun currentConnectionState(): DshHostRuntimeState = runtime.currentState()
    fun isProductReady(): Boolean = runtime.currentState().phase == DshHostRuntimePhase.READY
    fun stop() = runtime.stop()

    fun respondApproval(
        rpcId: String,
        sessionId: String,
        approvalId: String,
        outcome: String,
        callback: (Boolean, String) -> Unit,
    ) {
        if (outcome != "allowed-once" && outcome != "rejected") {
            callback(false, "非法审批结果")
            return
        }
        runtime.respond(rpcId, JSONObject().apply {
            put("sessionId", sessionId)
            put("approvalId", approvalId)
            put("outcome", outcome)
        }, callback)
    }

    fun respondQuestion(
        rpcId: String,
        sessionId: String,
        answer: JSONObject,
        callback: (Boolean, String) -> Unit,
    ) {
        DshStreamLog.question(
            "repo.respondQuestion rpcId=$rpcId session=$sessionId answer='${DshStreamLog.preview(answer.toString(), 400)}'",
        )
        runtime.respond(rpcId, JSONObject().apply {
            put("sessionId", sessionId)
            put("answer", answer)
        }, callback)
    }

    fun clearPending(rpcId: String) {
        DshStreamLog.question("repo.clearPending rpcId=$rpcId")
        store.removePending(rpcId)
    }

    override fun loadCredentialSetup(onSuccess: (DshCredentialSetup) -> Unit, onError: (String) -> Unit) {
        call(DshHostProtocol.LLM_PROVIDERS, JSONObject()) { providersValue, providersError ->
            if (providersError != null || providersValue == null) {
                onError(providersError?.message ?: "llm.providers 返回为空")
                return@call
            }
            val providers = providersValue.optJSONArray("providers") ?: JSONArray()
            val active = (0 until providers.length()).any { index ->
                val provider = providers.optJSONObject(index) ?: return@any false
                provider.optString("provider") == DEEPSEEK_PROVIDER &&
                    provider.optString("settingsNs") == DEEPSEEK_SETTINGS_NS && provider.optBoolean("active")
            }
            if (!active) {
                onSuccess(DshCredentialSetup(false, false, false))
                return@call
            }
            call(DshHostProtocol.SETTINGS_DESCRIBE, JSONObject()) { settingsValue, settingsError ->
                if (settingsError != null || settingsValue == null) {
                    onError(settingsError?.message ?: "settings.describe 返回为空")
                    return@call
                }
                var credentialRef = DEEPSEEK_CREDENTIAL_REF
                var namespaceFound = false
                val namespaces = settingsValue.optJSONArray("namespaces") ?: JSONArray()
                for (index in 0 until namespaces.length()) {
                    val namespace = namespaces.optJSONObject(index) ?: continue
                    if (namespace.optString("ns") != DEEPSEEK_SETTINGS_NS) continue
                    namespaceFound = true
                    credentialRef = namespace.optJSONObject("value")?.optString("apiKeyEnv")
                        ?.takeIf { it.isNotEmpty() } ?: credentialRef
                    break
                }
                call(DshHostProtocol.CREDENTIALS_DESCRIBE, JSONObject().apply {
                    put("refs", JSONArray().apply { put(credentialRef) })
                }) { credentialsValue, credentialsError ->
                    if (credentialsError != null || credentialsValue == null) {
                        onError(credentialsError?.message ?: "credentials.describe 返回为空")
                        return@call
                    }
                    val credential = credentialsValue.optJSONObject("credentials")?.optJSONObject(credentialRef)
                    onSuccess(DshCredentialSetup(
                        true,
                        credential?.optBoolean("configured") == true,
                        settingsValue.optBoolean("writable") && namespaceFound && credential?.optBoolean("writable") == true,
                        credentialRef,
                    ))
                }
            }
        }
    }

    override fun saveDeepSeekApiKey(apiKey: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        call(DshHostProtocol.CREDENTIALS_SET, JSONObject().apply {
            put("ref", DEEPSEEK_CREDENTIAL_REF)
            put("value", apiKey)
        }) { _, error -> if (error == null) onSuccess() else onError(error.message) }
    }

    override fun loadModels(sessionId: String, onSuccess: (DshSessionModels) -> Unit, onError: (String) -> Unit) {
        call(DshHostProtocol.SESSION_MODELS, JSONObject().apply { put("sessionId", sessionId) }) { value, error ->
            if (error != null || value == null) {
                onError(error?.message ?: "session.models 返回为空")
                return@call
            }
            val current = value.optJSONObject("current") ?: JSONObject()
            val currentProvider = current.optString("provider")
            val currentModel = current.optString("model")
            val currentEffort = current.optString("reasoningEffort").takeIf { it.isNotEmpty() }
            val options = mutableListOf<DshModelOption>()
            val groups = value.optJSONArray("groups") ?: JSONArray()
            for (groupIndex in 0 until groups.length()) {
                val group = groups.optJSONObject(groupIndex) ?: continue
                val provider = group.optString("id")
                val providerName = group.optString("name").ifEmpty { provider }
                val models = group.optJSONArray("models") ?: JSONArray()
                for (modelIndex in 0 until models.length()) {
                    val model = models.optJSONObject(modelIndex) ?: continue
                    val id = model.optString("id")
                    if (provider.isEmpty() || id.isEmpty()) continue
                    val effort = model.optJSONObject("reasoning")?.optString("defaultEffort")?.takeIf { it.isNotEmpty() }
                    options += DshModelOption(
                        provider, providerName, id, model.optString("name").ifEmpty { id }, model.optString("description"),
                        if (provider == currentProvider && id == currentModel) currentEffort ?: effort else effort,
                        provider == currentProvider && id == currentModel,
                    )
                }
            }
            val selected = options.firstOrNull { it.selected } ?: DshModelOption(
                currentProvider, currentProvider, currentModel, currentModel.ifEmpty { "选择模型" }, reasoningEffort = currentEffort, selected = true,
            )
            onSuccess(DshSessionModels(selected, options, value.optBoolean("routable")))
        }
    }

    override fun selectModel(sessionId: String, option: DshModelOption, onSuccess: (DshModelOption) -> Unit, onError: (String) -> Unit) {
        call(DshHostProtocol.SESSION_SELECT_MODEL, JSONObject().apply {
            put("sessionId", sessionId); put("provider", option.provider); put("model", option.model)
            option.reasoningEffort?.let { put("reasoningEffort", it) }
        }) { value, error ->
            if (error != null || value == null) {
                onError(error?.message ?: "session.selectModel 返回为空")
                return@call
            }
            val selected = value.optJSONObject("selected") ?: JSONObject()
            onSuccess(option.copy(
                provider = selected.optString("provider").ifEmpty { option.provider },
                model = selected.optString("model").ifEmpty { option.model },
                reasoningEffort = selected.optString("reasoningEffort").takeIf { it.isNotEmpty() }, selected = true,
            ))
        }
    }

    override fun loadSessions(onSuccess: (List<DshSession>) -> Unit, onError: (String) -> Unit) {
        call(DshHostProtocol.SESSION_LIST, JSONObject()) { value, error ->
            if (error != null || value == null) {
                onError(error?.message ?: "session.list 返回为空")
                return@call
            }
            val sessions = parseSessions(value)
            store.replaceSessions(sessions)
            onSuccess(store.sessions.values.toList())
        }
    }

    private fun parseSessions(value: JSONObject): List<DshSession> {
        val items = value.optJSONArray("items") ?: JSONArray()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val id = item.optString("sessionId")
                if (id.isEmpty()) continue
                val projections = item.optJSONObject("projections")?.optJSONObject("values")
                val updatedAt = dshParseUpdatedAt(item.optLong("updatedAt"), item.optString("updatedAt"))
                projections?.optJSONObject("imageLimits")?.let { limits ->
                    parseDshImageLimits(limits)?.let { store.applyImageLimits(it) }
                }
                add(DshSession(
                    id = id,
                    title = projections?.optString("title")?.takeIf { it.isNotEmpty() } ?: "尚无标题",
                    workspace = "Host",
                    updatedLabel = updatedAt.takeIf { it > 0 }?.toString().orEmpty(),
                    running = item.optBoolean("running"), blank = item.optBoolean("blank"), cwd = item.optString("cwd"),
                    parentSessionId = item.optString("parentSessionId").takeIf { it.isNotEmpty() },
                    origin = item.optString("origin").takeIf { it.isNotEmpty() },
                    agentPreset = item.optString("agentPreset").takeIf { it.isNotEmpty() },
                    updatedAt = updatedAt,
                ))
            }
        }
    }

    override fun createSession(workspaceId: String?, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        val payload = JSONObject()
        workspaceId?.takeIf { it.isNotEmpty() }?.let { payload.put("workspaceId", it) }
        call(DshHostProtocol.SESSION_CREATE, payload) { value, error ->
            if (error != null || value == null) {
                onError(error?.message ?: "session.create 返回为空")
                return@call
            }
            val id = value.optString("sessionId")
            if (id.isEmpty()) onError("session.create 未返回 sessionId") else onSuccess(id)
        }
    }

    override fun loadHistory(sessionId: String, onSuccess: (List<DshMessage>) -> Unit, onError: (String) -> Unit) {
        call(DshHostProtocol.SESSION_HISTORY, JSONObject().apply {
            put("sessionId", sessionId)
            put("maxMessages", HISTORY_PAGE_MESSAGES)
        }) { value, error ->
            if (error != null || value == null) {
                onError(error?.message ?: "session.history 返回为空")
                return@call
            }
            onSuccess(parseHistory(value.optJSONArray("events") ?: JSONArray()))
        }
    }

    fun loadWebTimeline(
        sessionId: String,
        onSuccess: (List<DshWebTimelineItem>) -> Unit,
        onError: (String) -> Unit = {},
    ) {
        call(DshHostProtocol.SESSION_HISTORY, JSONObject().apply {
            put("sessionId", sessionId)
            put("maxMessages", HISTORY_PAGE_MESSAGES)
        }) { value, error ->
            if (error != null || value == null) {
                DshStreamLog.i("history.fail session=$sessionId error='${error?.message ?: "empty"}'")
                onError(error?.message ?: "session.history 返回为空")
                return@call
            }
            onSuccess(DshWebTimelineParser.parseWebTimeline(value.optJSONArray("events") ?: JSONArray()))
        }
    }

    fun loadSkills(sessionId: String, onSuccess: (List<DshSkill>) -> Unit, onError: (String) -> Unit = {}) {
        call(DshHostProtocol.SKILL_LIST, JSONObject().apply { put("sessionId", sessionId) }) { value, error ->
            if (error != null || value == null) {
                onError(error?.message ?: "skill.list 返回为空")
                return@call
            }
            val skills = buildList {
                val items = value.optJSONArray("skills") ?: JSONArray()
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val name = item.optString("name")
                    if (name.isEmpty()) continue
                    add(DshSkill(name, item.optString("description"), item.optString("whenToUse"), item.optBoolean("modelInvocable", true)))
                }
            }
            onSuccess(skills)
        }
    }

    fun goalEdit(sessionId: String, goal: DshGoalSnapshot, objective: String, callback: (DshRpcError?) -> Unit) =
        goalMutation(
            DshHostProtocol.GOAL_EDIT,
            sessionId,
            goal,
            enrich = { it.put("objective", objective) },
            callback = callback,
        )

    fun goalPause(sessionId: String, goal: DshGoalSnapshot, callback: (DshRpcError?) -> Unit) =
        goalMutation(DshHostProtocol.GOAL_PAUSE, sessionId, goal, callback = callback)

    fun goalResume(sessionId: String, goal: DshGoalSnapshot, callback: (DshRpcError?) -> Unit) =
        goalMutation(DshHostProtocol.GOAL_RESUME, sessionId, goal, callback = callback)

    fun goalClear(sessionId: String, goal: DshGoalSnapshot, callback: (DshRpcError?) -> Unit) =
        goalMutation(DshHostProtocol.GOAL_CLEAR, sessionId, goal, callback = callback)

    private fun goalMutation(
        method: String,
        sessionId: String,
        goal: DshGoalSnapshot,
        enrich: (JSONObject) -> Unit = {},
        callback: (DshRpcError?) -> Unit,
    ) {
        call(method, JSONObject().apply {
            put("sessionId", sessionId)
            put("ref", JSONObject().apply { put("id", goal.id); put("revision", goal.revision) })
            enrich(this)
        }) { _, error -> callback(error) }
    }

    fun loadAttachment(
        sessionId: String,
        attachmentId: String,
        callback: (String?, String?) -> Unit,
    ) {
        call(DshHostProtocol.SESSION_ATTACHMENT, JSONObject().apply {
            put("sessionId", sessionId)
            put("attachmentId", attachmentId)
        }) { value, error ->
            val data = value?.optString("data").orEmpty()
            val mediaType = value?.optJSONObject("attachment")?.optString("mediaType")?.takeIf { it.isNotEmpty() }
                ?: "image/png"
            if (error != null || data.isEmpty()) callback(null, error?.message ?: "attachment 返回为空")
            else callback("data:$mediaType;base64,$data", null)
        }
    }

    fun queue(sessionId: String): List<DshQueueItem> {
        val raw = store.queueSnapshots[sessionId] ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val message = item.optJSONObject("message") ?: JSONObject()
                val text = textFromBlocks(message.optJSONArray("content"))
                add(DshQueueItem(
                    id = item.optString("id"),
                    placement = item.optString("placement"),
                    preview = text.lineSequence().firstOrNull().orEmpty(),
                    text = text.takeIf { it.isNotEmpty() },
                ))
            }
        }.filter { it.id.isNotEmpty() && it.placement == "queued" }
    }

    fun pendingInteractions(sessionId: String): Pair<DshPendingApproval?, DshPendingQuestion?> {
        var approval: DshPendingApproval? = null
        var question: DshPendingQuestion? = null
        store.pendingInteractions.forEach { (rpcId, raw) ->
            val payload = runCatching { JSONObject(raw) }.getOrNull() ?: return@forEach
            if (payload.optString("sessionId") != sessionId) return@forEach
            when (payload.optString("type")) {
                "approval/requested" -> approval = DshPendingApproval(
                    rpcId = rpcId,
                    sessionId = sessionId,
                    approvalId = payload.optString("approvalId"),
                    toolName = payload.optString("toolName"),
                    callId = payload.optString("callId").takeIf { it.isNotEmpty() },
                    reason = payload.optString("reason").takeIf { it.isNotEmpty() },
                    command = approvalCommand(sessionId, payload.optString("callId")),
                )
                "question/requested" -> {
                    val questions = payload.optJSONArray("questions") ?: JSONArray()
                    question = DshPendingQuestion(
                        rpcId = rpcId,
                        sessionId = sessionId,
                        questions = (0 until questions.length()).mapNotNull { index ->
                            val item = questions.optJSONObject(index) ?: return@mapNotNull null
                            val options = item.optJSONArray("options") ?: JSONArray()
                            DshPendingQuestionItem(
                                id = item.optString("id"),
                                question = item.optString("question"),
                                header = item.optString("header"),
                                detail = item.optString("detail"),
                                options = (0 until options.length()).mapNotNull { optionIndex ->
                                    val option = options.optJSONObject(optionIndex) ?: return@mapNotNull null
                                    DshPendingQuestionOption(
                                        label = option.optString("label"),
                                        description = option.optString("description"),
                                    )
                                },
                                multiSelect = item.optBoolean("multiSelect") || item.optBoolean("multi_select"),
                            )
                        },
                    )
                }
            }
        }
        return approval to question
    }

    fun jobs(sessionId: String): List<DshJobItem> {
        val raw = store.jobSnapshots[sessionId] ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id")
            if (id.isEmpty()) return@mapNotNull null
            DshJobItem(
                id = id,
                kind = item.optString("kind"),
                label = item.optString("label"),
                status = item.optString("status"),
                detail = item.optString("detail"),
                startedAt = item.optLong("startedAt"),
                finishedAt = item.optString("finishedAt").takeIf { it.isNotEmpty() }?.toLongOrNull(),
            )
        }
    }

    fun workspaceGroups(): List<DshWorkspaceGroup> {
        val raw = store.workspaceBaseline
        val workspaces = runCatching { JSONArray(raw) }.getOrNull() ?: JSONArray()
        val archived = store.archivedSessionIds
        val sessionById = store.sessions.values
            .filterNot { it.blank || archived.contains(it.id) }
            .associateBy { it.id }
        val grouped = mutableSetOf<String>()
        val groups = (0 until workspaces.length()).mapNotNull { index ->
            val workspace = workspaces.optJSONObject(index) ?: return@mapNotNull null
            val workspaceId = workspace.optString("workspaceId")
            if (workspaceId.isEmpty()) return@mapNotNull null
            val sessionIds = workspace.optJSONArray("sessionIds") ?: JSONArray()
            val sessions = (0 until sessionIds.length()).mapNotNull { sessionIndex ->
                val sessionId = sessionIds.optString(sessionIndex)
                sessionId?.takeIf { it.isNotEmpty() }?.let(grouped::add)
                sessionById[sessionId]
            }
            DshWorkspaceGroup(
                workspaceId = workspaceId,
                title = workspace.optString("title").ifEmpty { workspaceId },
                path = workspace.optString("path"),
                sessions = sessions,
            )
        }
        val ungrouped = sessionById.values.filterNot { grouped.contains(it.id) }
        return if (ungrouped.isEmpty()) groups else groups + DshWorkspaceGroup(
            workspaceId = "",
            title = "未归类",
            path = "",
            sessions = ungrouped,
        )
    }

    fun archivedSessions(): List<DshSession> =
        store.archivedSessionIds.mapNotNull(store.sessions::get).filterNot { it.blank }

    fun workspaceIdForSession(sessionId: String): String? {
        val workspaces = runCatching { JSONArray(store.workspaceBaseline) }.getOrNull() ?: JSONArray()
        for (index in 0 until workspaces.length()) {
            val workspace = workspaces.optJSONObject(index) ?: continue
            val sessionIds = workspace.optJSONArray("sessionIds") ?: continue
            for (sessionIndex in 0 until sessionIds.length()) {
                if (sessionIds.optString(sessionIndex) == sessionId) {
                    return workspace.optString("workspaceId").takeIf { it.isNotEmpty() }
                }
            }
        }
        return null
    }

    fun blankSessionInWorkspace(workspaceId: String?): DshSession? {
        if (workspaceId == null) {
            return store.sessions.values.firstOrNull {
                it.blank && it.cwd.isEmpty() && !store.archivedSessionIds.contains(it.id)
            }
        }
        val workspaces = runCatching { JSONArray(store.workspaceBaseline) }.getOrNull() ?: JSONArray()
        for (index in 0 until workspaces.length()) {
            val workspace = workspaces.optJSONObject(index) ?: continue
            if (workspace.optString("workspaceId") != workspaceId) continue
            val sessionIds = workspace.optJSONArray("sessionIds") ?: continue
            for (sessionIndex in 0 until sessionIds.length()) {
                val sessionId = sessionIds.optString(sessionIndex)
                val session = store.sessions[sessionId] ?: continue
                if (session.blank && !store.archivedSessionIds.contains(sessionId)) return session
            }
        }
        return null
    }

    private fun approvalCommand(sessionId: String, callId: String): String? {
        if (callId.isEmpty()) return null
        val events = store.sessionEvents[sessionId] ?: return null
        events.forEach { event ->
            if (event.type != "tool/call") return@forEach
            val payload = runCatching { JSONObject(event.raw) }.getOrNull() ?: return@forEach
            val data = payload.optJSONObject("data") ?: return@forEach
            if (data.optString("callId") != callId) return@forEach
            val arguments = data.opt("arguments") ?: return@forEach
            return when (arguments) {
                is String -> arguments
                else -> {
                    val obj = arguments as? JSONObject
                    obj?.optString("command")?.takeIf { it.isNotEmpty() } ?: arguments.toString()
                }
            }
        }
        return null
    }

    fun updateQueue(
        sessionId: String,
        itemId: String,
        action: JSONObject,
        callback: (JSONObject?, DshRpcError?) -> Unit,
    ) {
        call(DshHostProtocol.SESSION_UPDATE_QUEUE, JSONObject().apply {
            put("sessionId", sessionId)
            put("itemId", itemId)
            put("action", action)
        }) { value, error -> callback(value, error) }
    }

    fun renameSession(
        sessionId: String,
        title: String,
        callback: (JSONObject?, DshRpcError?) -> Unit,
    ) {
        call(DshHostProtocol.SESSION_RENAME, JSONObject().apply {
            put("sessionId", sessionId)
            put("title", title)
        }) { value, error ->
            if (error != null) {
                callback(null, error)
                return@call
            }
            val normalizedTitle = value?.optString("title").orEmpty()
            val seq = value?.optInt("seq", -1) ?: -1
            if (value == null || normalizedTitle.isEmpty() || seq < 0) {
                callback(null, DshRpcError("bad-response", "session.rename 返回了非法标题结果"))
                return@call
            }
            store.applyProjection(sessionId, "title", normalizedTitle, seq)
            callback(value, null)
        }
    }

    fun forkSession(
        sessionId: String,
        atSeq: Int?,
        callback: (JSONObject?, DshRpcError?) -> Unit,
    ) {
        val payload = JSONObject().apply { put("sessionId", sessionId) }
        atSeq?.let { payload.put("atSeq", it) }
        call(DshHostProtocol.SESSION_FORK, payload) { value, error -> callback(value, error) }
    }

    fun archiveSession(
        sessionId: String,
        callback: (JSONObject?, DshRpcError?) -> Unit,
    ) {
        call(DshHostProtocol.WORKSPACE_ARCHIVE_SESSION, JSONObject().apply {
            put("sessionId", sessionId)
        }) { value, error ->
            if (error != null) {
                callback(null, error)
                return@call
            }
            val archived = value?.optJSONArray("archivedSessionIds")
            if (value == null || archived == null) {
                callback(null, DshRpcError("bad-response", "workspace.archiveSession 返回了非法归档集合"))
                return@call
            }
            val archivedIds = parseArchivedSessionIds(archived)
            store.replaceWorkspaceBaseline(store.workspaceBaseline, archivedIds)
            callback(value, null)
        }
    }

    fun unarchiveSession(
        sessionId: String,
        callback: (JSONObject?, DshRpcError?) -> Unit,
    ) {
        call(DshHostProtocol.WORKSPACE_UNARCHIVE_SESSION, JSONObject().apply {
            put("sessionId", sessionId)
        }) { value, error ->
            if (error != null) {
                callback(null, error)
                return@call
            }
            val archived = value?.optJSONArray("archivedSessionIds")
            if (value == null || archived == null) {
                callback(null, DshRpcError("bad-response", "workspace.unarchiveSession 返回了非法归档集合"))
                return@call
            }
            store.replaceWorkspaceBaseline(store.workspaceBaseline, parseArchivedSessionIds(archived))
            callback(value, null)
        }
    }

    fun deleteSession(
        sessionId: String,
        callback: (JSONObject?, DshRpcError?) -> Unit,
    ) {
        call(DshHostProtocol.WORKSPACE_DELETE_SESSION, JSONObject().apply {
            put("sessionId", sessionId)
        }) { value, error ->
            if (error != null) {
                callback(null, error)
                return@call
            }
            val archived = value?.optJSONArray("archivedSessionIds")
            store.applySessionRemoved(sessionId)
            if (archived != null) {
                store.replaceWorkspaceBaseline(store.workspaceBaseline, parseArchivedSessionIds(archived))
            }
            callback(value ?: JSONObject(), null)
        }
    }

    fun sessionExportUrl(sessionId: String, includeDescendants: Boolean = true): String {
        val encodedSessionId = dshEncodeQueryComponent(sessionId)
        return "${connection.baseUrl.trimEnd('/')}${DshHostProtocol.SESSION_EXPORT_PATH}" +
            "?sessionId=$encodedSessionId" +
            "&includeDescendants=${if (includeDescendants) "true" else "false"}"
    }

    fun listDirectory(
        path: String?,
        callback: (DshDirectoryListing?, DshRpcError?) -> Unit,
    ) {
        val payload = JSONObject()
        path?.takeIf { it.isNotEmpty() }?.let { payload.put("path", it) }
        call(DshHostProtocol.HOST_LIST_DIRECTORY, payload) { value, error ->
            if (error != null || value == null) {
                callback(null, error ?: DshRpcError("internal", "host.listDirectory failed"))
                return@call
            }
            callback(parseDirectoryListing(value), null)
        }
    }

    fun createDirectory(
        path: String,
        name: String,
        callback: (String?, DshRpcError?) -> Unit,
    ) {
        call(DshHostProtocol.HOST_CREATE_DIRECTORY, JSONObject().apply {
            put("path", path)
            put("name", name)
        }) { value, error ->
            if (error != null || value == null) {
                callback(null, error ?: DshRpcError("internal", "host.createDirectory failed"))
                return@call
            }
            callback(value.optString("path").takeIf { it.isNotEmpty() }, null)
        }
    }

    fun createWorkspace(
        path: String,
        callback: (JSONObject?, DshRpcError?) -> Unit,
    ) {
        call(DshHostProtocol.WORKSPACE_CREATE, JSONObject().apply {
            put("path", path)
        }) { value, error -> callback(value, error) }
    }

    fun renameWorkspace(
        workspaceId: String,
        title: String,
        callback: (JSONObject?, DshRpcError?) -> Unit,
    ) {
        call(DshHostProtocol.WORKSPACE_RENAME, JSONObject().apply {
            put("workspaceId", workspaceId)
            put("title", title)
        }) { value, error -> callback(value, error) }
    }

    fun deleteWorkspace(
        workspaceId: String,
        callback: (JSONObject?, DshRpcError?) -> Unit,
    ) {
        call(DshHostProtocol.WORKSPACE_DELETE, JSONObject().apply {
            put("workspaceId", workspaceId)
        }) { value, error -> callback(value, error) }
    }

    fun moveWorkspaceBefore(
        workspaceId: String,
        beforeWorkspaceId: String?,
        callback: (JSONObject?, DshRpcError?) -> Unit,
    ) {
        val payload = JSONObject().apply {
            put("workspaceId", workspaceId)
            beforeWorkspaceId?.takeIf { it.isNotEmpty() }?.let { put("beforeWorkspaceId", it) }
        }
        call(DshHostProtocol.WORKSPACE_INSERT_BEFORE, payload) { value, error ->
            callback(value, error)
        }
    }

    private fun parseDirectoryListing(value: JSONObject): DshDirectoryListing {
        fun entries(array: JSONArray?): List<DshDirectoryEntry> = buildList {
            if (array == null) return@buildList
            for (index in 0 until array.length()) {
                val entry = array.optJSONObject(index) ?: continue
                add(DshDirectoryEntry(
                    name = entry.optString("name"),
                    path = entry.optString("path"),
                    hidden = entry.optBoolean("hidden"),
                ))
            }
        }
        return DshDirectoryListing(
            path = value.optString("path"),
            home = value.optString("home"),
            crumbs = entries(value.optJSONArray("crumbs")),
            entries = entries(value.optJSONArray("entries")),
            truncated = value.optBoolean("truncated"),
        )
    }

    fun streamReply(
        pagerId: String,
        sessionId: String,
        prompt: String,
        onDelta: (String) -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit,
        images: List<DshPromptImagePart> = emptyList(),
    ): DshStreamHandle = streamReply(pagerId, sessionId, prompt, { text, _ -> onDelta(text) }, onComplete, onError, images)

    override fun streamReply(
        pagerId: String,
        sessionId: String,
        prompt: String,
        onDelta: (String, Boolean) -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit,
        images: List<DshPromptImagePart>,
    ): DshStreamHandle {
        val timeoutSeconds = if (images.isEmpty()) 30 else DSH_IMAGE_PROMPT_TIMEOUT_SECONDS
        val call = runtime.call(
            DshHostProtocol.SESSION_PROMPT,
            JSONObject().apply {
                put("sessionId", sessionId)
                put("mode", "queue")
                put("content", dshPromptContent(prompt, images))
                put("clientTimeZone", "UTC")
            },
            timeoutSeconds,
        ) { value, error, rpcId ->
                if (error != null) {
                    if (dshIsTransportInterrupt(error.code, error.message)) {
                        DshStreamLog.i("prompt.hold-for-resync session=$sessionId rpcId=$rpcId code=${error.code}")
                        return@call
                    }
                    val label = dshAttachmentErrorLabel(error.code, error.message).ifEmpty { error.message }
                    activeStreams.remove(rpcId); onError(label); return@call
                }
                val command = value?.optJSONObject("command")
                if (command != null) {
                    activeStreams.remove(rpcId); onComplete(command.optString("text"))
                }
        }
        activeStreams[call.rpcId] = ActiveStream(sessionId, call.rpcId, onDelta, onComplete, onError)
        DshStreamLog.i(
            "prompt.start session=$sessionId rpcId=${call.rpcId} promptChars=${prompt.length} images=${images.size} prompt='${DshStreamLog.preview(prompt)}'",
        )
        return object : DshStreamHandle {
            private var cancelled = false
            override fun cancel() {
                if (cancelled) return
                cancelled = true
                activeStreams.remove(call.rpcId)
                call.cancel()
                runtime.call(DshHostProtocol.SESSION_CANCEL, JSONObject().apply { put("sessionId", sessionId) }) { _, _, _ -> }
            }
        }
    }

    fun adoptLiveStream(
        sessionId: String,
        onDelta: (String, Boolean) -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit,
    ): DshStreamHandle {
        detachLiveStreams(sessionId)
        val rpcId = "adopted-$sessionId"
        activeStreams[rpcId] = ActiveStream(sessionId, rpcId, onDelta, onComplete, onError)
        DshStreamLog.i("prompt.adopt-live session=$sessionId rpcId=$rpcId")
        return object : DshStreamHandle {
            private var cancelled = false
            override fun cancel() {
                if (cancelled) return
                cancelled = true
                activeStreams.remove(rpcId)
                runtime.call(DshHostProtocol.SESSION_CANCEL, JSONObject().apply { put("sessionId", sessionId) }) { _, _, _ -> }
            }
        }
    }

    fun detachLiveStreams(sessionId: String) {
        val removed = activeStreams.entries
            .filter { it.value.sessionId == sessionId }
            .map { it.key }
        removed.forEach(activeStreams::remove)
        if (removed.isNotEmpty()) {
            DshStreamLog.i("prompt.detach-live session=$sessionId count=${removed.size}")
        }
    }

    private fun call(method: String, payload: JSONObject, callback: (JSONObject?, DshRpcError?) -> Unit) =
        runtime.call(method, payload) { value, error, _ -> callback(value, error) }

    fun loadPluginInventory(onSuccess: (DshPluginSnapshot) -> Unit, onError: (String) -> Unit) {
        when (mobilePluginAdmin) {
            true -> loadAdminPluginInventory(onSuccess, onError, mobilePluginWritable)
            false -> loadOfficialPluginInventory(onSuccess, onError)
            null -> runtime.callPath(
                DshHostProtocol.MOBILE_PLUGIN_RPC_PATH,
                DshHostProtocol.MOBILE_CAPABILITIES,
                JSONObject(),
            ) { caps, capsError, _ ->
                val available = capsError == null && caps != null && capabilitiesAllow(caps, "plugin.inventory")
                if (available) {
                    mobilePluginAdmin = true
                    mobilePluginWritable = capabilitiesAllow(caps, "plugin.control")
                    loadAdminPluginInventory(onSuccess, onError, mobilePluginWritable)
                    return@callPath
                }
                if (shouldCacheAdminUnavailable(capsError)) mobilePluginAdmin = false
                loadOfficialPluginInventory(onSuccess, onError)
            }
        }
    }

    fun getPlugin(entryId: String, callback: (DshPluginEntry?, DshRpcError?) -> Unit) {
        if (mobilePluginAdmin != true) {
            callback(null, DshRpcError("unavailable", "当前 Host 没有配套管理插件"))
            return
        }
        runtime.callPath(
            DshHostProtocol.MOBILE_PLUGIN_RPC_PATH,
            DshHostProtocol.MOBILE_PLUGIN_GET,
            JSONObject().apply { put("entryId", entryId) },
        ) { value, error, _ ->
            callback(value?.let(::parsePluginEntry), error)
        }
    }

    fun controlPlugin(
        entryId: String,
        action: String,
        confirm: Boolean,
        callback: (DshPluginEntry?, DshRpcError?) -> Unit,
    ) {
        runtime.callPath(
            DshHostProtocol.MOBILE_PLUGIN_RPC_PATH,
            DshHostProtocol.MOBILE_PLUGIN_CONTROL,
            JSONObject().apply {
                put("entryId", entryId)
                put("action", action)
                put("confirm", confirm)
            },
        ) { value, error, _ ->
            if (error != null) {
                callback(null, error)
                return@callPath
            }
            val entry = value?.optJSONObject("entry")?.let(::parsePluginEntry)
            callback(entry, null)
        }
    }

    private fun loadAdminPluginInventory(
        onSuccess: (DshPluginSnapshot) -> Unit,
        onError: (String) -> Unit,
        writable: Boolean = true,
    ) {
        runtime.callPath(
            DshHostProtocol.MOBILE_PLUGIN_RPC_PATH,
            DshHostProtocol.MOBILE_PLUGIN_INVENTORY,
            JSONObject(),
        ) { value, error, _ ->
            if (error != null || value == null) {
                onError(pluginInventoryError(error?.message))
            } else {
                onSuccess(
                    parsePluginSnapshot(
                        value,
                        source = "admin",
                        writable = writable && value.optBoolean("writable", true),
                    ),
                )
            }
        }
    }

    private fun loadOfficialPluginInventory(onSuccess: (DshPluginSnapshot) -> Unit, onError: (String) -> Unit) {
        runtime.callTypert(DshHostProtocol.PLUGIN_INVENTORY_LIST, JSONObject()) { value, error, _ ->
            if (error == null && value != null) {
                onSuccess(parseOfficialPluginSnapshot(value))
                return@callTypert
            }
            call(DshHostProtocol.PLUGIN_INVENTORY_LIST, JSONObject()) { fallback, fallbackError ->
                if (fallbackError != null || fallback == null) {
                    onError(pluginInventoryError(fallbackError?.message ?: error?.message))
                } else {
                    onSuccess(parseOfficialPluginSnapshot(fallback))
                }
            }
        }
    }

    private fun shouldCacheAdminUnavailable(error: DshRpcError?): Boolean {
        if (error == null) return true
        if (error.code == "cancelled" || error.code == "generation-cancelled" || error.code.contains("not-ready")) {
            return false
        }
        val blob = "${error.code} ${error.message}"
        return blob.contains("404") || error.code == "bad-response" || blob.contains("forbidden")
    }

    private fun pluginInventoryError(raw: String?): String {
        val message = raw.orEmpty()
        return when {
            message.contains("not-ready") -> "还没连上 Host，请稍后再试"
            message.contains("transport-404") || message.contains("404") ->
                "Host 没有返回插件清单。确认电脑 DSH 已启动，并已安装官方插件清单或配套管理插件。"
            message.isBlank() -> "无法读取插件列表"
            else -> "无法读取插件列表：$message"
        }
    }

    private fun applyWorkspaceBaseline(value: JSONObject) {
        val archived = value.optJSONArray("archivedSessionIds")
        val archivedIds = archived?.let(::parseArchivedSessionIds).orEmpty()
        store.replaceWorkspaceBaseline(value.optJSONArray("items")?.toString() ?: "[]", archivedIds)
    }

    private fun handleFrame(frame: DshDownlinkFrame) {
        val envelope = runCatching { JSONObject(frame.raw) }.getOrNull()
        if (envelope == null) {
            DshStreamLog.i(
                "host.frame stream=${frame.stream.name.lowercase()} parse-error chars=${frame.raw.length} raw='${DshStreamLog.preview(frame.raw, 240)}'",
            )
            return
        }
        val payload = envelope.optJSONObject("payload")
        if (payload == null) {
            DshStreamLog.i(
                "host.frame stream=${frame.stream.name.lowercase()} no-payload envelopeType=${envelope.optString("type")} raw='${DshStreamLog.preview(frame.raw, 240)}'",
            )
            return
        }
        val frameType = payload.optString("type")
        val inboundEvent = payload.optJSONObject("event")
        DshStreamLog.i(
            "host.frame stream=${frame.stream.name.lowercase()} type=$frameType session=${payload.optString("sessionId")} event=${inboundEvent?.optString("type").orEmpty()} seq=${inboundEvent?.optInt("seq", -1) ?: -1} chars=${frame.raw.length} payload='${DshStreamLog.preview(payload.toString(), 400)}'",
        )
        if (frame.stream == DshEventStream.HOST) {
            handleHostFrame(payload)
            return
        }
        if (frame.stream != DshEventStream.MUX) return
        when (frameType) {
            "session/subscribed" -> {
                store.applySubscribed(payload.optString("sessionId"), payload.optInt("lastSeq", -1))
                return
            }
            "session/queue" -> {
                store.replaceQueue(payload.optString("sessionId"), payload.optJSONArray("items")?.toString() ?: "[]")
                onQueueSnapshotHandler(payload.optString("sessionId"))
                return
            }
            "session/jobs" -> {
                store.replaceJobs(payload.optString("sessionId"), payload.optJSONArray("jobs")?.toString() ?: "[]")
                onJobsSnapshotHandler(payload.optString("sessionId"))
                return
            }
            "session/projection" -> {
                val value = payload.optJSONObject("value")?.toString() ?: payload.optString("value")
                onProjectionHandler(payload.optString("sessionId"), payload.optString("key"), value, payload.optInt("seq", -1))
                return
            }
            "approval/requested", "question/requested" -> {
                val rpcId = pendingInteractionRpcId(envelope, payload)
                DshStreamLog.question(
                    "mux.requested type=$frameType rpcId=$rpcId session=${payload.optString("sessionId")} envelopeRpc=${envelope.optString("rpcId")} payloadRpc=${payload.optString("rpcId")}",
                )
                if (rpcId.isEmpty()) {
                    DshStreamLog.question(
                        "mux.requested-drop empty-rpcId type=$frameType raw='${DshStreamLog.preview(frame.raw, 240)}'",
                    )
                    return
                }
                store.putPending(rpcId, payload.toString())
                onPendingInteractionHandler(payload.optString("sessionId"))
                return
            }
            "approval/resolved", "question/resolved" -> {
                val rpcId = pendingInteractionRpcId(envelope, payload)
                    .ifEmpty { payload.optString("questionRpcId") }
                    .ifEmpty { payload.optString("approvalId") }
                DshStreamLog.question(
                    "mux.resolved type=$frameType rpcId=$rpcId session=${payload.optString("sessionId")} outcome=${payload.optString("outcome")}",
                )
                store.removePending(rpcId)
                onPendingInteractionHandler(payload.optString("sessionId"))
                return
            }
        }
        if (frameType != "session/event") return
        val sessionId = payload.optString("sessionId")
        val event = payload.optJSONObject("event") ?: return
        val type = event.optString("type")
        val seq = event.optInt("seq", -1)
        // Preserve the optional host-computed tool view. History entries and
        // live mux frames must feed the same remote tool model.
        val eventEnvelope = JSONObject().apply {
            put("event", event)
            payload.optJSONObject("view")?.let { put("view", it) }
        }
        store.applySessionEvent(sessionId, seq, type, eventEnvelope.toString())
        if (seq > -1) onSessionEventHandler(sessionId, DshRawSessionEvent(seq, type, eventEnvelope.toString()))
        val data = event.optJSONObject("data") ?: JSONObject()
        val source = sessionEventSource(data)
        val rpcId = source?.optString("rpcId").orEmpty()
        val active = resolveActiveStream(sessionId, type, rpcId)
        if (active == null) {
            DshStreamLog.i(
                "host.frame drop-no-active-stream session=$sessionId event=$type seq=$seq rpcId=$rpcId",
            )
            return
        }
        when (type) {
            "user/message" -> {
                val kind = source?.optString("kind").orEmpty()
                if (kind.isEmpty() || kind == "user") active.observed = true
            }
            "assistant/chunk" -> {
                val chunk = data.optJSONObject("chunk") ?: return
                val chunkType = chunk.optString("type")
                val text = chunk.optString("text").ifEmpty { chunk.optString("delta") }
                DshStreamLog.i(
                    "mux.chunk session=$sessionId rpcId=${active.promptRpcId} type=$chunkType deltaChars=${text.length} delta='${DshStreamLog.preview(text)}' acc=${active.accumulated.length}",
                )
                when (chunkType) {
                    "text-delta", "text_delta", "text" -> text.takeIf { it.isNotEmpty() }?.let {
                        active.observed = true
                        active.accumulated.append(it)
                        active.onDelta(it, false)
                    }
                    "reasoning-delta", "reasoning_delta" -> text.takeIf { it.isNotEmpty() }?.let {
                        active.observed = true
                        active.onDelta(it, true)
                    }
                }
                if (chunkType == "finish") {
                    val reason = chunk.optJSONObject("reason")
                    if (reason?.optString("kind") == "error") {
                        active.failure = reason.optJSONObject("failure")?.optString("message").orEmpty()
                    }
                }
            }
            "assistant/message" -> {
                val message = data.optJSONObject("message") ?: data
                active.finalMessage = textFromBlocks(message.optJSONArray("content"))
            }
            "turn/end" -> {
                activeStreams.remove(active.promptRpcId)
                val reason = data.optJSONObject("reason")
                val error = reason?.optJSONObject("error")?.optString("message")?.takeIf { it.isNotEmpty() }
                    ?: active.failure.takeIf { it.isNotEmpty() }
                val completed = active.accumulated.toString().ifEmpty { active.finalMessage }
                DshStreamLog.i(
                    "mux.turn-end session=$sessionId rpcId=${active.promptRpcId} acc=${active.accumulated.length} final=${active.finalMessage.length} error=${error ?: "-"} preview='${DshStreamLog.preview(completed)}'",
                )
                if (error != null) active.onError(error) else {
                    active.onComplete(completed)
                }
            }
        }
    }

    private fun sessionEventSource(data: JSONObject): JSONObject? =
        data.optJSONObject("source") ?: data.optJSONObject("message")?.optJSONObject("source")

    private fun resolveActiveStream(sessionId: String, type: String, rpcId: String): ActiveStream? {
        if (rpcId.isNotEmpty()) {
            activeStreams[rpcId]?.takeIf { it.sessionId == sessionId }?.let { return it }
        }
        return activeStreams.values.lastOrNull { it.sessionId == sessionId }
    }

    private fun handleHostFrame(payload: JSONObject) {
        when (payload.optString("type")) {
            "host/remote-event" -> {
                onRemoteEventHandler(payload.optString("event"))
                return
            }
            "host/session-added" -> {
                val id = payload.optString("sessionId")
                if (id.isEmpty()) return
                store.applySessionAdded(DshSession(
                    id = id,
                    title = "尚无标题",
                    workspace = "Host",
                    updatedLabel = "",
                    blank = true,
                    cwd = payload.optString("cwd"),
                    parentSessionId = payload.optString("parentSessionId").takeIf { it.isNotEmpty() },
                    origin = payload.optString("origin").takeIf { it.isNotEmpty() },
                    agentPreset = payload.optString("agentPreset").takeIf { it.isNotEmpty() },
                ))
            }
            "host/session-status" -> {
                val id = payload.optString("sessionId")
                val current = store.sessions[id] ?: return
                val running = payload.optBoolean("running")
                store.sessions[id] = current.copy(running = running, blank = if (running) false else current.blank)
                onSessionStatusHandler(id, running)
            }
            "host/session-removed" -> {
                val id = payload.optString("sessionId")
                store.applySessionRemoved(id)
            }
            "host/workspace-order-changed" -> {
                val order = payload.optJSONArray("workspaceIds")?.toString() ?: return
                store.reorderWorkspaces(order)
            }
            "host/archived-sessions-changed" -> {
                val archived = payload.optJSONArray("archivedSessionIds") ?: return
                val archivedIds = parseArchivedSessionIds(archived)
                store.replaceWorkspaceBaseline(store.workspaceBaseline, archivedIds)
                onArchivedSessionsChangedHandler()
            }
        }
    }

    private fun parseHistory(events: JSONArray): List<DshMessage> {
        val messages = mutableListOf<DshMessage>()
        val partials = mutableMapOf<String, StringBuilder>()
        for (index in 0 until events.length()) {
            val entry = events.optJSONObject(index) ?: continue
            val event = entry.optJSONObject("event") ?: entry
            val view = entry.optJSONObject("view")
            val viewValue = view?.optJSONObject("view")
            val seq = event.optInt("seq", index)
            val type = event.optString("type")
            val data = event.optJSONObject("data") ?: continue
            when (type) {
                "user/message" -> {
                    val content = data.optJSONArray("content")
                    val text = textFromBlocks(content)
                    val images = dshImageRefsFromBlocks(content)
                    if (text.isNotEmpty() || images.isNotEmpty()) {
                        messages += DshMessage(
                            "user-$seq",
                            DshMessageRole.USER,
                            text,
                            attachments = images,
                            attachmentId = images.firstOrNull()?.attachmentId,
                        )
                    }
                }
                "assistant/chunk" -> {
                    val key = "${data.optInt("turn")}:${data.optInt("step")}"
                    val chunk = data.optJSONObject("chunk")
                    val text = chunk?.optString("text").orEmpty()
                    if (text.isNotEmpty()) partials.getOrPut(key) { StringBuilder() }.append(text)
                    if (chunk?.optString("type") == "finish" && chunk.optJSONObject("reason")?.optString("kind") == "error") {
                        chunk.optJSONObject("reason")?.optJSONObject("failure")?.optString("message")?.takeIf { it.isNotEmpty() }?.let {
                            messages += DshMessage("turn-error-$seq", DshMessageRole.ERROR, it)
                        }
                    }
                }
                "assistant/message" -> {
                    val message = data.optJSONObject("message") ?: data
                    val key = "${data.optInt("turn")}:${data.optInt("step")}"
                    val text = textFromBlocks(message.optJSONArray("content")).ifEmpty { partials[key]?.toString().orEmpty() }
                    if (text.isNotEmpty()) messages += DshMessage("assistant-$seq", DshMessageRole.ASSISTANT, text)
                    partials.remove(key)
                }
                "tool/call" -> messages += DshMessage("tool-$seq", DshMessageRole.TOOL, "正在执行 ${data.optString("name").ifEmpty { "工具" }}", toolName = data.optString("name").takeIf { it.isNotEmpty() })
                "turn/end" -> data.optJSONObject("reason")?.optJSONObject("error")?.optString("message")?.takeIf { it.isNotEmpty() }?.let {
                    messages += DshMessage("turn-error-$seq", DshMessageRole.ERROR, it)
                }
            }
        }
        partials.forEach { (key, text) -> if (text.isNotEmpty()) messages += DshMessage("partial-$key", DshMessageRole.ASSISTANT, text.toString(), streaming = true) }
        return messages.filterNot { it.isRuntimeContextSnapshot() }
    }

    private fun DshWebTimelineItem.isRuntimeContextSnapshot(): Boolean {
        return text.startsWith("Current runtime context. This snapshot supersedes earlier runtime-context snapshots.")
    }

    private fun contextSourceLabel(source: JSONObject?): String {
        if (source == null) return "未知来源"
        return when {
            source.optString("kind") == "skill-invocation" -> source.optString("name").ifEmpty { "skill" }
            source.optString("plugin").isNotEmpty() -> source.optString("plugin")
            source.optString("name").isNotEmpty() -> source.optString("name")
            source.optString("kind").isNotEmpty() -> source.optString("kind")
            else -> "未知来源"
        }
    }

    private fun toolInputSummary(value: Any?): String = when (value) {
        null -> ""
        is String -> value
        else -> value.toString()
    }

    private fun toolOutputSummary(value: Any?): String = when (value) {
        null -> ""
        is String -> value
        is JSONArray -> textFromBlocks(value)
        else -> value.toString()
    }

    private fun toolCardType(view: JSONObject): DshToolCardType = when (view.optString("card")) {
        "terminal" -> DshToolCardType.TERMINAL
        "read" -> DshToolCardType.READ
        "diff" -> DshToolCardType.DIFF
        "search" -> DshToolCardType.SEARCH
        "web" -> DshToolCardType.WEB
        else -> DshToolCardType.GENERIC
    }

    private fun diffBody(view: JSONObject): String {
        val diffs = view.optJSONArray("diffs") ?: JSONArray()
        return buildString {
            for (index in 0 until diffs.length()) {
                val diff = diffs.optJSONObject(index) ?: continue
                appendLine(diff.optString("path"))
                appendLine("--- old")
                appendLine("+++ new")
                appendLine(diff.optString("oldText"))
                appendLine(diff.optString("newText"))
            }
        }.trim()
    }

    private fun toolResultBody(type: DshToolCardType, view: JSONObject, fallback: String): String {
        return when (type) {
            DshToolCardType.TERMINAL -> view.optString("output").ifEmpty { fallback }
            DshToolCardType.READ -> readBody(view)
            DshToolCardType.DIFF -> diffBody(view)
            DshToolCardType.SEARCH -> searchBody(view)
            DshToolCardType.WEB -> webBody(view)
            else -> fallback
        }
    }

    private fun readBody(view: JSONObject): String {
        val lines = view.optJSONArray("lines") ?: JSONArray()
        return buildString {
            for (index in 0 until lines.length()) {
                val line = lines.optJSONObject(index) ?: continue
                appendLine("${line.optInt("number")}\t${line.optString("text")}")
            }
        }.trim()
    }

    private fun searchBody(view: JSONObject): String {
        return when (view.optString("shape")) {
            "paths" -> {
                val paths = view.optJSONArray("paths") ?: JSONArray()
                buildString {
                    for (index in 0 until paths.length()) appendLine(paths.optString(index))
                }.trim()
            }
            else -> {
                val files = view.optJSONArray("files") ?: JSONArray()
                buildString {
                    for (index in 0 until files.length()) {
                        val file = files.optJSONObject(index) ?: continue
                        appendLine(file.optString("path"))
                        val matches = file.optJSONArray("matches") ?: JSONArray()
                        for (matchIndex in 0 until matches.length()) {
                            val match = matches.optJSONObject(matchIndex) ?: continue
                            appendLine("${match.optInt("lineNumber")}\t${match.optString("line")}")
                        }
                    }
                }.trim()
            }
        }
    }

    private fun webBody(view: JSONObject): String {
        return when (view.optString("kind")) {
            "fetch" -> "${view.optString("url")}\nHTTP ${view.optInt("statusCode")}"
            else -> {
                val sources = view.optJSONArray("sources") ?: JSONArray()
                buildString {
                    appendLine(view.optString("answer"))
                    for (index in 0 until sources.length()) {
                        val source = sources.optJSONObject(index) ?: continue
                        appendLine("- ${source.optString("title").ifEmpty { source.optString("url") }} ${source.optString("url")}")
                    }
                }.trim()
            }
        }
    }

    private fun textFromBlocks(blocks: JSONArray?): String {
        if (blocks == null) return ""
        return buildString {
            for (index in 0 until blocks.length()) {
                val block = blocks.optJSONObject(index) ?: continue
                if (block.optString("type") == "text") append(block.optString("text"))
            }
        }
    }

    private companion object {
        const val DEEPSEEK_PROVIDER = "deepseek-official"
        const val DEEPSEEK_SETTINGS_NS = "llm-deepseek"
        const val DEEPSEEK_CREDENTIAL_REF = "DEEPSEEK_API_KEY"
        const val HISTORY_PAGE_MESSAGES = 80
    }
}

internal fun pendingInteractionRpcId(envelope: JSONObject, payload: JSONObject): String {
    val nested = payload.optJSONObject("payload")
    return listOf(
        envelope.optString("rpcId"),
        payload.optString("rpcId"),
        nested?.optString("rpcId").orEmpty(),
    ).firstOrNull { it.isNotEmpty() }.orEmpty()
}

internal fun parseRespondReceipt(data: JSONObject): Pair<Boolean, String> {
    val result = data.optJSONObject("result")
    val value = result?.optJSONObject("value")
    val accepted = jsonFlag(data, "accepted")
        ?: jsonFlag(value, "accepted")
        ?: false
    val reason = data.optString("reason")
        .ifEmpty { value?.optString("reason").orEmpty() }
        .ifEmpty { result?.optJSONObject("error")?.optString("message").orEmpty() }
        .ifEmpty { if (accepted) "" else "bad-response" }
    return accepted to reason
}

private fun jsonFlag(obj: JSONObject?, key: String): Boolean? {
    if (obj == null) return null
    val raw = obj.opt(key) ?: return null
    return when (raw) {
        is Boolean -> raw
        is Number -> raw.toInt() != 0
        is String -> raw.equals("true", ignoreCase = true)
        else -> obj.optBoolean(key)
    }
}

internal fun dshEncodeQueryComponent(value: String): String {
    val allowed = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.~"
    return buildString {
        value.encodeToByteArray().forEach { byte ->
            val unsigned = byte.toInt() and 0xFF
            val char = unsigned.toChar()
            if (char in allowed) append(char)
            else append('%').append(unsigned.toString(16).uppercase().padStart(2, '0'))
        }
    }
}
