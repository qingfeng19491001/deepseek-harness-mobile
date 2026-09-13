import { authorizeRequest } from "./lib/auth.js"
import { applyControl, previewControl } from "./lib/control.js"
import { collectEntries, findEntry, mapEntry, PROTOCOL, SELF_MODULE } from "./lib/inventory.js"
import { capabilitiesValue, json, readJson, rpcErr, rpcOk } from "./lib/rpc.js"

export const name = SELF_MODULE
export const inject = ["loader", "webServer"]

const PREFIX = "/dsh-mobile"

export function apply(ctx) {
    const loader = ctx.loader
    const webServer = ctx.webServer
    if (webServer == null || typeof webServer.register !== "function") {
        ctx.logger?.warn?.("dsh-mobile-plugin-admin: webServer is unavailable")
        return
    }
    ctx.effect(() => {
        const unregister = webServer.register({
            kind: "prefix",
            path: PREFIX,
            handler: createHandler(loader, ctx.logger),
        })
        ctx.logger?.info?.("dsh-mobile-plugin-admin listening on /dsh-mobile/rpc")
        return () => unregister?.()
    }, "dsh-mobile-plugin-admin.routes")
}

export function createHandler(loader, logger) {
    return async (req, res) => {
        const path = new URL(req.url ?? "/", "http://127.0.0.1").pathname
        try {
            const auth = authorizeRequest(req)
            if (!auth.ok) {
                return json(res, 403, rpcErr(auth.code, auth.message))
            }
            if (req.method === "GET" && path === `${PREFIX}/health`) {
                return json(res, 200, { ok: true, protocol: PROTOCOL })
            }
            if (req.method !== "POST" || path !== `${PREFIX}/rpc`) {
                return json(res, 404, { reason: "not_found" })
            }
            const body = await readJson(req)
            const method = String(body.method ?? "")
            const payload = body.payload && typeof body.payload === "object" ? body.payload : {}
            const value = await dispatch(loader, method, payload)
            return json(res, 200, value)
        } catch (error) {
            logger?.warn?.(error)
            const message = error instanceof Error ? error.message : String(error)
            return json(res, 200, rpcErr("internal", message))
        }
    }
}

export async function dispatch(loader, method, payload) {
    switch (method) {
        case "dsh.mobile.capabilities":
            return rpcOk(capabilitiesValue())
        case "dsh.plugin.inventory":
            return rpcOk({
                protocol: PROTOCOL,
                writable: true,
                entries: collectEntries(loader),
                agentPresets: [],
            })
        case "dsh.plugin.get": {
            const entryId = String(payload.entryId ?? "")
            const entry = findEntry(loader, entryId)
            if (entry == null) return rpcErr("not-found", "找不到这个插件")
            return rpcOk(mapEntry(entry))
        }
        case "dsh.plugin.control":
            return control(loader, payload)
        default:
            return rpcErr("unknown-method", `不支持 ${method}`)
    }
}

async function control(loader, payload) {
    const entryId = String(payload.entryId ?? "")
    const action = String(payload.action ?? "")
    const entry = findEntry(loader, entryId)
    const preview = previewControl(entry, action)
    if (!preview.ok) return rpcErr(preview.code, preview.message)
    if (payload.confirm !== true) {
        return rpcErr("confirm-required", "请确认后再执行插件启停或重载", {
            entryId,
            action,
            displayName: preview.entry?.displayName,
        })
    }
    await applyControl(entry, action)
    const latest = findEntry(loader, entryId)
    return rpcOk({
        entryId,
        action,
        status: "accepted",
        message: actionLabel(action),
        entry: latest ? mapEntry(latest) : preview.entry,
    })
}

function actionLabel(action) {
    if (action === "enable") return "已请求启用"
    if (action === "disable") return "已请求停用"
    return "已请求重载"
}
