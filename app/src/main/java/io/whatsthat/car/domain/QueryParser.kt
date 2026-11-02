package io.whatsthat.car.domain

/** 음성/칩 입력을 앱이 이해하는 명령으로 바꾼다. 서버 없이 차 안에서 바로 동작하도록 규칙 기반. */
sealed interface Command {
    /** window 가 null 이면 "지금 보이는 것 → 방금 지나친 것" 순으로 찾는다. */
    data class Ask(val side: Side, val window: TimeWindow?) : Command
    data object MoreInfo : Command
    data object GoThere : Command
    data object AddWaypoint : Command
    data object Repeat : Command
    data object Next : Command
    data object Stop : Command
    data class AutoGuide(val on: Boolean) : Command
    data object Help : Command
    data class Unknown(val text: String) : Command
}

object QueryParser {

    private val LEFT = listOf("왼쪽", "왼편", "좌측", "왼")
    private val RIGHT = listOf("오른쪽", "오른편", "우측", "오른")
    private val AHEAD = listOf("앞에", "앞쪽", "정면", "앞으로")
    private val JUST = listOf("방금", "막", "지나간", "지나친", "지나온", "스쳐", "지났")
    private val AGO = listOf("아까", "조금 전", "좀 전", "전에")
    private val NOW = listOf("저거", "저기", "지금", "보이는", "저 ", "저건", "저게")
    private val WHAT = listOf("뭐", "무엇", "어디", "뭔", "누구", "이름", "알려", "궁금", "뭐야", "뭐지", "뭐였")

    fun parse(raw: String): Command {
        val t = normalize(raw)
        if (t.isBlank()) return Command.Unknown(raw)

        if (has(t, "다음 거", "다음거", "그거 말고", "다른 거", "아니 그거", "아니야")) return Command.Next
        if (has(t, "해설")) {
            if (has(t, "꺼", "끄", "중지", "그만")) return Command.AutoGuide(false)
            if (has(t, "켜", "시작", "틀어", "해줘")) return Command.AutoGuide(true)
        }
        if (has(t, "그만", "조용", "멈춰", "스톱", "됐어")) return Command.Stop
        if (has(t, "경유", "들렀다", "들러")) return Command.AddWaypoint
        if (has(t, "가자", "가볼", "가 볼", "가줘", "안내해", "길 안내", "데려다", "거기로", "목적지")) return Command.GoThere
        if (has(t, "자세히", "더 알려", "더 얘기", "좀 더", "더 말해", "설명해")) return Command.MoreInfo
        if (has(t, "다시", "한번 더", "한 번 더", "뭐라고")) return Command.Repeat
        if (has(t, "도움", "사용법", "어떻게 써", "뭐 할 수")) return Command.Help

        val side = when {
            has(t, *LEFT.toTypedArray()) -> Side.LEFT
            has(t, *RIGHT.toTypedArray()) -> Side.RIGHT
            has(t, *AHEAD.toTypedArray()) -> Side.AHEAD
            else -> Side.ANY
        }
        val window = when {
            has(t, *JUST.toTypedArray()) -> TimeWindow.JUST_NOW
            has(t, *AGO.toTypedArray()) -> TimeWindow.A_WHILE_AGO
            has(t, *NOW.toTypedArray()) -> TimeWindow.NOW
            side == Side.AHEAD -> TimeWindow.NOW
            else -> null
        }
        val asksWhat = has(t, *WHAT.toTypedArray())
        if (asksWhat || side != Side.ANY || window != null) return Command.Ask(side, window)
        return Command.Unknown(raw)
    }

    private fun normalize(s: String) = " " + s.trim().replace(Regex("[?!.,~]"), " ").replace(Regex("\\s+"), " ") + " "
    private fun has(t: String, vararg keys: String) = keys.any { t.contains(it) }
}
