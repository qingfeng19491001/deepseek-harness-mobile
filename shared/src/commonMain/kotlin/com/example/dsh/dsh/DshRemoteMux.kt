package com.example.dsh.dsh

import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/** DSH 0.1.5 Host Connection: one multiplexed WebSocket plus slash RPC endpoints. */
internal object DshRemoteMux {
    const val PATH = "${DshHostProtocol.API_PREFIX}/remote.mux"
    const val EVENTS_ENDPOINT = "\$events"
    const val EVENTS_RESULT = "\$events/result"
    const val WORKSPACE_FOLLOW = "workspace/follow"
    const val SESSION_FOLLOW = "session/follow"
    const val SESSION_CONTROL = "session/control"

    fun httpEndpoint(dottedMethod: String): String = when (dottedMethod) {
        DshHostProtocol.SESSION_HISTORY -> "session/page"
        DshHostProtocol.SESSION_MODELS -> "session/modelCatalog"
        DshHostProtocol.SKILL_LIST -> "skills/list"
        DshHostProtocol.LLM_PROVIDERS -> "llm/listConfigurableProviders"
        DshHostProtocol.AGENT_PRESET_LIST -> "agentPresets/list"
        DshHostProtocol.HOST_LIST_DIRECTORY -> "directoryPicker/list"
        DshHostProtocol.HOST_CREATE_DIRECTORY -> "directoryPicker/createDirectory"
        else -> dottedMethod.replace('.', '/')
    }

    fun httpPayload(dottedMethod: String, payload: JSONObject, rpcId: String): JSONObject {
        return when (dottedMethod) {
            DshHostProtocol.WORKSPACE_LIST,
            DshHostProtocol.SESSION_MODELS,
            DshHostProtocol.SETTINGS_DESCRIBE,
            DshHostProtocol.LLM_PROVIDERS,
            DshHostProtocol.AGENT_PRESET_LIST,
            DshHostProtocol.SKILL_LIST,
            DshHostProtocol.PLUGIN_INVENTORY_LIST,
            -> JSONObject().put("args", JSONObject())
            DshHostProtocol.SESSION_LIST -> JSONObject().put(
                "args",
                JSONObject().put("_request", copyObject(payload)),
            )
            DshHostProtocol.CREDENTIALS_DESCRIBE,
            DshHostProtocol.CREDENTIALS_SET,
            DshHostProtocol.HOST_LIST_DIRECTORY,
            DshHostProtocol.HOST_CREATE_DIRECTORY,
            -> JSONObject().put("args", copyObject(payload))
            DshHostProtocol.SESSION_HISTORY -> {
                val request = JSONObject().apply {
                    put("address", JSONObject().apply {
                        put("kind", "session")
                        put("sessionId", payload.optString("sessionId"))
                    })
                    val throughSeq = payload.optInt("throughSeq", -1)
                    if (throughSeq >= 0) put("throughSeq", throughSeq)
                    val maxMessages = payload.optInt("maxMessages", 0)
                    if (maxMessages > 0) put("maxMessages", maxMessages)
                    val beforeSeq = payload.optInt("beforeSeq", -1)
                    if (beforeSeq >= 0) put("beforeSeq", beforeSeq)
                }
                JSONObject().put("args", JSONObject().put("request", request))
            }
            DshHostProtocol.SESSION_PROMPT -> {
                val request = copyObject(payload)
                if (request.optString("requestId").isEmpty()) request.put("requestId", rpcId)
                if (request.optString("mode").isEmpty()) request.put("mode", "queue")
                JSONObject().put("args", JSONObject().put("request", request))
            }
            else -> JSONObject().put("args", JSONObject().put("request", copyObject(payload)))
        }
    }

    fun adaptHttpValue(dottedMethod: String, value: JSONObject?, array: JSONArray?): JSONObject {
        if (dottedMethod == DshHostProtocol.LLM_PROVIDERS) {
            val providers = array ?: value?.optJSONArray("providers") ?: JSONArray()
            return JSONObject().put("providers", providers)
        }
        val adapted = value?.let(::copyObject) ?: JSONObject()
        if (dottedMethod == DshHostProtocol.CREDENTIALS_DESCRIBE && !adapted.has("credentials")) {
            return JSONObject().put("credentials", adapted)
        }
        if (dottedMethod == DshHostProtocol.SESSION_MODELS) {
            if (!adapted.has("current")) {
                adapted.optJSONObject("default")?.let { adapted.put("current", it) }
            }
            if (!adapted.has("routable")) {
                val providers = adapted.optJSONArray("routableProviders")
                adapted.put("routable", providers != null && providers.length() > 0)
            }
        }
        if (dottedMethod == DshHostProtocol.SESSION_HISTORY) {
            if (!adapted.has("events")) adapted.put("events", snapshotRecords(adapted))
        }
        return adapted
    }

    fun openMessage(streamId: String, endpoint: String, args: JSONObject = JSONObject()): String =
        JSONObject().apply {
            put("type", "open")
            put("streamId", streamId)
            put("endpoint", endpoint)
            put("payload", JSONObject().put("args", args))
        }.toString()

    fun cancelMessage(streamId: String): String =
        JSONObject().apply {
            put("type", "cancel")
            put("streamId", streamId)
        }.toString()

    fun sessionFollowArgs(sessionId: String): JSONObject = JSONObject().put(
        "request",
        JSONObject().apply {
            put("address", JSONObject().apply {
                put("kind", "session")
                put("sessionId", sessionId)
            })
            put("assistantStream", true)
        },
    )

