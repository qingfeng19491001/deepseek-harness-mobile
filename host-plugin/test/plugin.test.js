import test from "node:test"
import assert from "node:assert/strict"
import { authorizeRequest, isLoopbackAddress } from "../lib/auth.js"
import { configSummary, redactValue } from "../lib/redact.js"
import { protectionFor } from "../lib/protected.js"
import { collectEntries, fiberPhase, mapEntry } from "../lib/inventory.js"
import { previewControl } from "../lib/control.js"
import { dispatch } from "../plugin.js"

test("redacts secret-looking keys", () => {
    const redacted = redactValue({
        relay: "http://127.0.0.1:8787",
        hostToken: "abcdef123456",
        nested: { apiKey: "sk-live-secret" },
    })
    assert.equal(redacted.relay, "http://127.0.0.1:8787")
    assert.equal(redacted.hostToken, "ab••••56")
    assert.equal(redacted.nested.apiKey, "sk••••et")
})

test("config summary falls back when empty", () => {
    assert.equal(configSummary({}, true), "已启用 · 无额外配置")
    assert.equal(configSummary({}, false), "已停用")
})

test("protects self, scan-remote and host core", () => {
    assert.equal(protectionFor("dsh-mobile-plugin-admin", "admin").protected, true)
    assert.equal(protectionFor("@yukiykchen/dsh-scan-remote", "scan").protected, true)
    assert.equal(protectionFor("@deepseek-ai/dsh-host-apiproxy", "api").protected, true)
    assert.equal(protectionFor("markdown-preview", "md").protected, false)
})

test("maps failed fiber errors", () => {
    const mapped = mapEntry({
        id: "broken",
        disabled: false,
        options: { name: "@scope/demo-plugin", config: { path: "/tmp" } },
        fiber: { state: 3, _error: new Error("config missing token") },
    })
    assert.equal(mapped.fiberPhase, "failed")
    assert.match(mapped.error, /config missing token/)
    assert.equal(mapped.displayName, "demo-plugin")
    assert.equal(fiberPhase({ state: "active" }), "active")
})

test("collectEntries skips groups", () => {
    const loader = {
        entries() {
            return [
                { id: "g1", options: { group: true, name: "group" } },
                { id: "p1", disabled: true, options: { name: "demo", config: {} } },
            ]
        },
    }
    const entries = collectEntries(loader)
    assert.equal(entries.length, 1)
    assert.equal(entries[0].enabled, false)
})

test("control preview blocks protected plugins", () => {
    const entry = {
        id: "scan",
        options: { name: "@yukiykchen/dsh-scan-remote" },
        update: async () => {},
    }
    const preview = previewControl(entry, "disable")
    assert.equal(preview.ok, false)
    assert.equal(preview.code, "protected")
})

test("rejects non-loopback requests", () => {
    assert.equal(isLoopbackAddress("127.0.0.1"), true)
    assert.equal(isLoopbackAddress("::ffff:127.0.0.1"), true)
    assert.equal(authorizeRequest({ socket: { remoteAddress: "10.0.0.8" } }).ok, false)
    assert.equal(authorizeRequest({ socket: { remoteAddress: "127.0.0.1" } }).ok, true)
})

test("rpc inventory and confirm gate", async () => {
    const updates = []
    const loader = {
        entries() {
            return [{
                id: "notes",
                disabled: false,
                options: { name: "notes-plugin", config: { folder: "~/notes" } },
                fiber: { state: 2 },
                async update(patch) { updates.push(patch) },
            }]
        },
    }
    const inventory = await dispatch(loader, "dsh.plugin.inventory", {})
    assert.equal(inventory.result.ok, true)
    assert.equal(inventory.result.value.entries[0].displayName, "notes-plugin")

    const denied = await dispatch(loader, "dsh.plugin.control", { entryId: "notes", action: "disable" })
    assert.equal(denied.result.error.code, "confirm-required")

    const accepted = await dispatch(loader, "dsh.plugin.control", {
        entryId: "notes",
        action: "disable",
        confirm: true,
    })
    assert.equal(accepted.result.ok, true)
    assert.deepEqual(updates[0], { disabled: true })
})
