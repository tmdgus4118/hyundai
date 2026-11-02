package io.whatsthat.car.demo

import io.whatsthat.car.domain.GeoMath
import io.whatsthat.car.domain.TrackPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** PT·전시장 시연용 주행 경로. 실제 도로를 따라 찍은 좌표를 일정 속도로 재생한다. */
data class DemoRoute(val id: String, val title: String, val subtitle: String, val speedKmh: Float, val points: List<Pair<Double, Double>>)

object DemoRoutes {
    val OLYMPIC_EAST = DemoRoute(
        "olympic", "올림픽대로", "여의도 → 잠실 · 한강 남단 동쪽 방향", 70f,
        listOf(
            37.5170 to 126.9300, 37.5150 to 126.9450, 37.5120 to 126.9560, 37.5075 to 126.9700,
            37.5050 to 126.9810, 37.5080 to 126.9930, 37.5170 to 127.0080, 37.5270 to 127.0230,
            37.5290 to 127.0350, 37.5250 to 127.0560, 37.5185 to 127.0700, 37.5200 to 127.0850,
            37.5230 to 127.0950, 37.5290 to 127.1050, 37.5330 to 127.1200,
        ),
    )
    val SEJONG_NORTH = DemoRoute(
        "sejong", "세종대로", "숭례문 → 광화문 · 도심 북쪽 방향", 35f,
        listOf(37.5560 to 126.9733, 37.5585 to 126.9740, 37.5620 to 126.9762, 37.5650 to 126.9768, 37.5690 to 126.9768, 37.5720 to 126.9768, 37.5742 to 126.9768),
    )
    val GYEONGBU_NORTH = DemoRoute(
        "gyeongbu", "경부고속도로", "판교 → 한남 · 서울 방향", 90f,
        listOf(
            37.3930 to 127.1150, 37.4100 to 127.1050, 37.4300 to 127.0850, 37.4500 to 127.0550,
            37.4590 to 127.0400, 37.4700 to 127.0390, 37.4850 to 127.0330, 37.5000 to 127.0230,
            37.5130 to 127.0130, 37.5250 to 127.0130, 37.5350 to 127.0110,
        ),
    )
    val all = listOf(OLYMPIC_EAST, SEJONG_NORTH, GYEONGBU_NORTH)
}

/**
 * 경로를 따라 위치를 재생한다. timeScale 배속으로 이동하되 보고하는 차속은 실제 주행 속도다.
 * 타임스탬프는 실제 시계를 쓰므로 "방금(2분)" 창은 배속만큼 더 긴 거리를 덮는다.
 */
class DemoRoutePlayer(private val scope: CoroutineScope) {
    private var job: Job? = null
    val running get() = job?.isActive == true

    fun play(route: DemoRoute, timeScale: Double = 3.0, tickMs: Long = 500L, onFix: (TrackPoint) -> Unit, onFinish: () -> Unit) {
        stop()
        job = scope.launch {
            val mps = route.speedKmh / 3.6 * timeScale
            val step = mps * tickMs / 1000.0
            for ((a, b) in route.points.zipWithNext()) {
                val segLen = GeoMath.distanceM(a.first, a.second, b.first, b.second)
                val heading = GeoMath.bearingDeg(a.first, a.second, b.first, b.second).toFloat()
                var travelled = 0.0
                while (travelled < segLen && isActive) {
                    val t = travelled / segLen
                    onFix(TrackPoint(a.first + (b.first - a.first) * t, a.second + (b.second - a.second) * t, System.currentTimeMillis(), heading, route.speedKmh))
                    delay(tickMs)
                    travelled += step
                }
            }
            val (prev, last) = route.points.takeLast(2).let { it[0] to it[1] }
            val endHeading = GeoMath.bearingDeg(prev.first, prev.second, last.first, last.second).toFloat()
            onFix(TrackPoint(last.first, last.second, System.currentTimeMillis(), endHeading, 0f))
            onFinish()
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
