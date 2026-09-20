package com.example.dsh.dsh

import com.tencent.kuikly.core.base.event.Event
import com.tencent.kuikly.core.base.event.EventName
import com.tencent.kuikly.core.base.event.PanGestureParams

/** Port of Kiko `DrawerPhysics`: progress, fling target, and Bezier settle. */
internal object DshDrawerPhysics {
    fun progress(start: Float, displacement: Float, width: Float): Float =
        (start + displacement / width.coerceAtLeast(1f)).coerceIn(0f, 1f)

    fun target(progress: Float, velocity: Float): Float = when {
        velocity > DshDrawerMotion.FLING_VELOCITY -> 1f
        velocity < -DshDrawerMotion.FLING_VELOCITY -> 0f
        progress > DshDrawerMotion.SNAP_THRESHOLD -> 1f
        else -> 0f
    }

    /** Solve x(t) first: the Bezier parameter is not elapsed time. */
    fun ease(time: Float): Float {
        if (time <= 0f) return 0f
        if (time >= 1f) return 1f
        var low = 0f
        var high = 1f
        repeat(24) {
            val t = (low + high) * 0.5f
            val u = 1f - t
            val x = 3f * u * u * t * 0.32f + t * t * t
            if (x < time) low = t else high = t
        }
        val t = (low + high) * 0.5f
        val u = 1f - t
        return 3f * u * u * t * 0.72f + 3f * u * t * t + t * t * t
    }
}

/** Floating-home drawer geometry. Progress 0=closed, 1=open. No drop shadow. */
internal object DshDrawerMotion {
    const val SNAP_THRESHOLD = 0.5f
    const val SCRIM_ALPHA = 0.62f
    const val FLING_VELOCITY = 680f
    const val HOME_MIN_SCALE = 0.96f
    const val HOME_MAX_RADIUS = 32f
    const val SETTLE_MS = 360f
    const val PAN_LOCK_SLOP = 8f
    const val PAN_SOURCE_HOME = 1
    const val PAN_SOURCE_CLOSE = 2

    fun drawerWidth(pageWidth: Float): Float = pageWidth * 0.80f

    fun drawerShift(progress: Float, drawerWidth: Float): Float = (progress - 1f) * drawerWidth

    fun homeScale(progress: Float): Float = 1f - (1f - HOME_MIN_SCALE) * progress

    fun homeShift(progress: Float, pageWidth: Float, drawerWidth: Float): Float =
        (drawerWidth - pageWidth * (1f - HOME_MIN_SCALE) / 2f) * progress
}

internal fun Event.dshFollowPan(handler: (PanGestureParams) -> Unit) {
    register(EventName.PAN.value, { handler(PanGestureParams.decode(it)) }, isSync = true)
}
