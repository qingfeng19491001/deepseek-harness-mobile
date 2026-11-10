package com.example.dsh.connection

/**
 * SSH 与凭证、扫码、Relay / SSH 引擎启停、连接状态。
 *
 * 阶段 2 只标出包边界。业务代码仍在 `com.example.dsh.dsh`，本阶段不搬迁。
 * 只建立和管理连接。不负责远端业务数据访问，不持有会话内部数据。
 * 可以把已经建立的通道交给 `host.runtime`。`host.runtime` 不反向依赖本包。
 */
@Suppress("unused")
private object Boundary
