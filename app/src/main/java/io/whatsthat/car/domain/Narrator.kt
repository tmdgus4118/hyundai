package io.whatsthat.car.domain

/**
 * 답변 문장을 만든다. 주행 부담(속도·방향지시등·와이퍼)에 따라 길이를 줄인다.
 * 운전자가 짧은 시간 안에 이해할 수 있도록 핵심을 먼저 말한다.
 */
object Narration {

    data class Context(
        val speedKmh: Float = 0f,
        val busy: Boolean = false,     // 방향지시등 작동 중 등 조작 부담이 큰 순간
        val raining: Boolean = false,  // 와이퍼 작동 → 시야가 나쁨
        val night: Boolean = false,    // 전조등 켜짐
    ) {
        val brief get() = busy || speedKmh >= 90f
    }

    fun answer(s: Sighting, window: TimeWindow?, ctx: Context): String {
        val lm = s.landmark
        val where = when {
            window == TimeWindow.NOW || s.passedAgoMs == 0L -> when (s.side) {
                Side.AHEAD -> "앞에 보이는"
                Side.LEFT -> "왼쪽에 보이는"
                Side.RIGHT -> "오른쪽에 보이는"
                Side.ANY -> "보이는"
            }
            else -> "${agoPhrase(s.passedAgoMs)} ${s.side.spoken}에 지나친"
        }
        val head = "$where 곳은 ${lm.name}${josaIGa(lm.name, copula = true)}."
        if (ctx.brief) return head
        val tail = buildString {
            append(' ').append(lm.short)
            if (ctx.night && lm.category == Landmark.Category.TOWER) append(" 밤에는 조명이 켜져 더 잘 보여요.")
        }
        return head + tail
    }

    fun more(lm: Landmark): String = "${lm.name}${josaEunNeun(lm.name)} ${lm.detail}"

    fun alternative(s: Sighting): String = "혹시 ${s.landmark.name}${josaEulReul(s.landmark.name)} 말씀하신 거면, \"다음 거\"라고 말해 주세요."

    /** straightM: 장소까지의 직선거리. 산·타워처럼 멀리 보이는 곳은 "몇 미터 앞" 대신 "멀리"라고 말한다. */
    fun upcoming(s: Sighting, aheadM: Double, straightM: Double = aheadM): String {
        val dist = if (aheadM >= 1000) "%.1f킬로미터".format(aheadM / 1000) else "${(aheadM / 100).toInt() * 100}미터"
        val sided = s.side == Side.LEFT || s.side == Side.RIGHT
        val where = when {
            straightM > FAR_M -> if (sided) "${s.side.spoken} 멀리" else "정면 멀리"
            sided -> "$dist 앞 ${s.side.spoken}으로"
            else -> "$dist 앞 정면에"
        }
        return "$where ${s.landmark.name}${josaIGa(s.landmark.name)} 보여요. ${s.landmark.short}"
    }

    fun notFound(side: Side, window: TimeWindow?): String {
        val sideText = if (side == Side.LEFT || side == Side.RIGHT) "${side.spoken}에서 " else ""
        return when (window) {
            TimeWindow.NOW -> "${sideText}지금 바로 보이는 이름난 곳은 찾지 못했어요."
            TimeWindow.A_WHILE_AGO -> "아까 ${sideText}지나친 곳 중에는 알려드릴 만한 곳을 찾지 못했어요."
            else -> "방금 ${sideText}지나친 곳 중에는 알려드릴 만한 곳을 찾지 못했어요."
        }
    }

    private const val FAR_M = 2_000.0

    const val HELP = "\"방금 왼쪽에 지나간 거 뭐야?\", \"저거 뭐야?\", \"거기 가자\"처럼 말해 보세요. \"해설 켜 줘\"라고 하면 지나가는 길의 명소를 먼저 알려드려요."

    fun agoPhrase(ms: Long): String = when {
        ms < 20_000 -> "방금"
        ms < 90_000 -> "${(ms / 10_000).coerceAtLeast(1) * 10}초 전"
        else -> "${ms / 60_000}분 전"
    }

    // ── 한국어 조사 처리 ──────────────────────────────
    private fun hasBatchim(word: String): Boolean {
        val c = word.trimEnd().lastOrNull { it.isLetterOrDigit() } ?: return false
        if (c in '가'..'힣') return (c - '가') % 28 != 0
        if (c.isDigit()) return c in "0136789"
        return c.lowercaseChar() in "lmnr"
    }

    private fun hasRieulBatchim(word: String): Boolean {
        val c = word.trimEnd().lastOrNull() ?: return false
        return c in '가'..'힣' && (c - '가') % 28 == 8
    }

    /** copula=true 면 "~이에요/~예요", 아니면 주격 "~이/~가". */
    fun josaIGa(word: String, copula: Boolean = false) =
        if (copula) (if (hasBatchim(word)) "이에요" else "예요") else (if (hasBatchim(word)) "이" else "가")

    fun josaEunNeun(word: String) = if (hasBatchim(word)) "은" else "는"
    fun josaEulReul(word: String) = if (hasBatchim(word)) "을" else "를"
    fun josaRo(word: String) = if (hasBatchim(word) && !hasRieulBatchim(word)) "으로" else "로"
}
