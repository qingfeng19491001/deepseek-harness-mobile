package com.example.dsh.session

/**
 * 首页会话状态组合、会话生命周期和消息流编排。
 *
 * 阶段 2 只标出包边界。业务代码仍在 `com.example.dsh.dsh`，本阶段不搬迁。
 * 可以调用 `host.repository` 和 `store`。不创建 SSH / Relay 引擎，不解析原始 RPC，
 * 不持有插件和日志的内部状态，不绘制界面。
 * 能脱离 Pager 运行的编排不接触 `PagerScope`。
 */
@Suppress("unused")
private object Boundary
