package io.whatsthat.car.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 오늘 물어본 곳 기록. 정차 중 "오늘 지나온 곳" 화면과 프로세스 재생성 후 복원에 쓴다. */
data class JourneyEntry(
    val landmarkId: String,
    val name: String,
    val sideLabel: String,
    val question: String,
    val answer: String,
    val timeMs: Long,
)

class JourneyStore(context: Context) {
    private val prefs = context.getSharedPreferences("journey", Context.MODE_PRIVATE)

    fun load(): List<JourneyEntry> = runCatching {
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            JourneyEntry(
                o.getString("id"), o.getString("name"), o.optString("side"),
                o.optString("q"), o.optString("a"), o.getLong("t"),
            )
        }
    }.getOrDefault(emptyList())

    fun save(entries: List<JourneyEntry>) {
        val arr = JSONArray()
        entries.takeLast(MAX).forEach { e ->
            arr.put(JSONObject().put("id", e.landmarkId).put("name", e.name).put("side", e.sideLabel)
                .put("q", e.question).put("a", e.answer).put("t", e.timeMs))
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    fun clear() = prefs.edit().remove(KEY).apply()

    var autoGuide: Boolean
        get() = prefs.getBoolean("auto_guide", true)
        set(v) = prefs.edit().putBoolean("auto_guide", v).apply()

    companion object {
        private const val KEY = "entries"
        private const val MAX = 100
    }
}
