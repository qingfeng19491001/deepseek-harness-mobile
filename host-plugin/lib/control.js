import { mapEntry, SELF_MODULE } from "./inventory.js"
import { protectionFor } from "./protected.js"

const ACTIONS = new Set(["enable", "disable", "reload"])

export function previewControl(entry, action, selfNames = [SELF_MODULE]) {
    if (!ACTIONS.has(action)) {
        return { ok: false, code: "unsupported-action", message: "只支持启用、停用和重载" }
    }
    if (entry == null) {
        return { ok: false, code: "not-found", message: "找不到这个插件" }
    }
    const mapped = mapEntry(entry, selfNames)
    const protection = protectionFor(mapped.moduleName, mapped.entryId, selfNames)
    if (protection.protected) {
        return { ok: false, code: "protected", message: protection.reason, entry: mapped }
    }
    return { ok: true, entry: mapped }
}

export async function applyControl(entry, action) {
    if (action === "disable") {
        await entry.update({ disabled: true })
        return
    }
    if (action === "enable") {
        await entry.update({ disabled: false })
        return
    }
    if (typeof entry.update === "function") {
        await entry.update({}, false, true)
        return
    }
    if (entry.fiber && typeof entry.fiber.update === "function") {
        await entry.fiber.update(entry.fiber.config, true)
    }
}
