package com.example.dsh.connection

import com.example.dsh.dsh.DshConnectionMode

/**
 * 连接 generation。一次连接尝试一个值，用来挡住上一次 SSH / Relay 回调。
 * 它不表示宿主 WebSocket 通道，通道 generation 仍在 `DshHostConnectionRuntime`。
 */
internal class DshConnectionCoordinator {
    private var generation = 0L
    private var activeMode: DshConnectionMode? = null

    fun begin(mode: DshConnectionMode): Long {
        activeMode = mode
        generation += 1
        return generation
    }

    fun stop() {
        generation += 1
        activeMode = null
    }

    fun accepts(callbackGeneration: Long, callbackMode: DshConnectionMode): Boolean =
        callbackGeneration == generation && activeMode == callbackMode

    fun isActive(mode: DshConnectionMode): Boolean = activeMode == mode

    fun activeModeOr(fallback: DshConnectionMode): DshConnectionMode = activeMode ?: fallback
}
