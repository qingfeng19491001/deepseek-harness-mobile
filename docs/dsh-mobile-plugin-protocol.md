# DSH Mobile 插件接入协议

这份协议让手机 App 发现电脑 DSH 上的配套插件能力。它**不是**官方 Harness 的一部分，也不改 `deepseek-ai/deepseek-harness`。

官方 `pluginInventory/list` 仍然只读，只返回 `entryId`、`moduleName`、`enabled`、`fiberPhase`。配置摘要、失败原因、启停和重载由本仓库 `host-plugin/` 提供。

## 1. 运输

配套插件在 Host 上注册独立前缀，避免冒充官方 `/api/{method}`：

```text
POST {baseUrl}/dsh-mobile/rpc
Content-Type: application/json
Authorization: Bearer <token>   // 与 Host 会话相同，token 非空时
```

请求信封与 App 现有 Host RPC 一致：

```json
{
  "type": "client-request",
  "rpcId": "dsh-g1-12",
  "method": "dsh.mobile.capabilities",
  "payload": {}
}
```

成功：

```json
{
  "result": { "ok": true, "value": {} }
}
```

失败：

```json
{
  "result": {
    "ok": false,
    "error": { "code": "not-found", "message": "..." }
  }
}
```

未安装配套插件时该路径返回 HTTP 404，或被 Host 的 SPA 回退成 HTML。App 必须回退到官方 `pluginInventory/list`，并隐藏所有写操作。

配套插件只接受来自回环地址（`127.0.0.1` / `::1`）的请求。扫码隧道和 SSH 本地转发都把 Host 露在手机或电脑的 loopback 上；不要把 DSH 直接绑到局域网网卡。

## 2. 能力发现

`method`: `dsh.mobile.capabilities`

```json
{
  "protocol": "dsh-mobile-plugin/v1",
  "features": ["plugin.inventory", "plugin.control"],
  "control": {
    "actions": ["enable", "disable", "reload"],
    "confirmRequired": true
  }
}
```

没有出现在 `features` 里的能力，App 不得展示对应按钮。

## 3. 清单

`method`: `dsh.plugin.inventory`

返回当前 Loader 快照。`config` 已脱敏。`fiberPhase` 与官方词汇相同，缺活根 Fiber 时为 `null`。

```json
{
  "protocol": "dsh-mobile-plugin/v1",
  "writable": true,
  "entries": [
    {
      "entryId": "scan-remote",
      "moduleName": "@yukiykchen/dsh-scan-remote",
      "displayName": "scan-remote",
      "enabled": true,
      "fiberPhase": "active",
      "configSummary": "{\"relay\":\"http://127.0.0.1:8787\"}",
      "config": { "relay": "http://127.0.0.1:8787" },
      "error": null,
      "protected": true,
      "protectReason": "连通性插件，停用会导致扫码连接中断"
    }
  ],
  "agentPresets": []
}
```

`dsh.plugin.get` 的 payload 为 `{ "entryId": "..." }`，返回单条同样形状的对象。

## 4. 安全控制

`method`: `dsh.plugin.control`

```json
{
  "entryId": "example",
  "action": "enable",
  "confirm": true
}
```

`action` 仅 `enable` | `disable` | `reload`。`confirm` 必须为 `true`，否则返回 `confirm-required`。

拒绝条件：

- 自身管理插件
- 连通性插件（如 `dsh-scan-remote`）
- Host 核心（webserver、apiproxy、gateway、plugin-inventory、loader）
- 未声明 `plugin.control`

不做安装、卸载或改配置写入。

## 5. 官方回退

当 `/dsh-mobile/rpc` 不可用时，App 调用官方 Typert Remote：

```text
POST {baseUrl}/api/pluginInventory/list
```

body 优先 `{ "args": {} }`；若 Host 仍走 ApiProxy 信封，再试 `pluginInventory/list` 的 `client-request`。

官方快照 `writable` 恒为 `false`。`failed` 条目若无错误文本，App 显示可读占位说明，而不是空白。
