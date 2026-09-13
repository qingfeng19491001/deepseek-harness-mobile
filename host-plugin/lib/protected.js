const PROTECTED_RULES = [
    { pattern: /dsh-mobile-plugin-admin/, reason: "这是手机插件管理自身，停用后将失去配置、错误和启停能力" },
    { pattern: /dsh-scan-remote/, reason: "连通性插件，停用会导致扫码连接中断" },
    { pattern: /plugin-inventory/, reason: "Host 插件清单服务，停用后将无法读取插件状态" },
    { pattern: /apiproxy|api-proxy/, reason: "Host API 代理，停用会中断 App 与 Host 的 RPC" },
    { pattern: /api-gateway|typert/, reason: "Host API 网关，停用会中断远程方法调用" },
    { pattern: /webserver|web-server/, reason: "Host WebServer，停用会关闭 HTTP 入口" },
    { pattern: /cordis-plugin-loader|plugin-loader/, reason: "插件加载器，停用会拆掉整个 Host 组合" },
]

export function protectionFor(moduleName, entryId, selfNames = []) {
    const haystack = `${moduleName ?? ""} ${entryId ?? ""}`
    for (const name of selfNames) {
        if (name && haystack.includes(name)) {
            return { protected: true, reason: "这是手机插件管理自身，停用后将失去配置、错误和启停能力" }
        }
    }
    for (const rule of PROTECTED_RULES) {
        if (rule.pattern.test(haystack)) return { protected: true, reason: rule.reason }
    }
    return { protected: false, reason: "" }
}
