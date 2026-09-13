package com.example.dsh.theme

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

/**
 * 按当地太阳高度判断是否已入夜（民用晨昏线，太阳高度 -0.833°）。
 * 默认纬度取东经附近常见纬度；测试可注入偏移与纬度。
 */
internal object DshSolarClock {
    fun isNight(
        epochMillis: Long,
        utcOffsetMinutes: Int = 480,
        latitudeDeg: Double = 31.2304,
    ): Boolean {
        val localMillis = epochMillis + utcOffsetMinutes * 60_000L
        val dayMs = 86_400_000L
        var dayOfYear = ((localMillis / dayMs + 4) % 365).toInt()
        if (dayOfYear < 0) dayOfYear += 365
        dayOfYear += 1
        val gamma = 2.0 * PI / 365.0 * (dayOfYear - 1)
        val decl = 0.006918 -
            0.399912 * cos(gamma) + 0.070257 * sin(gamma) -
            0.006758 * cos(2 * gamma) + 0.000907 * sin(2 * gamma)
        val lat = latitudeDeg * PI / 180.0
        val cosHa = (cos(90.833 * PI / 180.0) - sin(lat) * sin(decl)) / (cos(lat) * cos(decl))
        if (cosHa >= 1.0) return true
        if (cosHa <= -1.0) return false
        val haHours = acos(cosHa) * 12.0 / PI
        var minutes = ((localMillis / 60_000L) % 1440L).toInt()
        if (minutes < 0) minutes += 1440
        val hour = minutes / 60.0
        return hour < (12.0 - haHours) || hour >= (12.0 + haHours)
    }
}
