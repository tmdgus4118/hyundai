package io.whatsthat.car.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.whatsthat.car.data.JourneyEntry
import io.whatsthat.car.demo.DemoRoutes
import io.whatsthat.car.domain.Narration
import io.whatsthat.car.ui.UiState
import io.whatsthat.car.ui.WhatsThatViewModel
import io.whatsthat.car.ui.theme.WT
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MainScreen(state: UiState, vm: WhatsThatViewModel) {
    BoxWithConstraints(Modifier.fillMaxSize().background(WT.Night)) {
        val wide = maxWidth >= 1000.dp
        Column(Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 24.dp)) {
            TopBar(state, vm)
            Spacer(Modifier.height(20.dp))
            if (wide) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    AskPanel(state, vm, Modifier.weight(0.92f).fillMaxHeight())
                    if (state.parked) ParkedPanel(state, vm, Modifier.weight(1.08f).fillMaxHeight())
                    else AnswerPanel(state, vm, Modifier.weight(1.08f).fillMaxHeight())
                }
            } else {
                // 세로 창(앱 영역 1칸): 핵심 기능만 위에서 아래로
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    AskPanel(state, vm, Modifier.fillMaxWidth().height(620.dp))
                    if (state.parked) ParkedPanel(state, vm, Modifier.fillMaxWidth().height(820.dp))
                    else AnswerPanel(state, vm, Modifier.fillMaxWidth().height(620.dp))
                }
            }
        }
        state.notice?.let { msg ->
            LaunchedEffect(msg) {
                delay(4_000)
                vm.dismissNotice()
            }
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp).clip(RoundedCornerShape(20.dp))
                    .background(WT.SurfaceHigh).border(1.dp, WT.Line, RoundedCornerShape(20.dp))
                    .clickable { vm.dismissNotice() }.padding(horizontal = 32.dp, vertical = 20.dp),
            ) { Text(msg, style = MaterialTheme.typography.titleMedium, color = WT.TextHigh) }
        }
    }
}

@Composable
private fun TopBar(state: UiState, vm: WhatsThatViewModel) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("저거뭐야", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = WT.TextHigh)
        Spacer(Modifier.width(14.dp))
        Text("창밖 가이드", style = MaterialTheme.typography.titleMedium, color = WT.TextLow)
        Spacer(Modifier.weight(1f))
        state.demo?.let { Pill("시연 중 · ${it.title} ■", active = true, color = WT.Warn) { vm.stopDemo() }; Spacer(Modifier.width(12.dp)) }
        Pill(if (state.autoGuide) "자동 해설 켬" else "자동 해설 끔", active = state.autoGuide) { vm.setAutoGuide(!state.autoGuide, speak = true) }
        Spacer(Modifier.width(12.dp))
        val v = state.vehicle
        val speed = v.speedKmh?.takeIf { it > 0.5f } ?: state.position?.speedKmh
        Pill(
            buildString {
                append(if (state.parked) "정차" else "주행")
                append(" · ").append(v.gear.label)
                if (speed != null && !state.parked) append(" · ${speed.toInt()}km/h")
            },
            active = !state.parked, color = WT.Sand,
        ) { if (state.demo != null || v.gear == io.whatsthat.car.platform.VehicleState.Gear.UNKNOWN) vm.toggleGearForDemo() }
    }
}

/** 왼쪽: 질문 영역. 음성 버튼 + 한 번에 누르는 프리셋. 주행 중에도 이 영역만으로 모든 기능을 쓸 수 있다. */
@Composable
private fun AskPanel(state: UiState, vm: WhatsThatViewModel, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(0.4f))
            AskOrb(state.phase, state.micLevel, state.speaking, enabled = state.voiceAvailable, size = 300.dp) { vm.onMicPressed() }
            Spacer(Modifier.height(18.dp))
            Text(
                when {
                    state.partial.isNotBlank() -> "“${state.partial}”"
                    state.voiceAvailable -> "누르고 \"방금 왼쪽에 지나간 거 뭐야?\""
                    else -> "아래 버튼으로 물어보세요"
                },
                style = MaterialTheme.typography.titleMedium, color = WT.TextMid, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(0.6f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                QuickAsk("방금 왼쪽", Icons.AutoMirrored.Rounded.ArrowBack, true, Modifier.weight(1f)) { vm.ask("방금 왼쪽에 지나간 거 뭐야") }
                QuickAsk("저거 뭐야", Icons.Rounded.Visibility, true, Modifier.weight(1f)) { vm.ask("저거 뭐야") }
                QuickAsk("방금 오른쪽", Icons.AutoMirrored.Rounded.ArrowForward, false, Modifier.weight(1f)) { vm.ask("방금 오른쪽에 지나간 거 뭐야") }
            }
        }
    }
}