    fun muxEnvelope(payload: JSONObject): String =
        JSONObject().put("payload", payload).toString()

    fun mapEventsItem(value: JSONObject): Pair<DshEventStream, JSONObject>? {
        when (value.optString("type")) {
            "ready" -> return null
            "emit" -> {
                val event = value.optString("event")
                val args = value.optJSONArray("args") ?: JSONArray()
                return DshEventStream.HOST to mapEmit(event, args)
            }
            "waterfall" -> {
                val event = value.optString("event")
                val request = value.optJSONObject("request") ?: JSONObject()
                val payload = copyObject(request).apply {
                    put("type", waterfallType(event))
                    if (optString("rpcId").isEmpty()) {
                        put("rpcId", value.optString("eventId"))
                    }
                }
                return DshEventStream.MUX to payload
            }
        }
        return null
    }

    fun mapFollowItem(sessionId: String, value: JSONObject): JSONObject? {
        return when (value.optString("type")) {
            "snapshot" -> null
            "event" -> {
                val event = value.optJSONObject("event") ?: return null
                JSONObject().apply {
                    put("type", "session/event")
                    put("sessionId", sessionId)
                    put("event", event)
                }
            }
            "assistant-stream" -> {
                val frame = value.optJSONObject("frame") ?: return null
                if (frame.optString("type") != "chunk") return null
                val chunk = frame.optJSONObject("chunk") ?: JSONObject().apply {
                    put("type", "text-delta")
                    put("text", frame.optString("text"))
                }
                JSONObject().apply {
                    put("type", "session/event")
                    put("sessionId", sessionId)
                    put("event", JSONObject().apply {
                        put("type", "assistant/chunk")
                        put("seq", -1)
                        put("data", JSONObject().put("chunk", chunk))
                    })
                }
            }
            "queue" -> JSONObject().apply {
                put("type", "session/queue")
                put("sessionId", value.optString("sessionId").ifEmpty { sessionId })
                put("items", value.optJSONArray("items") ?: JSONArray())
            }
            "jobs" -> JSONObject().apply {
                put("type", "session/jobs")
                put("sessionId", value.optString("sessionId").ifEmpty { sessionId })
                put("jobs", value.optJSONArray("jobs") ?: JSONArray())
            }
            "projection" -> JSONObject().apply {
                put("type", "session/projection")
                put("sessionId", value.optString("sessionId").ifEmpty { sessionId })
                put("key", value.optString("key"))
                put("value", value.optString("value"))
                put("seq", value.optInt("seq", -1))
            }
            else -> null
        }
    }

    fun mapWorkspaceItem(value: JSONObject): Pair<String, JSONObject>? {
        return when (value.optString("type")) {
            "baseline" -> "baseline" to (value.optJSONObject("value") ?: JSONObject())
            "order" -> "order" to JSONObject().apply {
                put("type", "host/workspace-order-changed")
                put("workspaceIds", value.optJSONArray("workspaceIds") ?: JSONArray())
            }
            else -> null
        }
    }

    fun snapshotRecords(value: JSONObject): JSONArray {
        val records = value.optJSONArray("records") ?: JSONArray()
        val events = JSONArray()
        for (index in 0 until records.length()) {
            val record = records.optJSONObject(index) ?: continue
            val event = record.optJSONObject("event") ?: record
            events.put(JSONObject().put("event", event))
        }
        return events
    }

    fun emptyWorkspaceBaseline(): JSONObject = JSONObject().apply {
        put("items", JSONArray())
        put("archivedSessionIds", JSONArray())
    }

    private fun copyObject(payload: JSONObject): JSONObject = JSONObject(payload.toString())

    private fun mapEmit(event: String, args: JSONArray): JSONObject {
        val first = args.optJSONObject(0)
        val firstString = args.optString(0).orEmpty()
        return when (event) {
            "api-session/added" -> {
                val summary = first ?: JSONObject()
                JSONObject().apply {
                    put("type", "host/session-added")
                    put("sessionId", summary.optString("sessionId"))
                    put("cwd", summary.optString("cwd"))
                    put("parentSessionId", summary.optString("parentSessionId"))
                    put("origin", summary.optString("origin"))
                    put("agentPreset", summary.optJSONObject("projections")
                        ?.optJSONObject("values")
                        ?.optString("agentPreset"))
                }
            }
            "api-session/removed" -> JSONObject().apply {
                put("type", "host/session-removed")
                put("sessionId", firstString)
            }
            "api-session/status" -> JSONObject().apply {
                put("type", "host/session-status")
                put("sessionId", firstString)
                put("running", booleanArg(args, 1))
            }
            "settings/document-updated",
            "credentials/reference-updated",
            "llm/adapters-updated",
            "agent-preset/selected" -> JSONObject().apply {
                put("type", "host/remote-event")
                put("event", event)
            }
            else -> JSONObject().apply {
                put("type", "host/remote-event")
                put("event", event)
            }
        }
    }

    private fun booleanArg(args: JSONArray, index: Int): Boolean {
        val raw = args.opt(index) ?: return false
        return when (raw) {
            is Boolean -> raw
            is Number -> raw.toInt() != 0
            is String -> raw.equals("true", ignoreCase = true)
            else -> args.optString(index) == "true" || args.optInt(index) == 1
        }
    }

    private fun waterfallType(event: String): String = when (event) {
        "approval/request" -> "approval/requested"
        "user-questions/request" -> "question/requested"
        else -> event
    }
}
