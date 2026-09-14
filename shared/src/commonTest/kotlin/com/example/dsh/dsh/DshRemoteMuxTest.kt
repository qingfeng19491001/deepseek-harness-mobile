package com.example.dsh.dsh

import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DshRemoteMuxTest {
    @Test
    fun httpEndpointUsesSlashRpcPaths() {
        assertEquals("session/list", DshRemoteMux.httpEndpoint("session.list"))
        assertEquals("session/page", DshRemoteMux.httpEndpoint("session.history"))
        assertEquals("workspace/create", DshRemoteMux.httpEndpoint("workspace.create"))
        assertEquals("/api/remote.mux", DshRemoteMux.PATH)
    }

    @Test
    fun httpPayloadWrapsNamedRequestArgs() {
        val prompt = DshRemoteMux.httpPayload(
            "session.prompt",
            JSONObject().apply {
                put("sessionId", "s1")
                put("mode", "queue")
            },
            "dsh-g1-1",
        )
        val request = prompt.optJSONObject("args")?.optJSONObject("request")
        assertEquals("dsh-g1-1", request?.optString("requestId"))
        assertEquals("s1", request?.optString("sessionId"))
    }

    @Test
    fun sessionListUsesUnderscoreRequestWireField() {
        val payload = DshRemoteMux.httpPayload("session.list", JSONObject(), "rpc")
        assertTrue(payload.optJSONObject("args")?.has("_request") == true)
        assertEquals("session/modelCatalog", DshRemoteMux.httpEndpoint("session.models"))
        assertEquals("skills/list", DshRemoteMux.httpEndpoint("skill.list"))
        assertEquals("llm/listConfigurableProviders", DshRemoteMux.httpEndpoint("llm.providers"))
        assertEquals(0, DshRemoteMux.httpPayload("settings.describe", JSONObject(), "rpc")
            .optJSONObject("args")?.length())
        assertEquals(0, DshRemoteMux.httpPayload("pluginInventory/list", JSONObject(), "rpc")
            .optJSONObject("args")?.length())
        assertEquals(0, DshRemoteMux.httpPayload("skill.list", JSONObject(), "rpc")
            .optJSONObject("args")?.length())
    }

    @Test
    fun mapsSessionAddedEmitOntoLegacyHostFrame() {
        val value = JSONObject().apply {
            put("type", "emit")
            put("event", "api-session/added")
            put("args", JSONArray().apply {
                put(JSONObject().put("sessionId", "abc").put("cwd", "/tmp"))
            })
        }
        val mapped = DshRemoteMux.mapEventsItem(value)
        assertEquals(DshEventStream.HOST, mapped?.first)
        assertEquals("host/session-added", mapped?.second?.optString("type"))
        assertEquals("abc", mapped?.second?.optString("sessionId"))
    }

    @Test
    fun readyItemIsNotALegacyFrame() {
        val value = JSONObject().apply {
            put("type", "ready")
            put("clientId", "c1")
            put("host", JSONObject().put("home", "/Users/mac"))
        }
        assertNull(DshRemoteMux.mapEventsItem(value))
    }

    @Test
    fun followEventBecomesSessionEvent() {
        val payload = DshRemoteMux.mapFollowItem(
            "s1",
            JSONObject().apply {
                put("type", "event")
                put("event", JSONObject().apply {
                    put("type", "assistant/chunk")
                    put("seq", 3)
                })
            },
        )
        assertEquals("session/event", payload?.optString("type"))
        assertEquals("s1", payload?.optString("sessionId"))
        assertEquals(3, payload?.optJSONObject("event")?.optInt("seq"))
    }

    @Test
    fun openMessageMatchesGatewayClientShape() {
        val raw = DshRemoteMux.openMessage("sid", DshRemoteMux.EVENTS_ENDPOINT)
        val json = JSONObject(raw)
        assertEquals("open", json.optString("type"))
        assertEquals("\$events", json.optString("endpoint"))
        assertTrue(json.optJSONObject("payload")?.optJSONObject("args") != null)
        assertEquals(0, json.optJSONObject("payload")?.optJSONObject("args")?.length())
    }

    @Test
    fun adaptHttpValueWrapsProviderArrayAndModelCatalog() {
        val providers = DshRemoteMux.adaptHttpValue(
            "llm.providers",
            null,
            JSONArray().apply { put(JSONObject().put("provider", "deepseek-official")) },
        )
        assertEquals("deepseek-official", providers.optJSONArray("providers")?.optJSONObject(0)?.optString("provider"))

        val catalog = DshRemoteMux.adaptHttpValue(
            "session.models",
            JSONObject().apply {
                put("default", JSONObject().put("provider", "p").put("model", "m"))
                put("routableProviders", JSONArray().apply { put("p") })
            },
            null,
        )
        assertEquals("m", catalog.optJSONObject("current")?.optString("model"))
        assertTrue(catalog.optBoolean("routable"))

        val credentials = DshRemoteMux.adaptHttpValue(
            "credentials.describe",
            JSONObject().put("DEEPSEEK_API_KEY", JSONObject().put("configured", true).put("writable", true)),
            null,
        )
        assertTrue(credentials.optJSONObject("credentials")?.optJSONObject("DEEPSEEK_API_KEY")?.optBoolean("configured") == true)
    }

    @Test
    fun snapshotRecordsUnwrapEventEntries() {
        val events = DshRemoteMux.snapshotRecords(
            JSONObject().put(
                "records",
                JSONArray().apply {
                    put(JSONObject().put("type", "event").put("event", JSONObject().put("type", "user/message").put("seq", 1)))
                },
            ),
        )
        assertEquals("user/message", events.optJSONObject(0)?.optJSONObject("event")?.optString("type"))
    }
}
