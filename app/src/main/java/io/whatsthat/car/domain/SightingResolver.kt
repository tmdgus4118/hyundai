package io.whatsthat.car.domain

import kotlin.math.abs
import kotlin.math.pow

/**
 * "방금 왼쪽에 지나간 거 뭐야?"에 답하는 핵심 엔진.
 * 지나온 궤적의 각 구간에 대해 랜드마크와의 최근접점을 구하고,
 * 그 순간 진행 방향 기준 좌/우, 거리, 경과 시간으로 점수를 매긴다.
 */
class SightingResolver(private val landmarks: List<Landmark>) {

    fun resolve(track: List<TrackPoint>, side: Side, window: TimeWindow, nowMs: Long, headingOverride: Double? = null): List<Sighting> =
        when (window) {
            TimeWindow.NOW -> visibleNow(track, side, headingOverride)
            else -> passed(track, side, window, nowMs, headingOverride)
        }

    /** 지금 차창 밖으로 보이는 것. 앞쪽 시야각 안을 우선한다. */
    fun visibleNow(track: List<TrackPoint>, side: Side, headingOverride: Double? = null): List<Sighting> {
        val cur = track.lastOrNull() ?: return emptyList()
        val heading = headingOverride ?: cur.headingDeg.toDouble()
        return landmarks.mapNotNull { lm ->
            val d = GeoMath.distanceM(cur.lat, cur.lng, lm.lat, lm.lng)
            if (d > lm.visibleRadiusM) return@mapNotNull null
            val rel = GeoMath.angleDiff(heading, GeoMath.bearingDeg(cur.lat, cur.lng, lm.lat, lm.lng))
            val observed = when {
                abs(rel) <= 30 -> Side.AHEAD
                rel < 0 -> Side.LEFT
                else -> Side.RIGHT
            }
            val accepted = when (side) {
                Side.ANY -> abs(rel) <= 100
                Side.AHEAD -> abs(rel) <= 40
                Side.LEFT -> rel in -160.0..-15.0
                Side.RIGHT -> rel in 15.0..160.0
            }
            if (!accepted) return@mapNotNull null
            // 정면에 가까울수록 "저거"로 가리켰을 가능성이 높다
            val angleWeight = 1.0 - (abs(rel) / 180.0) * 0.7
            val score = prominenceWeight(lm) * closeness(d, lm) * angleWeight * proximity(d, 2_000.0)
            Sighting(lm, observed, d, 0L, score)
        }.sortedByDescending { it.score }
    }

    /** 이미 지나친 것. 궤적 선분별 최근접점으로 판정한다. */
    fun passed(track: List<TrackPoint>, side: Side, window: TimeWindow, nowMs: Long, headingOverride: Double? = null): List<Sighting> {
        if (track.size < 2) return emptyList()
        val cur = track.last()
        val heading = headingOverride ?: cur.headingDeg.toDouble()
        val recent = track.filter { nowMs - it.timeMs <= window.maxAgeMs }.ifEmpty { track.takeLast(2) }
        val path = if (recent.size >= 2) recent else track.takeLast(2)
        val pathLen = path.zipWithNext { a, b -> GeoMath.distanceM(a.lat, a.lng, b.lat, b.lng) }.sum()

        return landmarks.mapNotNull { lm ->
            // 이번 경로에서 닿을 수 없는 곳은 빠르게 제외
            if (GeoMath.distanceM(cur.lat, cur.lng, lm.lat, lm.lng) > pathLen + lm.visibleRadiusM + 200) return@mapNotNull null

            var best: Triple<Double, Side, Long>? = null
            for ((a, b) in path.zipWithNext()) {
                val va = GeoMath.toLocal(lm.lat, lm.lng, a.lat, a.lng)
                val vb = GeoMath.toLocal(lm.lat, lm.lng, b.lat, b.lng)
                val dir = vb - va
                if (dir.length() < 1.0) continue
                val t = GeoMath.projectT(va, vb, GeoMath.Vec(0.0, 0.0))
                val c = va + dir * t
                val d = c.length()
                if (best == null || d < best.first) {
                    // 최근접점에서 랜드마크를 향하는 벡터 = 원점 - c
                    val s = GeoMath.sideOf(dir, c * -1.0)
                    val time = a.timeMs + ((b.timeMs - a.timeMs) * t).toLong()
                    best = Triple(d, s, time)
                }
            }
            val (d, s, time) = best ?: return@mapNotNull null
            if (d > lm.visibleRadiusM) return@mapNotNull null
            if (side == Side.LEFT || side == Side.RIGHT) if (s != side) return@mapNotNull null

            // 아직 한참 앞에 있는 것은 "지나친 것"이 아니다
            val rel = GeoMath.angleDiff(heading, GeoMath.bearingDeg(cur.lat, cur.lng, lm.lat, lm.lng))
            val distNow = GeoMath.distanceM(cur.lat, cur.lng, lm.lat, lm.lng)
            if (abs(rel) < 35 && distNow > 150) return@mapNotNull null

            val ago = (nowMs - time).coerceAtLeast(0L)
            if (ago > window.maxAgeMs + 5_000) return@mapNotNull null
            val recency = 1.0 - 0.6 * (ago.toDouble() / window.maxAgeMs).coerceIn(0.0, 1.0)
            // 바로 옆을 스쳐 지난 곳이 멀리 보이던 유명한 곳보다 질문 대상일 가능성이 높다
            val score = prominenceWeight(lm) * closeness(d, lm) * recency * proximity(d, 800.0)
            Sighting(lm, s, d, ago, score)
        }.sortedByDescending { it.score }
    }

    /**
     * 자동 해설용: 지금 진행 방향을 유지하면 곧 창밖에 나타날 곳.
     * alongM 은 진행 방향으로 얼마나 앞인지, 측면 거리는 visibleRadius 이내여야 한다.
     */
    fun upcoming(cur: TrackPoint, heading: Double, minAheadM: Double = 150.0, maxAheadM: Double = 1_800.0): List<Pair<Sighting, Double>> {
        val dir = GeoMath.headingVec(heading)
        return landmarks.mapNotNull { lm ->
            val p = GeoMath.toLocal(cur.lat, cur.lng, lm.lat, lm.lng)
            val along = p.dot(dir)
            if (along !in minAheadM..maxAheadM) return@mapNotNull null
            val cross = abs(dir.cross(p))
            if (cross > lm.visibleRadiusM) return@mapNotNull null
            // 거의 정면에 있는 곳은 직선 외삽만으로 좌/우를 단정할 수 없다 (도로가 조금만 휘어도 뒤바뀜)
            val side = if (cross < maxOf(60.0, along * 0.2)) Side.AHEAD else GeoMath.sideOf(dir, p)
            val score = prominenceWeight(lm) * closeness(cross, lm)
            Sighting(lm, side, p.length(), 0L, score) to along
        }.sortedByDescending { it.first.score }
    }

    private fun proximity(d: Double, scaleM: Double) = 0.5 + 0.5 * kotlin.math.exp(-d / scaleM)

    private fun prominenceWeight(lm: Landmark) = 0.4 + lm.prominence.coerceIn(1, 10) / 10.0 * 0.6

    private fun closeness(d: Double, lm: Landmark) = (1.0 - d / lm.visibleRadiusM).coerceIn(0.0, 1.0).pow(0.6) * 0.8 + 0.2
}
