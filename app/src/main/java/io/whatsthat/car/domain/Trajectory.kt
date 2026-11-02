package io.whatsthat.car.domain

/**
 * 최근 주행 궤적을 보관하는 링 버퍼.
 * "방금 지나친 것"을 계산하려면 현재 위치가 아니라 지나온 경로가 필요하다.
 */
class Trajectory(
    private val retentionMs: Long = 30 * 60_000L,
    private val minStepM: Double = 8.0,
) {
    private val points = ArrayDeque<TrackPoint>()

    @Synchronized
    fun add(p: TrackPoint) {
        val last = points.lastOrNull()
        if (last != null) {
            val jump = GeoMath.distanceM(last.lat, last.lng, p.lat, p.lng)
            val dtSec = (p.timeMs - last.timeMs) / 1000.0
            // 시간 역행(시연 재시작)이나 물리적으로 불가능한 순간 이동(GPS 튐, 페리·터널 후 재획득)은
            // 가짜 구간을 만들어 엉뚱한 곳을 "지나쳤다"고 답하게 하므로 궤적을 새로 시작한다.
            if (p.timeMs < last.timeMs || (jump > 300 && jump / dtSec.coerceAtLeast(0.5) > MAX_SPEED_MPS)) points.clear()
            else if (jump < minStepM) {
                // 정차 중 미세 흔들림은 마지막 점만 갱신
                points.removeLast()
                points.addLast(p.copy(headingDeg = if (p.speedKmh < 3f) last.headingDeg else p.headingDeg))
                return
            }
        }
        points.addLast(p)
        val cutoff = p.timeMs - retentionMs
        while (points.size > 2 && points.first().timeMs < cutoff) points.removeFirst()
    }

    @Synchronized
    fun snapshot(): List<TrackPoint> = points.toList()

    @Synchronized
    fun latest(): TrackPoint? = points.lastOrNull()

    @Synchronized
    fun clear() = points.clear()

    private companion object {
        const val MAX_SPEED_MPS = 90.0 // 약 320km/h
    }

    /** 위치 소스가 heading 을 주지 않을 때 최근 이동 벡터로 진행 방향을 보정한다. */
    @Synchronized
    fun estimatedHeading(): Double? {
        if (points.size < 2) return points.lastOrNull()?.headingDeg?.toDouble()
        val b = points.last()
        val a = points.reversed().firstOrNull { GeoMath.distanceM(it.lat, it.lng, b.lat, b.lng) > 25 } ?: return b.headingDeg.toDouble()
        return GeoMath.bearingDeg(a.lat, a.lng, b.lat, b.lng)
    }
}
