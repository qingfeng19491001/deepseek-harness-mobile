package com.example.dsh.base

import com.example.dsh.dsh.DshAppLog
import com.example.dsh.dsh.DshEngineModule
import com.example.dsh.dsh.DshLogLevel
import com.example.dsh.dsh.DshRelayModule
import com.example.dsh.dsh.DshSseModule
import com.example.dsh.dsh.DshThemeModule
import com.example.dsh.dsh.DshWebSocketModule
import com.example.dsh.theme.DshCodeThemePreference
import com.example.dsh.theme.DshSolarClock
import com.example.dsh.theme.DshTheme
import com.example.dsh.theme.DshThemePreference
import com.example.dsh.theme.DshThemeSnapshot
import com.tencent.kuikly.core.log.KLog
import com.tencent.kuikly.core.module.CallbackRef
import com.tencent.kuikly.core.module.Module
import com.tencent.kuikly.core.module.NotifyModule
import com.tencent.kuikly.core.module.SharedPreferencesModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.pager.Pager
import com.tencent.kuikly.core.reactive.handler.*
import com.tencent.kuikly.core.timer.setTimeout
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt

internal abstract class BasePager : Pager() {
    private var systemDark = false

    /**
     * 当前 Pager 的主题镜像。真正的状态源是 [DshTheme]；
     * 这里镜像是为了让 Kuikly observable 能驱动 attr 更新。
     */
    var theme: DshThemeSnapshot by observable(DshTheme.snapshot)
        private set

    private var themeCallbackRef: CallbackRef? = null
    private var logCallbackRef: CallbackRef? = null

    override fun createExternalModules(): Map<String, Module>? {
        val externalModules = hashMapOf<String, Module>()
        externalModules[BridgeModule.MODULE_NAME] = BridgeModule()
        externalModules[DshEngineModule.MODULE_NAME] = DshEngineModule()
        externalModules[DshRelayModule.MODULE_NAME] = DshRelayModule()
        externalModules[DshSseModule.MODULE_NAME] = DshSseModule()
        externalModules[DshWebSocketModule.MODULE_NAME] = DshWebSocketModule()
        externalModules[DshThemeModule.MODULE_NAME] = DshThemeModule()
        return externalModules
    }

    override fun created() {
        super.created()
        systemDark = pageData.params.optBoolean(IS_NIGHT_MODE_KEY)
        val stored = readPref(DshTheme.PREF_KEY)
        val storedCode = readPref(DshTheme.CODE_PREF_KEY)
        val storedContrast = readPref(DshTheme.HIGH_CONTRAST_KEY) == "1"
        DshTheme.bootstrap(
            stored = stored,
            systemDark = systemDark,
            storedCodeTheme = storedCode,
            storedHighContrast = storedContrast,
            solarNight = currentSolarNight(),
        )
        theme = DshTheme.snapshot
        themeCallbackRef = notifyModule().addNotify(DshTheme.EVENT) { theme = DshTheme.snapshot }
        DshAppLog.publisher = { notifyModule().postNotify(DshAppLog.EVENT, JSONObject()) }
        logCallbackRef = notifyModule().addNotify(DshAppLog.EVENT) { onAppLogChanged() }
        ingestPersistedCrash()
        DshAppLog.record(DshLogLevel.INFO, "app.start", "pager-created")
        scheduleSolarTick()
    }

    protected open fun onAppLogChanged() {}

    override fun pageWillDestroy() {
        themeCallbackRef?.let { notifyModule().removeNotify(DshTheme.EVENT, it) }
        themeCallbackRef = null
        logCallbackRef?.let { notifyModule().removeNotify(DshAppLog.EVENT, it) }
        logCallbackRef = null
        super.pageWillDestroy()
    }

    private fun ingestPersistedCrash() {
        val crash = readPref(DshAppLog.CRASH_KEY) ?: return
        if (crash.isBlank()) return
        DshAppLog.record(DshLogLevel.ERROR, "crash", crash.take(800))
        persistOrToast(DshAppLog.CRASH_KEY, "")
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).toast("上次异常已写入日志中心")
    }

    override fun themeDidChanged(data: JSONObject) {
        super.themeDidChanged(data)
        systemDark = data.optBoolean(IS_NIGHT_MODE_KEY)
        if (DshTheme.updateSystemDark(systemDark)) {
            publishTheme()
        }
    }

    fun setThemePreference(preference: DshThemePreference) {
        persistOrToast(DshTheme.PREF_KEY, preference.storageValue)
        DshTheme.setPreference(preference)
        publishTheme()
    }

    fun setCodeThemePreference(preference: DshCodeThemePreference) {
        persistOrToast(DshTheme.CODE_PREF_KEY, preference.storageValue)
        DshTheme.setCodeTheme(preference)
        publishTheme()
    }

    fun setHighContrast(enabled: Boolean) {
        persistOrToast(DshTheme.HIGH_CONTRAST_KEY, if (enabled) "1" else "0")
        DshTheme.setHighContrast(enabled)
        publishTheme()
    }

    override fun isNightMode(): Boolean = DshTheme.snapshot.isDark

    // 不开启调试UI模式
    override fun debugUIInspector(): Boolean {
        return false
    }

    private fun publishTheme() {
        theme = DshTheme.snapshot
        notifyModule().postNotify(DshTheme.EVENT, JSONObject())
        runCatching {
            acquireModule<DshThemeModule>(DshThemeModule.MODULE_NAME).applyNativeChrome(DshTheme.snapshot.isDark)
        }.onFailure { KLog.e(TAG, "applyNativeChrome failed: ${it.message}") }
    }

    private fun persistOrToast(key: String, value: String) {
        val persisted = runCatching {
            sharedPreferences().setItem(key, value)
            true
        }.onFailure { KLog.e(TAG, "persist $key failed: ${it.message}") }
            .getOrDefault(false)
        if (!persisted) {
            acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)
                .toast("设置未能保存，下次启动可能恢复默认")
        }
    }

    private fun readPref(key: String): String? =
        runCatching { sharedPreferences().getItem(key) }
            .onFailure { KLog.e(TAG, "read $key failed: ${it.message}") }
            .getOrNull()
            ?.ifEmpty { null }

    private fun currentSolarNight(): Boolean {
        val now = Clock.System.now()
        val offsetMinutes = TimeZone.currentSystemDefault().offsetAt(now).totalSeconds / 60
        return DshSolarClock.isNight(now.toEpochMilliseconds(), offsetMinutes)
    }

    private fun scheduleSolarTick() {
        setTimeout(pagerId, SOLAR_TICK_MS) {
            if (DshTheme.snapshot.preference == DshThemePreference.AUTO &&
                DshTheme.updateSolarNight(currentSolarNight())
            ) {
                publishTheme()
            }
            scheduleSolarTick()
        }
    }

    private fun sharedPreferences(): SharedPreferencesModule =
        acquireModule(SharedPreferencesModule.MODULE_NAME)

    private fun notifyModule(): NotifyModule = acquireModule(NotifyModule.MODULE_NAME)

    companion object {
        const val IS_NIGHT_MODE_KEY = "isNightMode"
        private const val TAG = "BasePager"
        private const val SOLAR_TICK_MS = 60_000
    }
}
