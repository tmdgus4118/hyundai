package io.whatsthat.car.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.whatsthat.car.WhatsThatApp
import io.whatsthat.car.data.JourneyEntry
import io.whatsthat.car.demo.DemoRoute
import io.whatsthat.car.demo.DemoRoutePlayer
import io.whatsthat.car.domain.Command
import io.whatsthat.car.domain.GeoMath
import io.whatsthat.car.domain.Landmark
import io.whatsthat.car.domain.Narration
import io.whatsthat.car.domain.QueryParser
import io.whatsthat.car.domain.Side
import io.whatsthat.car.domain.Sighting
import io.whatsthat.car.domain.SightingResolver
import io.whatsthat.car.domain.TimeWindow
import io.whatsthat.car.domain.TrackPoint
import io.whatsthat.car.domain.Trajectory
import io.whatsthat.car.platform.SdkStatus
import io.whatsthat.car.platform.VehicleState
import io.whatsthat.car.platform.VoiceInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class Answer(
    val question: String,
    val text: String,
    val sighting: Sighting?,
    val timeMs: Long,
)

data class UiState(
    val vehicle: VehicleState = VehicleState(),
    val position: TrackPoint? = null,
    val nearby: String = "",
    val phase: VoiceInput.Phase = VoiceInput.Phase.IDLE,
    val partial: String = "",
    val micLevel: Float = 0f,
    val speaking: Boolean = false,
    val answer: Answer? = null,
    val upcoming: Pair<Sighting, Double>? = null,
    val journey: List<JourneyEntry> = emptyList(),
    val autoGuide: Boolean = true,
    val sdk: List<SdkStatus> = emptyList(),
    val demo: DemoRoute? = null,
    val notice: String? = null,
    val voiceAvailable: Boolean = false,
) {
    val parked get() = vehicle.parked
}

/**
 * 화면과 SDK 사이의 컨트롤러.
 * 위치 → 궤적 → 질의 해석 → 장소 판정 → 음성 답변 → (원하면) 내비 연동 흐름을 조율한다.
 */
class WhatsThatViewModel(app: Application) : AndroidViewModel(app) {

    private val deps = app as WhatsThatApp
    private val resolver = SightingResolver(deps.landmarks.all)
    private val trajectory = Trajectory()
    private val demoPlayer = DemoRoutePlayer(viewModelScope)

    private val _ui = MutableStateFlow(UiState(journey = deps.journey.load(), autoGuide = deps.journey.autoGuide))
    val ui: StateFlow<UiState> = _ui

    private var candidates: List<Sighting> = emptyList()
    private var candidateIndex = 0
    private var lastWindow: TimeWindow? = null
    private var lastSpoken: String = ""
    private val announced = mutableMapOf<String, Long>()
    private var lastAutoCheck = 0L
    private var lastAnnouncedAt = 0L
    private var started = false

    fun start() {
        if (started) return
        started = true
        deps.vehicle.start()
        deps.voiceOut.start()
        deps.voiceIn.start()
        deps.navi.start()
        deps.navi.onEvent = { msg -> notify(msg) }
        deps.location.start { fix -> if (_ui.value.demo == null) onFix(fix) }

        viewModelScope.launch {
            combine(deps.location.status, deps.vehicle.status, deps.voiceIn.status, deps.voiceOut.status, deps.navi.status) { a, b, c, d, e ->
                listOf(a, b, c, d, e)
            }.collect { list -> _ui.update { it.copy(sdk = list, voiceAvailable = deps.voiceIn.available) } }
        }
        viewModelScope.launch { deps.vehicle.state.collect { v -> _ui.update { it.copy(vehicle = v) } } }
        viewModelScope.launch {
            combine(deps.voiceIn.phase, deps.voiceIn.partial, deps.voiceIn.level, deps.voiceOut.speaking) { p, t, l, s -> listOf(p, t, l, s) }
                .collect { (p, t, l, s) ->
                    _ui.update { it.copy(phase = p as VoiceInput.Phase, partial = t as String, micLevel = l as Float, speaking = s as Boolean) }
                }
        }
    }

