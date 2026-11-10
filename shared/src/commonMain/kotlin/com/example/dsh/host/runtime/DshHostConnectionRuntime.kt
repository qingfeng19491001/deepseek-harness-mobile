package com.example.dsh.host.runtime

import com.example.dsh.dsh.*
import com.example.dsh.host.parser.parseHostRpcBody
import com.example.dsh.host.parser.parseRespondReceipt
import com.example.dsh.host.protocol.DshHostProtocol
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.timer.setTimeout

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
