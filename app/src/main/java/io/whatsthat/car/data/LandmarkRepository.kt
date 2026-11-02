package io.whatsthat.car.data

import android.content.Context
import io.whatsthat.car.domain.Landmark
import org.json.JSONArray

/**
 * 오프라인 장소 데이터. 주행 중 네트워크가 끊겨도 답할 수 있도록 앱에 내장한다.
 * 운영 단계에서는 한국관광공사 TourAPI 등 공공데이터로 지역 팩을 내려받아 확장한다.
 */
class LandmarkRepository(private val context: Context) {

    val all: List<Landmark> by lazy { parse(context.assets.open(ASSET).bufferedReader().use { it.readText() }) }

    fun byId(id: String): Landmark? = all.firstOrNull { it.id == id }

    companion object {
        const val ASSET = "landmarks.json"

        fun parse(json: String): List<Landmark> {
            val arr = JSONArray(json)
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Landmark(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    category = Landmark.Category.from(o.getString("category")),
                    lat = o.getDouble("lat"),
                    lng = o.getDouble("lng"),
                    visibleRadiusM = o.getDouble("r"),
                    prominence = o.getInt("p"),
                    short = o.getString("short"),
                    detail = o.getString("detail"),
                    address = o.optString("address"),
                )
            }
        }
    }
}
