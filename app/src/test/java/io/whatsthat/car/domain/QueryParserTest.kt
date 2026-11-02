package io.whatsthat.car.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class QueryParserTest {

    @Test
    fun `방향과 시점을 함께 해석한다`() {
        assertEquals(Command.Ask(Side.LEFT, TimeWindow.JUST_NOW), QueryParser.parse("방금 왼쪽에 지나간 거 뭐야?"))
        assertEquals(Command.Ask(Side.RIGHT, TimeWindow.A_WHILE_AGO), QueryParser.parse("아까 오른쪽에 있던 건물 뭐였지"))
        assertEquals(Command.Ask(Side.ANY, TimeWindow.NOW), QueryParser.parse("저거 뭐야"))
        assertEquals(Command.Ask(Side.AHEAD, TimeWindow.NOW), QueryParser.parse("앞에 저 높은 건 뭐야"))
        assertEquals(Command.Ask(Side.RIGHT, null), QueryParser.parse("오른쪽 뭐야"))
    }

    @Test
    fun `후속 명령을 구분한다`() {
        assertEquals(Command.GoThere, QueryParser.parse("거기 가자"))
        assertEquals(Command.AddWaypoint, QueryParser.parse("거기 경유지로 넣어줘"))
        assertEquals(Command.MoreInfo, QueryParser.parse("자세히 알려줘"))
        assertEquals(Command.Next, QueryParser.parse("아니 그거 말고"))
        assertEquals(Command.AutoGuide(false), QueryParser.parse("해설 그만"))
        assertEquals(Command.AutoGuide(true), QueryParser.parse("해설 켜 줘"))
        assertEquals(Command.Stop, QueryParser.parse("조용히 해"))
    }

    @Test
    fun `모르는 말은 Unknown`() {
        assertEquals(true, QueryParser.parse("오늘 날씨 좋네") is Command.Unknown)
    }
}