    // ── 위치 수신 ──────────────────────────────────────────
    private fun onFix(fix: TrackPoint) {
        trajectory.add(fix)
        val heading = trajectory.estimatedHeading() ?: fix.headingDeg.toDouble()
        val upcoming = resolver.upcoming(fix, heading).firstOrNull()
        val near = deps.landmarks.all.minByOrNull { GeoMath.distanceM(fix.lat, fix.lng, it.lat, it.lng) }
        val nearText = near?.let {
            val d = GeoMath.distanceM(fix.lat, fix.lng, it.lat, it.lng)
            "${it.name} ${if (d >= 1000) "%.1fkm".format(d / 1000) else "${d.toInt()}m"}"
        }.orEmpty()
        _ui.update { it.copy(position = fix, upcoming = upcoming, nearby = nearText) }
        maybeAutoGuide(fix, heading)
    }

    /** 자동 해설: 곧 창밖에 나타날 명소를 운전 부담이 적은 순간에만 먼저 알려준다. */
    private fun maybeAutoGuide(fix: TrackPoint, heading: Double) {
        val now = System.currentTimeMillis()
        if (now - lastAutoCheck < 2_000) return
        lastAutoCheck = now
        val s = _ui.value
        val list = resolver.upcoming(fix, heading, maxAheadM = 4_000.0)

        // 실차: Pleos FusedLocation Proximity Alert 로 진입 시점을 받는다
        if (s.demo == null && deps.location.proximitySupported) {
            list.take(5).forEach { (sight, _) ->
                val lm = sight.landmark
                deps.location.watch(lm.id, lm.lat, lm.lng, (lm.visibleRadiusM.coerceAtMost(2_000.0)).toFloat()) { id -> onProximityEnter(id) }
            }
        }
        if (!s.autoGuide || !isMoving(fix, s) || s.speaking || s.phase != VoiceInput.Phase.IDLE || s.vehicle.signalling) return
        if (now - lastAnnouncedAt < MIN_ANNOUNCE_GAP_MS) return
        val target = list.firstOrNull { (sight, along) ->
            along in 250.0..1_500.0 && sight.landmark.prominence >= 5 && !recentlyAnnounced(sight.landmark.id, now)
        } ?: return
        announce(target.first, target.second, now)
    }

    private fun onProximityEnter(landmarkId: String) {
        val now = System.currentTimeMillis()
        val s = _ui.value
        val fix = trajectory.latest() ?: return
        if (now - lastAnnouncedAt < MIN_ANNOUNCE_GAP_MS) return
        if (!s.autoGuide || !isMoving(fix, s) || s.speaking || s.vehicle.signalling || recentlyAnnounced(landmarkId, now)) return
        val heading = trajectory.estimatedHeading() ?: return
        val hit = resolver.upcoming(fix, heading, minAheadM = 0.0, maxAheadM = 4_000.0).firstOrNull { it.first.landmark.id == landmarkId } ?: return
        announce(hit.first, hit.second, now)
    }

    private fun announce(sight: Sighting, along: Double, now: Long) {
        val s = sight.copy(distanceM = along) // 화면과 음성이 같은 "앞으로 남은 거리"를 쓰도록
        announced[s.landmark.id] = now
        lastAnnouncedAt = now
        val text = Narration.upcoming(s, along, straightM = sight.distanceM)
        setAnswer("자동 해설", text, s, record = false)
        candidates = listOf(s)
        candidateIndex = 0
        say(text)
    }

    /** 자동 해설은 실제로 달리는 중에만. 정차(P)나 신호 대기 중에는 먼저 말 걸지 않는다. */
    private fun isMoving(fix: TrackPoint, s: UiState): Boolean {
        if (s.vehicle.gear == VehicleState.Gear.PARK) return false
        return currentSpeed(fix, s) >= 8f
    }