/** 오른쪽(주행 중): 답변 하나만 크게. 텍스트는 짧게, 방향은 화살표로 먼저 보여준다. */
@Composable
private fun AnswerPanel(state: UiState, vm: WhatsThatViewModel, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Card(Modifier.fillMaxWidth().weight(1f)) {
            AnimatedContent(state.answer, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "answer") { a ->
                if (a == null) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                        Text("궁금한 게 지나가면", style = MaterialTheme.typography.displayMedium, color = WT.TextHigh)
                        Text("그냥 물어보세요.", style = MaterialTheme.typography.displayMedium, color = WT.ActiveBlue)
                        Spacer(Modifier.height(20.dp))
                        Text("이미 지나친 것도 차가 기억하고 있어요.", style = MaterialTheme.typography.bodyLarge, color = WT.TextMid)
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Text(a.question, style = MaterialTheme.typography.titleMedium, color = WT.TextLow, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(18.dp))
                        val s = a.sighting
                        if (s != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SideBadge(s.side)
                                Spacer(Modifier.width(22.dp))
                                Column {
                                    Text(s.landmark.name, style = MaterialTheme.typography.displayLarge, color = WT.TextHigh, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        listOfNotNull(
                                            s.landmark.category.label,
                                            s.side.spoken.ifBlank { null },
                                            if (s.passedAgoMs > 0) Narration.agoPhrase(s.passedAgoMs) else null,
                                            if (s.distanceM > 0) "약 ${formatDist(s.distanceM)}" else null,
                                        ).joinToString("  ·  "),
                                        style = MaterialTheme.typography.titleMedium, color = WT.ActiveBlue,
                                    )
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                        }
                        Text(
                            a.text, style = MaterialTheme.typography.bodyLarge, color = WT.TextMid,
                            // 주행 중 긴 글 금지(LIMIT_STRING_LENGTH): 정차 시에만 전체 표시
                            maxLines = if (state.parked) 8 else 3, overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.weight(1f))
                        if (s != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                QuickAsk("거기 가자", Icons.Rounded.Navigation, true, Modifier.weight(1f)) { vm.ask("거기 가자") }
                                QuickAsk("더 알려줘", null, true, Modifier.weight(1f)) { vm.ask("자세히 알려줘") }
                                QuickAsk("다른 거", null, true, Modifier.weight(0.8f)) { vm.ask("다음 거") }
                            }
                        }
                    }
                }
            }
        }
        UpNext(state)
    }
}

