package com.example.dsh.connection

import com.example.dsh.base.bridgeModule
import com.example.dsh.dsh.DshConnectionMode
import com.example.dsh.dsh.DshEngineModule
import com.example.dsh.dsh.DshHostRepository
import com.example.dsh.dsh.DshHostRuntimePhase
import com.example.dsh.dsh.DshHostRuntimeState
import com.example.dsh.dsh.DshLocalStore
import com.example.dsh.dsh.DshRelayModule
import com.example.dsh.dsh.DshRelayPhase
import com.example.dsh.dsh.DshRemoteProfile
import com.example.dsh.dsh.DshRepository
import com.example.dsh.dsh.DshSessionScope
import com.example.dsh.dsh.DshSseModule
import com.example.dsh.dsh.DshSshConfig
import com.example.dsh.dsh.DshSshPhase
import com.example.dsh.dsh.DshWebSocketModule
import com.example.dsh.dsh.isReconnectLabel
import com.example.dsh.dsh.supportsRelayBridge
import com.example.dsh.host.repository.DshRemoteRepository
import com.example.dsh.host.runtime.DshHostConnection
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.pager.Pager
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.timer.setTimeout

/**
 * SSH / Relay 连接生命周期。宿主 WebSocket 仍由 [com.example.dsh.host.runtime.DshHostConnectionRuntime] 持有。
 */
