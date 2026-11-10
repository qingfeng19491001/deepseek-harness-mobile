package com.example.dsh.host.repository

/**
 * 远端业务数据访问。由 `session` 调用，不由 `connection` 转交。
 *
 * 阶段 2 只标出包边界。业务代码仍在 `com.example.dsh.dsh`，本阶段不搬迁。
 * 使用 `runtime`、`parser`、`protocol`。
 */
@Suppress("unused")
private object Boundary
