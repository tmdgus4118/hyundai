package io.whatsthat.car.domain

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * 수 km 범위의 계산만 하므로 위도 기준 등장방형(equirectangular) 근사로 충분하다.
 * 모든 좌표는 기준점(origin)으로부터의 동(x)/북(y) 미터 평면으로 변환해 계산한다.
 */
object GeoMath {
    private const val EARTH_R = 6_371_000.0

    data class Vec(val x: Double, val y: Double) {
        operator fun minus(o: Vec) = Vec(x - o.x, y - o.y)
        operator fun plus(o: Vec) = Vec(x + o.x, y + o.y)
        operator fun times(k: Double) = Vec(x * k, y * k)
        fun dot(o: Vec) = x * o.x + y * o.y
        fun cross(o: Vec) = x * o.y - y * o.x
        fun length() = sqrt(x * x + y * y)
    }

    fun toLocal(originLat: Double, originLng: Double, lat: Double, lng: Double): Vec {
        val x = Math.toRadians(lng - originLng) * cos(Math.toRadians(originLat)) * EARTH_R
        val y = Math.toRadians(lat - originLat) * EARTH_R
        return Vec(x, y)
    }

    fun distanceM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double =
        toLocal(lat1, lng1, lat2, lng2).length()

    /** 북쪽 기준 시계방향 방위각(도). */
    fun bearingDeg(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val v = toLocal(lat1, lng1, lat2, lng2)
        val deg = Math.toDegrees(atan2(v.x, v.y))
        return (deg + 360.0) % 360.0
    }

    /** -180~180 범위로 정규화한 각도 차이 (target - base). */
    fun angleDiff(baseDeg: Double, targetDeg: Double): Double {
        var d = (targetDeg - baseDeg) % 360.0
        if (d > 180) d -= 360.0
        if (d < -180) d += 360.0
        return d
    }

    /** 방위각 → 평면 단위벡터 (x=동, y=북). */
    fun headingVec(headingDeg: Double): Vec {
        val r = Math.toRadians(headingDeg)
        return Vec(kotlin.math.sin(r), cos(r))
    }

    /**
     * 진행 방향 벡터 기준 점 p 가 어느 쪽인지.
     * cross(dir, p) > 0 이면 진행 방향의 왼쪽(반시계), < 0 이면 오른쪽.
     */
    fun sideOf(dir: Vec, toPoint: Vec): Side = if (dir.cross(toPoint) > 0) Side.LEFT else Side.RIGHT

    /** 선분 ab 위에서 p 에 가장 가까운 점의 비율 t(0~1). */
    fun projectT(a: Vec, b: Vec, p: Vec): Double {
        val ab = b - a
        val len2 = ab.dot(ab)
        if (len2 < 1e-6) return 0.0
        return ((p - a).dot(ab) / len2).coerceIn(0.0, 1.0)
    }
}
