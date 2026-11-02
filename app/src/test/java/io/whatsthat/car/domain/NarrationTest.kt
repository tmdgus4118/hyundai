package io.whatsthat.car.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NarrationTest {

    private fun lm(name: String) = Landmark("x", name, Landmark.Category.TOWER, 0.0, 0.0, 1000.0, 5, "짧은 설명이에요.", "긴 설명이에요.", "")

    @Test
    fun `받침에 맞는 조사를 붙인다`() {
        assertEquals("이에요", Narration.josaIGa("경복궁", copula = true))
        assertEquals("예요", Narration.josaIGa("롯데월드타워", copula = true))
        assertEquals("을", Narration.josaEulReul("경복궁"))
        assertEquals("를", Narration.josaEulReul("코엑스"))
        assertEquals("는", Narration.josaEunNeun("롯데월드타워"))
        assertEquals("은", Narration.josaEunNeun("63빌딩"))
    }

    @Test
    fun `고속 주행이나 방향지시등 작동 중에는 짧게 말한다`() {
        val s = Sighting(lm("롯데월드타워"), Side.RIGHT, 300.0, 30_000L, 1.0)
        val brief = Narration.answer(s, TimeWindow.JUST_NOW, Narration.Context(speedKmh = 100f))
        val full = Narration.answer(s, TimeWindow.JUST_NOW, Narration.Context(speedKmh = 50f))
        assertEquals("30초 전 오른쪽에 지나친 곳은 롯데월드타워예요.", brief)
        assertTrue(full.startsWith(brief) && full.length > brief.length)
        assertEquals(brief, Narration.answer(s, TimeWindow.JUST_NOW, Narration.Context(busy = true)))
    }

    @Test
    fun `멀리 있는 명소는 거리 대신 멀리라고 말한다`() {
        val s = Sighting(lm("N서울타워"), Side.LEFT, 400.0, 0L, 1.0)
        assertTrue(Narration.upcoming(s, 400.0, straightM = 4_200.0).startsWith("왼쪽 멀리 N서울타워가 보여요."))
        assertTrue(Narration.upcoming(s, 400.0, straightM = 600.0).startsWith("400미터 앞 왼쪽으로"))
    }
}
