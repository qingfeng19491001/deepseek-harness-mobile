package com.example.dsh.host.parser

/**
 * RPC 业务响应解析。保持纯函数，不持有可变运行时状态，不发请求。
 *
 * 阶段 2 只标出包边界。业务代码仍在 `com.example.dsh.dsh`，本阶段不搬迁。
 */
@Suppress("unused")
private object Boundary