internal class DshConnection(
    private val pager: Pager,
    private val events: DshConnectionEvents,
    private val localStore: () -> DshLocalStore?,
) {
    var connectionMode by pager.observable(DshConnectionMode.RELAY)
    var remoteProfileId by pager.observable(DshSessionScope.DEFAULT_REMOTE_PROFILE_ID)
    var sshHost by pager.observable("")
    var sshUser by pager.observable("")
    var sshPort by pager.observable("22")
    var sshDshPort by pager.observable("3080")
    var sshKeyId by pager.observable("")
    var sshFingerprint by pager.observable("")
    var sshKeyLabel by pager.observable("未导入私钥")
    var sshKeyPassphrase by pager.observable("")
    var sshSettingsVisible by pager.observable(false)
    var sshSettingsBusy by pager.observable(false)
    var sshSettingsError by pager.observable("")
    var connectionLabel by pager.observable("本地内核启动中")
    var apiKeyDraft by pager.observable("")
    var credentialSetupVisible by pager.observable(false)
    var credentialSetupBusy by pager.observable(false)
    var credentialSetupError by pager.observable("")
    var credentialSetupTitle by pager.observable("添加一个 API Key 开始使用")

    var repository: DshRepository? = null
    var engineReady = false
    var pendingApiKey = ""

    val sshMode: Boolean
        get() = connectionMode == DshConnectionMode.SSH

    private var relayEngineEndpoint = ""
    private var engineModule: DshEngineModule? = null
    private val coordinator = DshConnectionCoordinator()
    private var disposed = false

    fun isActive(): Boolean = !disposed && coordinator.isActive(connectionMode)

    fun dispose() {
        if (disposed) return
        disposed = true
        stop()
    }

    fun start() {
        if (disposed) return
        val generation = coordinator.begin(connectionMode)
        when (connectionMode) {
            DshConnectionMode.SSH -> startSshEngine(generation)
            DshConnectionMode.RELAY -> startRelayEngine(generation)
            DshConnectionMode.LOCAL -> connectionLabel = "本地模式已独立为 DSH Local App"
        }
    }

    fun loadSshConfig() {
        val profile = runCatching { localStore()?.loadRemoteProfile() }.getOrNull()
        sshHost = profile?.host.orEmpty()
        sshUser = profile?.username.orEmpty()
        sshPort = profile?.sshPort?.toString() ?: "22"
        sshDshPort = profile?.remoteDshPort?.toString() ?: "3080"
        sshKeyId = profile?.keyId.orEmpty()
        sshFingerprint = profile?.hostFingerprint.orEmpty()
        sshKeyLabel = if (sshKeyId.isEmpty()) "未导入私钥" else "已导入私钥"
    }

    fun stop() {
        val mode = coordinator.activeModeOr(connectionMode)
        coordinator.stop()
        (repository as? DshRemoteRepository)?.stop()
        repository = null
        events.clearGoalAndStream()
        when (mode) {
            DshConnectionMode.RELAY -> pager.acquireModule<DshRelayModule>(DshRelayModule.MODULE_NAME).disconnect()
            DshConnectionMode.SSH -> engineModule?.stopSsh()
            DshConnectionMode.LOCAL -> engineModule?.stop()
        }
        engineReady = false
    }

    fun connectLocalEngine(apiKey: String) {
        connectionLabel = "本地内核启动中"
        repository = DshHostRepository(
            network = pager.acquireModule(NetworkModule.MODULE_NAME),
            sse = pager.acquireModule(DshSseModule.MODULE_NAME),
            connection = DshHostConnection(LOCAL_ENGINE_URL),
            pagerId = pager.pagerId,
        )
        syncLocalCredential(apiKey, 0)
    }

    fun saveDeepSeekApiKey() {
        val key = apiKeyDraft.trim()
        when {
            key.isEmpty() -> {
                credentialSetupError = "请输入 API Key 后继续。"
                return
            }
            key.any { it.code !in 0x21..0x7E } -> {
                credentialSetupError = "API Key 格式错误，请检查后重试。"
                return
            }
        }
        credentialSetupBusy = true
        credentialSetupError = ""
        if (sshMode) {
            val hostRepository = repository
            if (hostRepository == null) {
                credentialSetupBusy = false
                credentialSetupError = "远程 DSH 尚未就绪"
                return
            }
            hostRepository.saveDeepSeekApiKey(key, {
                setTimeout(pager.pagerId, 0) {
                    if (disposed) return@setTimeout
                    events.clearApiKeyInput()
                    credentialSetupBusy = false
                    updateCredentialSetupVisibility(false)
                    events.dismissKeyboard()
                    connectionLabel = "远程 DSH 已更新"
                    events.loadRepository(null)
                }
            }, { error ->
                setTimeout(pager.pagerId, 0) {
                    if (disposed) return@setTimeout
                    credentialSetupBusy = false
                    credentialSetupError = "无法修改电脑端 DSH：$error"
                }
            })
            return
        }
        val saved = runCatching { localStore()?.saveApiKey(key) }
        if (saved.isFailure || localStore() == null) {
            credentialSetupBusy = false
            credentialSetupError = saved.exceptionOrNull()?.message ?: "本地数据库不可用"
            return
        }
        events.clearApiKeyInput()
        credentialSetupBusy = false
        credentialSetupError = ""
        updateCredentialSetupVisibility(false)
        events.dismissKeyboard()
        pendingApiKey = key
        if (engineReady) {
            connectLocalEngine(key)
        } else {
            connectionLabel = "等待本地内核启动"
        }
    }

    fun openCredentialSettings() {
        events.dismissKeyboard()
        credentialSetupTitle = if (sshMode) "修改电脑端 DSH 的 API Key" else "设置 DeepSeek API Key"
        credentialSetupError = ""
        apiKeyDraft = pendingApiKey
        updateCredentialSetupVisibility(true)
    }

    fun closeCredentialSettings() {
        events.dismissKeyboard()
        updateCredentialSetupVisibility(false)
    }

    fun openConnectionSettings(preserveError: Boolean = false) {
        events.dismissKeyboard()
        events.closeSessionDrawer()
        events.hideAppSettingsHub()
        if (!preserveError) sshSettingsError = ""
        updateSshSettingsVisibility(true)
    }

    fun updateSshSettingsVisibility(visible: Boolean) {
        sshSettingsVisible = visible
        if (pager.pageData.isAndroid || pager.pageData.isIOS) {
            pager.bridgeModule.setSystemBarsDimmed(visible)
        }
    }

    fun updateCredentialSetupVisibility(visible: Boolean) {
        credentialSetupVisible = visible
        if (pager.pageData.isAndroid || pager.pageData.isIOS) {
            pager.bridgeModule.setSystemBarsDimmed(visible)
        }
    }

    fun setConnectionMode(useSsh: Boolean) {
        connectionMode = if (useSsh) DshConnectionMode.SSH else DshConnectionMode.RELAY
        sshSettingsError = ""
    }

    fun pickSshKey() {
        pager.bridgeModule.pickSshKey { uri ->
            if (disposed || uri.isEmpty()) return@pickSshKey
            sshSettingsBusy = true
            pager.bridgeModule.importSshKey(uri) { keyId ->
                setTimeout(pager.pagerId, 0) {
                    if (disposed) return@setTimeout
                    sshSettingsBusy = false
                    if (keyId.isEmpty()) {
                        sshSettingsError = "无法导入 SSH 私钥"
                    } else {
                        sshKeyId = keyId
                        sshKeyLabel = "已导入私钥"
                        sshSettingsError = ""
                    }
                }
            }
        }
    }

    fun trustSshFingerprint() {
        if (sshFingerprint.isBlank()) return
        pager.acquireModule<DshEngineModule>(DshEngineModule.MODULE_NAME).trustSshFingerprint(sshFingerprint)
        runCatching {
            localStore()?.saveRemoteProfile(DshRemoteProfile(
                host = sshHost.trim(),
                sshPort = sshPort.toIntOrNull() ?: 22,
                username = sshUser.trim(),
                remoteDshPort = sshDshPort.toIntOrNull() ?: 3080,
                keyId = sshKeyId,
                hostFingerprint = sshFingerprint,
            ))
        }
        sshSettingsError = "正在使用已确认的主机指纹连接"
    }

    fun saveConnectionSettings() {
        if (sshMode) {
            val port = sshPort.toIntOrNull()
            val dshPort = sshDshPort.toIntOrNull()
            when {
                sshHost.isBlank() -> sshSettingsError = "请输入 SSH 主机地址"
                sshUser.isBlank() -> sshSettingsError = "请输入 SSH 用户名"
                port == null || port !in 1..65535 -> sshSettingsError = "SSH 端口无效"
                dshPort == null || dshPort !in 1..65535 -> sshSettingsError = "远程 DSH 端口无效"
                sshKeyId.isBlank() -> sshSettingsError = "请先导入 SSH 私钥"
                else -> {
                    runCatching {
                        localStore()?.saveRemoteProfile(DshRemoteProfile(
                            host = sshHost.trim(),
                            sshPort = port,
                            username = sshUser.trim(),
                            remoteDshPort = dshPort,
                            keyId = sshKeyId,
                            hostFingerprint = sshFingerprint,
                        ))
                    }
                    runCatching { localStore()?.saveLastConnectionMode(DshConnectionMode.SSH) }
                    updateSshSettingsVisibility(false)
                    stop()
                    openSetupPage()
                }
            }
        } else {
            runCatching { localStore()?.saveLastConnectionMode(DshConnectionMode.RELAY) }
            updateSshSettingsVisibility(false)
            stop()
            openSetupPage()
        }
    }

    private fun startRelayEngine(generation: Long) {
        if (!pager.pageData.supportsRelayBridge) {
            connectionLabel = "扫码连接目前仅支持 Android、iOS 和 HarmonyOS"
            return
        }
        connectionLabel = "正在连接扫码电脑"
        pager.acquireModule<DshRelayModule>(DshRelayModule.MODULE_NAME).connect { state ->
            if (!accepts(generation, DshConnectionMode.RELAY)) return@connect
            when (state.phase) {
                DshRelayPhase.READY -> {
                    if (state.localPort <= 0 || state.localToken.isEmpty()) return@connect
                    val endpoint = "http://127.0.0.1:${state.localPort}"
                    engineReady = true
                    connectionLabel = state.message.ifEmpty { "扫码隧道已连接" }
                    if (state.hostId.isNotEmpty()) remoteProfileId = state.hostId
                    if (relayEngineEndpoint == endpoint && repository != null) return@connect
                    relayEngineEndpoint = endpoint
                    connectRemoteEngine(endpoint, state.localToken)
                }
                DshRelayPhase.ERROR -> {
                    engineReady = false
                    relayEngineEndpoint = ""
                    connectionLabel = state.message.ifEmpty { "扫码连接失败" }
                }
                DshRelayPhase.RECONNECTING -> {
                    relayEngineEndpoint = ""
                    (repository as? DshRemoteRepository)?.stop()
                    repository = null
                    connectionLabel = "扫码连接重试中"
                    events.syncTurnStatus()
                }
                DshRelayPhase.STOPPED -> {
                    engineReady = false
                    relayEngineEndpoint = ""
                    (repository as? DshRemoteRepository)?.stop()
                    repository = null
                    connectionLabel = "扫码连接已断开"
                }
                else -> {
                    if (state.localPort <= 0) relayEngineEndpoint = ""
                    connectionLabel = state.message.ifEmpty { "正在建立扫码隧道" }
                }
            }
        }
    }

    private fun startSshEngine(generation: Long) {
        if (sshHost.isBlank() || sshUser.isBlank() || sshKeyId.isBlank()) {
            connectionLabel = "请配置 SSH 连接"
            openConnectionSettings()
            return
        }
        val module = pager.acquireModule<DshEngineModule>(DshEngineModule.MODULE_NAME)
        engineModule = module
        connectionLabel = "正在连接 SSH"
        module.startSsh(DshSshConfig(
            host = sshHost,
            port = sshPort.toIntOrNull() ?: 22,
            username = sshUser,
            remoteDshPort = sshDshPort.toIntOrNull() ?: 3080,
            keyId = sshKeyId,
            hostFingerprint = sshFingerprint,
            keyPassphrase = sshKeyPassphrase,
        )) { state ->
            if (!accepts(generation, DshConnectionMode.SSH)) return@startSsh
            when (state.phase) {
                DshSshPhase.FINGERPRINT_REQUIRED -> {
                    sshFingerprint = state.message
                    sshSettingsError = "首次连接需要确认主机指纹：${state.message}"
                    openSetupPage()
                }
                DshSshPhase.READY -> {
                    engineReady = true
                    connectionLabel = "正在检查远程 DSH"
                    connectRemoteEngine("http://127.0.0.1:${state.localPort}")
                }
                DshSshPhase.RECONNECTING -> connectionLabel = "SSH 重连中"
                DshSshPhase.ERROR -> {
                    engineReady = false
                    connectionLabel = "SSH 连接失败"
                    sshSettingsError = state.message
                    openSetupPage()
                }
                DshSshPhase.STOPPED -> {
                    engineReady = false
                    repository = null
                    connectionLabel = "SSH 已断开"
                }
                else -> connectionLabel = state.message.ifEmpty { "正在连接 SSH" }
            }
        }
    }

    private fun connectRemoteEngine(baseUrl: String, token: String = "") {
        (repository as? DshRemoteRepository)?.stop()
        val opened = DshRemoteRepository(
            network = pager.acquireModule(NetworkModule.MODULE_NAME),
            webSocket = pager.acquireModule(DshWebSocketModule.MODULE_NAME),
            connection = DshHostConnection(baseUrl, token),
            pagerId = pager.pagerId,
            onState = { state -> handleHostRuntimeState(state) },
            onQueueSnapshot = { sessionId -> if (!disposed) events.onQueueSnapshot(sessionId) },
            onJobsSnapshot = { sessionId -> if (!disposed) events.onJobsSnapshot(sessionId) },
            onSessionStatus = { sessionId, running -> if (!disposed) events.onSessionStatus(sessionId, running) },
            onProjection = { sessionId, key, value, seq ->
                if (!disposed) events.onProjection(sessionId, key, value, seq)
            },
            onSessionEvent = { sessionId, event -> if (!disposed) events.onSessionEvent(sessionId, event) },
            onRemoteEvent = { event -> if (!disposed) events.onRemoteEvent(event) },
            onArchivedSessionsChanged = { if (!disposed) events.onArchivedSessionsChanged() },
            onPendingInteraction = { sessionId -> if (!disposed) events.onPendingInteraction(sessionId) },
        )
        repository = opened
        events.loadRepository(events.activeSessionId())
    }

    private fun handleHostRuntimeState(state: DshHostRuntimeState) {
        if (disposed || !coordinator.isActive(connectionMode)) return
        val wasReconnecting = isReconnectLabel(connectionLabel)
        connectionLabel = when (state.phase) {
            DshHostRuntimePhase.CONNECTING -> "正在打开远程事件流"
            DshHostRuntimePhase.HOST_HANDSHAKE -> "正在检查远程 DSH"
            DshHostRuntimePhase.SYNCING -> "正在同步远程会话"
            DshHostRuntimePhase.READY -> "远程 DSH 已就绪"
            DshHostRuntimePhase.RECONNECTING -> reconnectLabel()
            DshHostRuntimePhase.ERROR -> "远程 DSH 连接失败"
            DshHostRuntimePhase.STOPPED -> "远程 DSH 已停止"
            DshHostRuntimePhase.DISCONNECTED -> "等待远程连接"
        }
        if (state.phase == DshHostRuntimePhase.READY && wasReconnecting) {
            events.loadRepository(events.activeSessionId())
        }
        if (state.phase == DshHostRuntimePhase.READY) {
            events.probeSessionLifecycle()
        }
        events.syncTurnStatus()
    }

    private fun syncLocalCredential(apiKey: String, attempt: Int) {
        val hostRepository = repository ?: return
        hostRepository.saveDeepSeekApiKey(apiKey, {
            if (disposed) return@saveDeepSeekApiKey
            connectionLabel = "已连接"
            events.loadRepository(null)
        }, { error ->
            if (disposed) return@saveDeepSeekApiKey
            if (attempt < ENGINE_CONNECT_RETRIES) {
                connectionLabel = "本地内核启动中"
                setTimeout(pager.pagerId, ENGINE_RETRY_DELAY_MS) {
                    if (!disposed) syncLocalCredential(apiKey, attempt + 1)
                }
            } else {
                connectionLabel = "内核启动失败"
                events.onLocalEngineError(error)
            }
        })
    }

    private fun openSetupPage() {
        if (disposed) return
        events.openConnectionSetup()
    }

    private fun accepts(generation: Long, mode: DshConnectionMode): Boolean =
        !disposed && coordinator.accepts(generation, mode)

    private fun reconnectLabel(): String = when (connectionMode) {
        DshConnectionMode.SSH -> "远程连接重建中"
        DshConnectionMode.RELAY -> "扫码连接重建中"
        DshConnectionMode.LOCAL -> "本地 DSH 连接重建中"
    }

    private companion object {
        const val LOCAL_ENGINE_URL = "http://127.0.0.1:3080"
        const val ENGINE_CONNECT_RETRIES = 60
        const val ENGINE_RETRY_DELAY_MS = 1_000
    }
}
