package com.example.dsh.host

/**
 * 协议、解析、在已打开通道上的 RPC、远端仓储。
 *
 * 子包是 `protocol`、`parser`、`runtime`、`repository`。
 * 阶段 2 只标出包边界。业务代码仍在 `com.example.dsh.dsh`，本阶段不搬迁。
 * 不依赖 `ui`、`session`、`connection`，不接触 Pager。
 */
@Suppress("unused")
private object Boundary
