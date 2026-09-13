export function moduleShortName(moduleName) {
    const value = String(moduleName || "")
    const unscoped = value.startsWith("@") ? value.slice(value.indexOf("/") + 1) : value
    return unscoped
        .replace(/^cordis:/, "")
        .replace(/^cordis-plugin-/, "")
        .replace(/^dsh-(?:host-|client-)?/, "")
        .replace(/^@/, "")
}

export function formatError(error) {
    if (error == null) return ""
    if (typeof error === "string") return clip(error)
    const message = typeof error.message === "string" && error.message
        ? error.message
        : ""
    const stack = typeof error.stack === "string" ? error.stack.split("\n").slice(0, 4).join("\n") : ""
    if (message && stack && !stack.startsWith(message)) return clip(`${message}\n${stack}`)
    if (message) return clip(message)
    if (stack) return clip(stack)
    try {
        return clip(JSON.stringify(error))
    } catch {
        return clip(String(error))
    }
}

function clip(text) {
    return text.length > 1200 ? `${text.slice(0, 1197)}…` : text
}
