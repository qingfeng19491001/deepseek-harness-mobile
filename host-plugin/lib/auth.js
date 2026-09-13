export function isLoopbackAddress(address) {
    const value = String(address ?? "")
    return value === "127.0.0.1" || value === "::1" || value === "::ffff:127.0.0.1"
}

export function authorizeRequest(req) {
    if (isLoopbackAddress(req?.socket?.remoteAddress)) {
        return { ok: true }
    }
    return {
        ok: false,
        code: "forbidden",
        message: "插件管理只接受本机回环请求。扫码隧道和 SSH 转发都会把 Host 暴露在 127.0.0.1。",
    }
}
