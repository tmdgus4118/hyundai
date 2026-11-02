package io.whatsthat.car.domain

/** 창밖에서 보이는 장소 한 곳. visibleRadiusM 은 도로에서 "보인다"고 판단하는 최대 거리. */
data class Landmark(
    val id: String,
    val name: String,
    val category: Category,
    val lat: Double,
    val lng: Double,
    val visibleRadiusM: Double,
    val prominence: Int,
    val short: String,
    val detail: String,
    val address: String,
) {
    enum class Category(val label: String) {
        TOWER("랜드마크"), BRIDGE("다리"), PALACE("고궁·유적"), MOUNTAIN("산"),
        PARK("공원"), CULTURE("문화"), STADIUM("경기장"), BUILDING("건물"),
        REST_AREA("휴게소"), NATURE("자연"), LANDMARK("명소");

        companion object {
            fun from(raw: String) = entries.firstOrNull { it.name.equals(raw, true) } ?: LANDMARK
        }
    }
}

/** 차량 궤적의 한 점. heading 은 북쪽 기준 시계방향 각도(0~360). */
data class TrackPoint(
    val lat: Double,
    val lng: Double,
    val timeMs: Long,
    val headingDeg: Float,
    val speedKmh: Float,
)

enum class Side(val spoken: String) {
    LEFT("왼쪽"), RIGHT("오른쪽"), AHEAD("앞쪽"), ANY("");
}

/** "언제" 지나친 것을 묻는지. */
enum class TimeWindow(val maxAgeMs: Long, val spoken: String) {
    JUST_NOW(120_000L, "방금"),
    A_WHILE_AGO(20 * 60_000L, "아까"),
    NOW(0L, "지금"),
}

/** 질의 결과 후보. passedAgoMs 는 가장 가까이 지난 시점이 지금으로부터 얼마나 전인지. */
data class Sighting(
    val landmark: Landmark,
    val side: Side,
    val distanceM: Double,
    val passedAgoMs: Long,
    val score: Double,
)
