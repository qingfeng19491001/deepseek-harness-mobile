export function json(res, status, body) {
    res.writeHead(status, {
        "content-type": "application/json; charset=utf-8",
        "cache-control": "no-store",
    })
    res.end(JSON.stringify(body))
}

export function rpcOk(value) {
    return { result: { ok: true, value } }
}

export function rpcErr(code, message, details = {}) {
    return { result: { ok: false, error: { code, message, details } } }
}

export async function readJson(req) {
    const chunks = []
    for await (const chunk of req) chunks.push(chunk)
    const raw = Buffer.concat(chunks).toString("utf8").trim()
    if (!raw) return {}
    return JSON.parse(raw)
}

export function capabilitiesValue() {
    return {
        protocol: "dsh-mobile-plugin/v1",
        features: ["plugin.inventory", "plugin.control"],
        control: {
            actions: ["enable", "disable", "reload"],
            confirmRequired: true,
        },
    }
}