    /** 클러스터 차속(PERF_VEHICLE_SPEED)을 우선하고, 값이 없거나 0이면 위치 변화로 계산한 속도를 쓴다. */
    private fun currentSpeed(fix: TrackPoint?, s: UiState = _ui.value): Float =
        s.vehicle.speedKmh?.takeIf { it > 0.5f } ?: fix?.speedKmh ?: 0f

    private fun recentlyAnnounced(id: String, now: Long) = announced[id]?.let { now - it < 30 * 60_000L } == true

    // ── 사용자 질의 ────────────────────────────────────────
    fun onMicPressed() {
        val s = _ui.value
        if (s.phase != VoiceInput.Phase.IDLE) { deps.voiceIn.cancel(); return }
        deps.voiceOut.stop()
        deps.voiceIn.listen(onFinal = { text -> ask(text) }, onError = { msg -> notify(msg) })
    }

    fun ask(text: String) {
        val command = QueryParser.parse(text)
        when (command) {
            is Command.Ask -> answerAsk(text, command)
            Command.Next -> nextCandidate(text)
            Command.MoreInfo -> current()?.let { respond(text, Narration.more(it.landmark), it, record = false) } ?: respond(text, "먼저 \"저거 뭐야?\"라고 물어봐 주세요.", null)
            Command.GoThere -> goThere(text, asWaypoint = false)
            Command.AddWaypoint -> goThere(text, asWaypoint = true)
            Command.Repeat -> if (lastSpoken.isNotBlank()) say(lastSpoken)
            Command.Stop -> { deps.voiceOut.stop(); deps.voiceIn.cancel() }
            is Command.AutoGuide -> setAutoGuide(command.on, speak = true)
            Command.Help -> respond(text, Narration.HELP, null, record = false)
            is Command.Unknown -> respond(text, "잘 못 알아들었어요. ${Narration.HELP}", null, record = false)
        }
    }

    private fun answerAsk(question: String, cmd: Command.Ask) {
        val track = trajectory.snapshot()
        if (track.isEmpty()) { respond(question, "아직 차량 위치를 받지 못했어요.", null, record = false); return }
        val now = System.currentTimeMillis()
        val heading = trajectory.estimatedHeading()
        val window = cmd.window
        val found = when (window) {
            null -> resolver.resolve(track, cmd.side, TimeWindow.NOW, now, heading)
                .ifEmpty { resolver.resolve(track, cmd.side, TimeWindow.JUST_NOW, now, heading) }
            else -> resolver.resolve(track, cmd.side, window, now, heading)
        }
        candidates = found.take(3)
        candidateIndex = 0
        lastWindow = window ?: if (found.firstOrNull()?.passedAgoMs == 0L) TimeWindow.NOW else TimeWindow.JUST_NOW
        val top = candidates.firstOrNull()
        if (top == null) { respond(question, Narration.notFound(cmd.side, window), null, record = false); return }
        respond(question, Narration.answer(top, lastWindow, narrationContext()), top)
    }

    private fun nextCandidate(question: String) {
        if (candidates.size <= candidateIndex + 1) { respond(question, "다른 후보는 없어요.", null, record = false); return }
        candidateIndex++
        val s = candidates[candidateIndex]
        respond(question, Narration.answer(s, lastWindow, narrationContext()), s)
    }

    private fun goThere(question: String, asWaypoint: Boolean) {
        val target = current()
        if (target == null) { respond(question, "어디로 갈지 먼저 물어봐 주세요.", null, record = false); return }
        respond(question, deps.navi.goTo(target.landmark, asWaypoint), target, record = false)
    }

    /** 정차 화면 기록에서 바로 길 안내. */
    fun navigateTo(entry: JourneyEntry) {
        val lm = deps.landmarks.byId(entry.landmarkId) ?: return
        val msg = deps.navi.goTo(lm, asWaypoint = false)
        setAnswer("${lm.name} 길 안내", msg, null, record = false)
        say(msg)
    }