@Composable
private fun UpNext(state: UiState) {
    val up = state.upcoming
    Row(
        Modifier.fillMaxWidth().heightIn(min = 84.dp).clip(RoundedCornerShape(22.dp)).background(WT.Surface)
            .border(1.dp, WT.Line, RoundedCornerShape(22.dp)).padding(horizontal = 26.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("곧 보여요", style = MaterialTheme.typography.labelMedium, color = WT.TextLow)
        Spacer(Modifier.width(20.dp))
        if (up == null) Text(if (state.position == null) "위치를 기다리는 중" else "가까운 앞쪽에는 아직 없어요", style = MaterialTheme.typography.titleMedium, color = WT.TextMid)
        else {
            Text("${formatDist(up.second)} 앞 ${if (up.first.side == io.whatsthat.car.domain.Side.AHEAD) "정면" else up.first.side.spoken}", style = MaterialTheme.typography.titleMedium, color = WT.ActiveBlue)
            Spacer(Modifier.width(14.dp))
            Text(up.first.landmark.name, style = MaterialTheme.typography.titleLarge, color = WT.TextHigh, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.weight(1f))
        if (state.nearby.isNotBlank()) Text("근처 ${state.nearby}", fontSize = 16.sp, color = WT.TextLow, maxLines = 1)
    }
}

/** 오른쪽(정차 중): 답변 + 오늘 지나온 곳 + 연결 상태 + 시연. 주행 규제가 풀린 상태에서만 보인다. */
@Composable
private fun ParkedPanel(state: UiState, vm: WhatsThatViewModel, modifier: Modifier) {
    var tab by remember { mutableStateOf(0) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("답변", "오늘 지나온 곳 ${state.journey.size}", "연결 상태", "시연").forEachIndexed { i, t ->
                Pill(t, active = tab == i) { tab = i }
            }
        }
        when (tab) {
            0 -> AnswerPanel(state, vm, Modifier.fillMaxWidth().weight(1f))
            1 -> JourneyList(state.journey, vm, Modifier.fillMaxWidth().weight(1f))
            2 -> Card(Modifier.fillMaxWidth().weight(1f)) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Pleos Connect SDK 연결", style = MaterialTheme.typography.titleLarge, color = WT.TextHigh)
                    Spacer(Modifier.height(12.dp))
                    state.sdk.forEach { SdkRow(it) }
                    Spacer(Modifier.height(12.dp))
                    val v = state.vehicle
                    Text(
                        "기어 ${v.gear.label} · 방향지시등 ${v.turnSignal.name} · 와이퍼 ${if (v.wiperOn) "ON" else "OFF"} · 전조등 ${if (v.headlightOn) "ON" else "OFF"}" +
                            (v.tripKm?.let { " · 트립 %.1fkm".format(it) } ?: "") + " · 주행규제 ${if (v.uxRestricted) "적용" else "해제"}",
                        fontSize = 16.sp, color = WT.TextLow,
                    )
                }
            }
            else -> Card(Modifier.fillMaxWidth().weight(1f)) {
                Column {
                    Text("시연 주행", style = MaterialTheme.typography.titleLarge, color = WT.TextHigh)
                    Text("실제 도로 좌표를 3배속으로 재생해 주행 중 질문을 체험해요.", style = MaterialTheme.typography.bodyMedium, color = WT.TextMid)
                    Spacer(Modifier.height(18.dp))
                    DemoRoutes.all.forEach { r ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(20.dp)).background(WT.SurfaceHigh)
                                .clickable { vm.startDemo(r) }.padding(horizontal = 24.dp, vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(r.title, style = MaterialTheme.typography.titleLarge, color = WT.TextHigh)
                                Text("${r.subtitle} · ${r.speedKmh.toInt()}km/h", style = MaterialTheme.typography.labelMedium, color = WT.TextMid)
                            }
                            Text("출발", style = MaterialTheme.typography.labelLarge, color = WT.ActiveBlue)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JourneyList(entries: List<JourneyEntry>, vm: WhatsThatViewModel, modifier: Modifier) {
    Card(modifier) {
        if (entries.isEmpty()) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("아직 물어본 곳이 없어요", style = MaterialTheme.typography.headlineMedium, color = WT.TextMid)
                Text("주행 중에 물어본 곳이 여기에 모여요.", style = MaterialTheme.typography.bodyMedium, color = WT.TextLow)
            }
        } else {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("오늘 지나온 곳", style = MaterialTheme.typography.titleLarge, color = WT.TextHigh)
                    Spacer(Modifier.weight(1f))
                    Text("기록 지우기", style = MaterialTheme.typography.labelMedium, color = WT.TextLow, modifier = Modifier.clickable { vm.clearJourney() }.padding(12.dp))
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(entries.reversed(), key = { it.landmarkId }) { e -> JourneyRow(e, vm) }
                }
            }
        }
    }
}

@Composable
private fun JourneyRow(e: JourneyEntry, vm: WhatsThatViewModel) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(WT.SurfaceHigh).padding(horizontal = 22.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(92.dp)) {
            Text(timeFmt.format(Date(e.timeMs)), style = MaterialTheme.typography.labelMedium, color = WT.ActiveBlue)
            Text(e.sideLabel.ifBlank { "-" }, fontSize = 15.sp, color = WT.TextLow)
        }
        Column(Modifier.weight(1f)) {
            Text(e.name, style = MaterialTheme.typography.titleLarge, color = WT.TextHigh, maxLines = 1)
            Text("“${e.question}”", fontSize = 15.sp, color = WT.TextLow, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(width = 120.dp, height = 64.dp).clip(RoundedCornerShape(16.dp)).background(WT.Surface).clickable { vm.describe(e) }, contentAlignment = Alignment.Center) {
            Text("설명", style = MaterialTheme.typography.labelMedium, color = WT.TextHigh)
        }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(width = 120.dp, height = 64.dp).clip(RoundedCornerShape(16.dp)).background(WT.ActiveBlue).clickable { vm.navigateTo(e) }, contentAlignment = Alignment.Center) {
            Text("길 안내", style = MaterialTheme.typography.labelMedium, color = WT.Night)
        }
    }
}

private val timeFmt = SimpleDateFormat("HH:mm", Locale.KOREA)
private fun formatDist(m: Double) = if (m >= 1000) "%.1fkm".format(m / 1000) else "${(m / 10).toInt() * 10}m"
