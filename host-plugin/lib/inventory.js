import { moduleShortName, formatError } from "./names.js"
import { protectionFor } from "./protected.js"
import { configSummary, redactValue } from "./redact.js"

const FIBER_PHASE = {
    0: "pending",
    1: "loading",
    2: "active",
    3: "failed",
    4: null,
    5: "unloading",
    PENDING: "pending",
    LOADING: "loading",
    ACTIVE: "active",
    FAILED: "failed",
    DISPOSED: null,
    UNLOADING: "unloading",
    pending: "pending",
    loading: "loading",
    active: "active",
    failed: "failed",
    disposed: null,
    unloading: "unloading",
}

export const PROTOCOL = "dsh-mobile-plugin/v1"
export const SELF_MODULE = "dsh-mobile-plugin-admin"

export function fiberPhase(fiber) {
    if (fiber == null) return null
    const state = fiber.state
    if (state in FIBER_PHASE) return FIBER_PHASE[state]
    return null
}

export function fiberError(fiber) {
    if (fiber == null) return ""
    return formatError(fiber._error ?? fiber.error ?? fiber.reason ?? fiber.failure)
}

export function mapEntry(entry, selfNames = [SELF_MODULE]) {
    const moduleName = String(entry?.options?.name ?? "")
    const entryId = String(entry?.id ?? entry?.options?.id ?? "")
    const enabled = !entry?.disabled
    const phase = fiberPhase(entry?.fiber)
    const rawConfig = entry?.options?.config ?? entry?.fiber?.config ?? {}
    const config = redactValue(rawConfig)
    const protection = protectionFor(moduleName, entryId, selfNames)
    const error = phase === "failed"
        ? fiberError(entry?.fiber) || "插件启动失败，未能读取更详细的错误。"
        : ""
    return {
        entryId,
        moduleName,
        displayName: moduleShortName(moduleName) || entryId || "未命名插件",
        enabled,
        fiberPhase: phase,
        configSummary: configSummary(config, enabled),
        config,
        error,
        protected: protection.protected,
        protectReason: protection.reason,
    }
}

export function collectEntries(loader, selfNames = [SELF_MODULE]) {
    const items = []
    if (loader == null || typeof loader.entries !== "function") return items
    for (const entry of loader.entries()) {
        if (entry?.options?.group) continue
        items.push(mapEntry(entry, selfNames))
    }
    return items
}

export function findEntry(loader, entryId) {
    if (loader == null || typeof loader.entries !== "function") return null
    for (const entry of loader.entries()) {
        if (entry?.options?.group) continue
        const id = String(entry?.id ?? entry?.options?.id ?? "")
        if (id === entryId) return entry
    }
    return null
}
