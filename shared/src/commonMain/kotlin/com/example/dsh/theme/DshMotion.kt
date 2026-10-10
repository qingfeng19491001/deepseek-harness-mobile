package com.example.dsh.theme

import com.tencent.kuikly.core.base.Animation

/**
 * 动效令牌。页面只允许通过这里取时长与曲线，不得再散写 `Animation.xxx(0.24f)`。
 * 四档：微交互（按下、图标切换）、小组件（展开、Chip）、浮层进退场、页面级。
 * [reduced] 打开后所有动效退化为极短的线性过渡，保留状态变化但不再位移。
 */
internal object DshMotion {
    const val PRESS_MS = 90
    const val RELEASE_MS = 160
    const val MICRO_MS = 120
    const val SMALL_MS = 200
    const val ENTER_MS = 300
    const val EXIT_MS = 200
    const val PAGE_MS = 340

    const val PREF_KEY = "reduce_motion"

    /** 由 BasePager 在启动与切换时写入；纯数据，不是 observable。 */
    var reduced: Boolean = false

    const val PRESSED_OPACITY = 0.55f
    const val DIALOG_ENTER_SCALE = 0.94f
    const val MESSAGE_ENTER_OFFSET = 10f
    const val PANEL_SWAP_OFFSET = 6f

    fun press(): Animation = Animation.easeOut(seconds(PRESS_MS))
    fun release(): Animation = Animation.easeOut(seconds(RELEASE_MS))
    fun micro(): Animation = Animation.easeInOut(seconds(MICRO_MS))
    fun small(): Animation = Animation.easeInOut(seconds(SMALL_MS))

    /** 浮层进场：减速，略带弹性但不过冲。 */
    fun enter(): Animation = if (reduced) {
        Animation.linear(REDUCED_S)
    } else {
        Animation.springEaseOut(seconds(ENTER_MS), 0.92f, 0f)
    }

    /** 浮层退场：加速离开，比进场短。 */
    fun exit(): Animation = Animation.easeIn(seconds(EXIT_MS))

    fun page(): Animation = if (reduced) {
        Animation.linear(REDUCED_S)
    } else {
        Animation.springEaseOut(seconds(PAGE_MS), 0.95f, 0f)
    }

    /**
     * 供 `attr { animation(DshMotion.presence(shown), observable) }` 使用。
     * Kuikly 在属性变化时取的是上一轮 attr 登记的动画，所以这里返回的是
     * “离开当前状态”要用的曲线：已展示时登记退场，未展示时登记进场。
     */
    fun presence(shown: Boolean): Animation = if (shown) exit() else enter()

    fun pagePresence(shown: Boolean): Animation = if (shown) exit() else page()

    /** 遮罩只做透明度，进退都用减速曲线，退场时先于面板变淡。 */
    fun scrimPresence(shown: Boolean): Animation =
        Animation.easeOut(seconds(if (shown) EXIT_MS else ENTER_MS))

    /** 退场动画结束后再卸载节点；留一点余量给渲染层回调。 */
    val exitUnmountMs: Int get() = (if (reduced) REDUCED_MS else EXIT_MS) + 40

    fun durationMs(ms: Int): Int = if (reduced) REDUCED_MS else ms

    private fun seconds(ms: Int): Float = if (reduced) REDUCED_S else ms / 1000f

    private const val REDUCED_MS = 60
    private const val REDUCED_S = REDUCED_MS / 1000f
}
