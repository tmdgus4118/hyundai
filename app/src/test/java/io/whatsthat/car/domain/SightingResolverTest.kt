package io.whatsthat.car.domain

import io.whatsthat.car.data.LandmarkRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SightingResolverTest {

    private val landmarks = LandmarkRepository.parse(File("src/main/assets/landmarks.json").readText())
    private val resolver = SightingResolver(landmarks)

    /** a→b 직선을 speedKmh 로 달린 궤적. 마지막 점의 시각이 now. */
    private fun drive(a: Pair<Double, Double>, b: Pair<Double, Double>, now: Long, speedKmh: Double = 60.0, steps: Int = 60): List<TrackPoint> {
        val dist = GeoMath.distanceM(a.first, a.second, b.first, b.second)
        val totalMs = (dist / (speedKmh / 3.6) * 1000).toLong()
        val heading = GeoMath.bearingDeg(a.first, a.second, b.first, b.second).toFloat()
        return (0..steps).map { i ->
            val t = i.toDouble() / steps
            TrackPoint(a.first + (b.first - a.first) * t, a.second + (b.second - a.second) * t, now - totalMs + (totalMs * t).toLong(), heading, speedKmh.toFloat())
        }
    }

    @Test
    fun `동쪽으로 달리면 북쪽에 있는 랜드마크는 왼쪽이다`() {
        val now = 1_000_000L
        // 세종대로 남단에서 동쪽으로: 숭례문(37.5600,126.9752)을 북쪽에 두고 지나감
        val track = drive(37.5585 to 126.9700, 37.5585 to 126.9810, now, speedKmh = 40.0)
        val result = resolver.passed(track, Side.ANY, TimeWindow.JUST_NOW, now)
        val hit = result.first { it.landmark.id == "sungnyemun" }
        assertEquals(Side.LEFT, hit.side)
    }

    @Test
    fun `북쪽으로 달리면 동쪽 랜드마크는 오른쪽, 서쪽은 왼쪽`() {
        val now = 2_000_000L
        val track = drive(37.5585 to 126.9762, 37.5680 to 126.9765, now, speedKmh = 35.0)
        val passed = resolver.passed(track, Side.ANY, TimeWindow.JUST_NOW, now)
        assertEquals(Side.RIGHT, passed.first { it.landmark.id == "seoul_cityhall" }.side)
        assertEquals(Side.LEFT, passed.first { it.landmark.id == "deoksugung" }.side)
    }

    @Test
    fun `방향 필터를 주면 반대편 장소는 나오지 않는다`() {
        val now = 3_000_000L
        val track = drive(37.5585 to 126.9762, 37.5680 to 126.9765, now, speedKmh = 35.0)
        val left = resolver.passed(track, Side.LEFT, TimeWindow.JUST_NOW, now)
        assertTrue(left.isNotEmpty())
        assertTrue(left.all { it.side == Side.LEFT })
    }

    @Test
    fun `아직 앞에 있는 것은 지나친 것으로 답하지 않는다`() {
        val now = 4_000_000L
        // 광화문(37.5759) 남쪽에서 북쪽으로 접근 중
        val track = drive(37.5650 to 126.9768, 37.5700 to 126.9768, now, speedKmh = 35.0)
        val passed = resolver.passed(track, Side.ANY, TimeWindow.JUST_NOW, now)
        assertTrue(passed.none { it.landmark.id == "gwanghwamun" })
        val ahead = resolver.visibleNow(track, Side.AHEAD)
        assertEquals("gwanghwamun", ahead.first().landmark.id)
    }

    @Test
    fun `오래전에 지나친 곳은 방금 질문에서 제외된다`() {
        val now = 10_000_000L
        val old = drive(37.5585 to 126.9762, 37.5680 to 126.9765, now - 15 * 60_000L, speedKmh = 35.0)
        val recent = drive(37.5700 to 126.9900, 37.5700 to 126.9950, now, speedKmh = 30.0, steps = 10)
        val passed = resolver.passed(old + recent, Side.ANY, TimeWindow.JUST_NOW, now)
        assertTrue(passed.none { it.landmark.id == "seoul_cityhall" })
        val earlier = resolver.passed(old + recent, Side.ANY, TimeWindow.A_WHILE_AGO, now)
        assertTrue(earlier.any { it.landmark.id == "seoul_cityhall" })
    }

    @Test
    fun `바로 옆을 지난 곳이 멀리 보이던 유명한 곳보다 우선한다`() {
        val now = 5_000_000L
        // 숭례문·시청 앞을 지나 북쪽으로: 오른쪽 멀리 N서울타워(2km+)도 보이지만 답은 바로 옆을 지난 곳이어야 한다
        val track = drive(37.5585 to 126.9762, 37.5690 to 126.9768, now, speedKmh = 35.0)
        val top = resolver.passed(track, Side.RIGHT, TimeWindow.A_WHILE_AGO, now).first()
        assertTrue(top.landmark.id in setOf("sungnyemun", "seoul_cityhall"))
        assertTrue(top.distanceM < 300)
    }

    @Test
    fun `진행 방향 앞쪽 명소를 예고한다`() {
        val cur = TrackPoint(37.5200, 127.0800, 0L, 80f, 70f) // 잠실 쪽으로 동진
        val up = resolver.upcoming(cur, 80.0, maxAheadM = 4_000.0)
        assertTrue(up.any { it.first.landmark.id == "lotte_tower" && it.first.side == Side.RIGHT })
    }
}

class UpcomingSideTest {
    private val resolver = SightingResolver(LandmarkRepository.parse(File("src/main/assets/landmarks.json").readText()))

    @Test
    fun `거의 정면에 있는 먼 곳은 좌우를 단정하지 않는다`() {
        // 숭례문 앞에서 북북동으로 진행: 900m 앞 서울시청은 진행선에서 거의 벗어나지 않음
        val cur = TrackPoint(37.5585, 126.9740, 0L, 27f, 35f)
        val hall = resolver.upcoming(cur, 27.0).first { it.first.landmark.id == "seoul_cityhall" }
        assertEquals(Side.AHEAD, hall.first.side)
    }
}

class TrajectoryTest {
    @Test
    fun `GPS 순간이동이 생기면 이전 궤적을 버린다`() {
        val t = Trajectory()
        t.add(TrackPoint(37.5200, 127.1000, 0L, 90f, 60f))
        t.add(TrackPoint(37.5201, 127.1010, 1_000L, 90f, 60f))
        t.add(TrackPoint(37.5600, 126.9750, 2_000L, 0f, 40f)) // 1초 만에 11km 이동
        org.junit.Assert.assertEquals(1, t.snapshot().size)
    }
}