    fun describe(entry: JourneyEntry) {
        val lm: Landmark = deps.landmarks.byId(entry.landmarkId) ?: return
        val s = Sighting(lm, Side.ANY, 0.0, 0L, 1.0)
        candidates = listOf(s)
        candidateIndex = 0
        respond("${lm.name} 자세히", Narration.more(lm), s, record = false)
    }

    private fun current(): Sighting? = candidates.getOrNull(candidateIndex)

    private fun narrationContext(): Narration.Context {
        val v = _ui.value.vehicle
        return Narration.Context(
            speedKmh = currentSpeed(trajectory.latest()),
            busy = v.signalling,
            raining = v.wiperOn,
            night = v.headlightOn,
        )
    }

    private fun respond(question: String, text: String, s: Sighting?, record: Boolean = true) {
        setAnswer(question, text, s, record)
        say(text)
    }

    private fun setAnswer(question: String, text: String, s: Sighting?, record: Boolean) {
        val now = System.currentTimeMillis()
        _ui.update { st ->
            val journey = if (record && s != null) {
                (st.journey.filterNot { it.landmarkId == s.landmark.id } +
                    JourneyEntry(s.landmark.id, s.landmark.name, s.side.spoken, question, text, now)).also { deps.journey.save(it) }
            } else st.journey
            st.copy(answer = Answer(question, text, s, now), journey = journey)
        }
    }

    private fun say(text: String) {
        lastSpoken = text
        deps.voiceOut.speak(text)
    }

    private fun notify(msg: String) {
        _ui.update { it.copy(notice = msg) }
        say(msg)
    }

    fun dismissNotice() = _ui.update { it.copy(notice = null) }

    fun setAutoGuide(on: Boolean, speak: Boolean = false) {
        deps.journey.autoGuide = on
        _ui.update { it.copy(autoGuide = on) }
        if (speak) say(if (on) "지나가는 길의 명소를 먼저 알려드릴게요." else "자동 해설을 껐어요. 궁금할 때 물어봐 주세요.")
    }

    fun clearJourney() {
        deps.journey.clear()
        _ui.update { it.copy(journey = emptyList()) }
    }

    // ── 시연 모드 ─────────────────────────────────────────
    fun startDemo(route: DemoRoute) {
        trajectory.clear()
        announced.clear()
        lastAnnouncedAt = 0L
        deps.location.unwatchAll()
        _ui.update { it.copy(demo = route) }
        deps.vehicle.overrideGear(VehicleState.Gear.DRIVE)
        demoPlayer.play(route, onFix = { onFix(it) }, onFinish = {
            deps.vehicle.overrideGear(VehicleState.Gear.PARK)
            _ui.update { it.copy(demo = null) }
        })
    }

    fun stopDemo() {
        demoPlayer.stop()
        deps.vehicle.overrideGear(VehicleState.Gear.PARK)
        _ui.update { it.copy(demo = null) }
    }

    fun toggleGearForDemo() {
        val g = _ui.value.vehicle.gear
        deps.vehicle.overrideGear(if (g == VehicleState.Gear.PARK) VehicleState.Gear.DRIVE else VehicleState.Gear.PARK)
    }

    private companion object {
        /** 먼저 말 거는 해설 사이 최소 간격. 도심처럼 명소가 몰린 곳에서 수다스러워지지 않도록. */
        const val MIN_ANNOUNCE_GAP_MS = 40_000L
    }

    override fun onCleared() {
        demoPlayer.stop()
        // 화면이 완전히 종료되면 SDK 연결과 리스너를 해제한다 (차량 리소스 절약)
        deps.location.stop()
        deps.vehicle.stop()
        deps.voiceIn.release()
        deps.voiceOut.release()
        deps.navi.release()
        super.onCleared()
    }
}
