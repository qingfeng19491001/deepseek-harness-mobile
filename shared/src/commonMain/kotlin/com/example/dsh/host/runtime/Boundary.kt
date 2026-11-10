package com.example.dsh.host.runtime

/**
 * 在已经打开的通道上运行宿主 RPC，并用运行时 generation 丢弃旧通道回调。
 *
 * 阶段 2 只标出包边界。业务代码仍在 `com.example.dsh.dsh`，本阶段不搬迁。
 * 依赖 `protocol`。不依赖 `connection`，不启动 SSH / Relay，不持有会话状态。
 */
@Suppress("unused")
private object Boundary
