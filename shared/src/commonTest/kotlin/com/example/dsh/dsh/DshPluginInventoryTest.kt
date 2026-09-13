package com.example.dsh.dsh

import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DshPluginInventoryTest {
    @Test
    fun officialFailedEntryGetsReadableHint() {
        val snapshot = parseOfficialPluginSnapshot(
            JSONObject(
                """{"entries":[{"entryId":"broken","moduleName":"@scope/demo-plugin","enabled":true,"fiberPhase":"failed"}]}""",
            ),
        )
        assertFalse(snapshot.writable)
        assertEquals("official", snapshot.source)
        assertEquals("demo-plugin", snapshot.entries.single().displayName)
        assertEquals(DshPluginFiberPhase.FAILED, snapshot.entries.single().fiberPhase)
        assertEquals(DSH_OFFICIAL_FAILED_HINT, snapshot.entries.single().error)
        assertEquals("已启用", snapshot.entries.single().configSummary)
    }

    @Test
    fun adminSnapshotKeepsConfigErrorAndWriteFlag() {
        val snapshot = parsePluginSnapshot(
            JSONObject(
                """{"protocol":"dsh-mobile-plugin/v1","writable":true,"entries":[{"entryId":"notes","moduleName":"notes-plugin","displayName":"notes-plugin","enabled":true,"fiberPhase":"active","configSummary":"{\"folder\":\"~/notes\"}","config":{"folder":"~/notes"},"protected":false}]}""",
            ),
            source = "admin",
            writable = true,
        )
        assertTrue(snapshot.writable)
        assertEquals("~/notes", JSONObject(snapshot.entries.single().configJson).optString("folder"))
        assertEquals("", snapshot.entries.single().error)
    }

    @Test
    fun searchAndStatusFilter() {
        val entries = listOf(
            plugin("a", "Alpha Tools", enabled = true, phase = DshPluginFiberPhase.ACTIVE),
            plugin("b", "Broken Notes", enabled = true, phase = DshPluginFiberPhase.FAILED, error = "missing token"),
            plugin("c", "Cache", enabled = false, phase = DshPluginFiberPhase.NONE),
            plugin("d", "Delta", enabled = true, phase = DshPluginFiberPhase.LOADING),
        )
        assertEquals(listOf("b", "a", "c", "d"), filterPluginEntries(entries, "", DshPluginFilter.ALL).map { it.entryId })
        assertEquals(listOf("a"), filterPluginEntries(entries, "alpha", DshPluginFilter.ALL).map { it.entryId })
        assertEquals(listOf("c"), filterPluginEntries(entries, "", DshPluginFilter.DISABLED).map { it.entryId })
        assertEquals(listOf("b"), filterPluginEntries(entries, "token", DshPluginFilter.FAILED).map { it.entryId })
        assertEquals(listOf("d"), filterPluginEntries(entries, "", DshPluginFilter.TRANSITION).map { it.entryId })
    }

    @Test
    fun capabilitiesGateWriteFeatures() {
        val caps = JSONObject("""{"features":["plugin.inventory","plugin.control"]}""")
        assertTrue(capabilitiesAllow(caps, "plugin.control"))
        assertFalse(capabilitiesAllow(JSONObject(), "plugin.control"))
    }

    @Test
    fun parseHostRpcBodyReadsOfficialAndAdminEnvelopes() {
        val admin = parseHostRpcBody(
            JSONObject("""{"result":{"ok":true,"value":{"entries":[]}}}"""),
            "dsh.plugin.inventory",
        )
        assertEquals(0, admin.first?.optJSONArray("entries")?.length() ?: -1)

        val failed = parseHostRpcBody(
            JSONObject("""{"result":{"ok":false,"error":{"code":"protected","message":"不可停用"}}}"""),
            "dsh.plugin.control",
        )
        assertEquals("protected", failed.second?.code)

        val typert = parseHostRpcBody(
            JSONObject("""{"entries":[{"entryId":"a","moduleName":"demo","enabled":true,"fiberPhase":"active"}]}"""),
            "pluginInventory/list",
            allowBareValue = true,
        )
        assertEquals("a", typert.first?.optJSONArray("entries")?.optJSONObject(0)?.optString("entryId"))
    }

    @Test
    fun inventorySubtitleDistinguishesReadonly() {
        assertEquals("可管理 · 已接入管理插件 · 3 个插件", pluginInventorySubtitle(true, "admin", 3))
        assertEquals("只读 · 来自 Host 官方清单", pluginInventorySubtitle(false, "official", 0))
    }

    @Test
    fun shortNameStripsScopeAndPrefix() {
        assertEquals("scan-remote", pluginDisplayName("@yukiykchen/dsh-scan-remote", "scan"))
        assertEquals("plugin-inventory", pluginDisplayName("@deepseek-ai/dsh-host-plugin-inventory", "inv"))
    }

    private fun plugin(
        id: String,
        name: String,
        enabled: Boolean,
        phase: DshPluginFiberPhase,
        error: String = "",
    ) = DshPluginEntry(
        entryId = id,
        moduleName = name.lowercase(),
        displayName = name,
        enabled = enabled,
        fiberPhase = phase,
        configSummary = if (enabled) "已启用" else "已停用",
        error = error,
    )
}
