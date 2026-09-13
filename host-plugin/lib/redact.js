const SECRET_KEY = /key|token|secret|password|passphrase|credential|authorization|cookie|private/i

export function redactValue(value, depth = 0) {
    if (depth > 8) return "[…]"
    if (Array.isArray(value)) {
        return value.slice(0, 40).map((item) => redactValue(item, depth + 1))
    }
    if (value && typeof value === "object") {
        const out = {}
        for (const [key, nested] of Object.entries(value)) {
            out[key] = SECRET_KEY.test(key) && typeof nested === "string"
                ? redactSecret(nested)
                : redactValue(nested, depth + 1)
        }
        return out
    }
    return value
}

export function redactSecret(value) {
    if (!value) return "••••"
    if (value.length <= 4) return "••••"
    return `${value.slice(0, 2)}••••${value.slice(-2)}`
}

export function configSummary(config, enabled) {
    if (config == null || isEmptyObject(config)) {
        return enabled ? "已启用 · 无额外配置" : "已停用"
    }
    let json
    try {
        json = JSON.stringify(config)
    } catch {
        json = String(config)
    }
    if (!json || json === "{}") return enabled ? "已启用 · 无额外配置" : "已停用"
    return json.length <= 88 ? json : `${json.slice(0, 85)}…`
}

function isEmptyObject(value) {
    return typeof value === "object" && !Array.isArray(value) && Object.keys(value).length === 0
}
