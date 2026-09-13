# dsh-mobile-plugin-admin

给 DeepSeek Harness Mobile 用的电脑侧配套插件。它不替代官方只读的 `pluginInventory/list`，而是补上官方没有的配置摘要、失败原因，以及安全的启用 / 停用 / 重载。

**不要改官方 DSH 源码。** 把这个插件装进电脑上正在跑的官方 Host，和安装 `dsh-scan-remote` 是同一类操作。

## 安装

在本仓库的 `host-plugin/` 目录：

```bash
npx @deepseek-ai/dsh plugin --profile web add "link:$(pwd)"
```

或在电脑上指向已克隆的 mobile 仓库：

```bash
npx @deepseek-ai/dsh plugin --profile web add "link:/absolute/path/to/deepseek-harness-mobile/host-plugin"
```

然后重启 `npx @deepseek-ai/dsh web`。App 侧栏打开「插件」后，标题下应显示「可管理」。

未安装时 App 仍会拉官方清单，只是没有配置、错误详情和启停按钮。

## 协议

见仓库内 [docs/dsh-mobile-plugin-protocol.md](../docs/dsh-mobile-plugin-protocol.md)。入口是 `POST /dsh-mobile/rpc`。

## 安全策略

下列插件不能从手机启停：管理插件自身、`dsh-scan-remote`、webserver / apiproxy / gateway / plugin-inventory / loader。写操作必须带 `confirm: true`。HTTP 入口只接受回环请求。不做安装和卸载。
