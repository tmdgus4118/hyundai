package io.whatsthat.car.platform

import android.content.Context
import android.util.Log
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import io.whatsthat.car.domain.Landmark
import io.whatsthat.car.domain.Narration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * "거기 가자" → Pleos 내비게이션으로 목적지/경유지 설정.
 * 길 안내 중이면 기존 목적지를 지우지 않도록 경유지로 추가한다.
 */
class NaviBridge(private val context: Context) {

    private val _status = MutableStateFlow(SdkStatus("NaviHelper", SdkState.CONNECTING))
    val status: StateFlow<SdkStatus> = _status
    private val _guiding = MutableStateFlow(false)
    val guiding: StateFlow<Boolean> = _guiding

    private var navi: NaviHelper? = null
    private var connected = false
    var onEvent: ((String) -> Unit)? = null

    private val listener = object : NaviHelperEventListener {
        override fun onNavigationStatus(status: NavigationStatus) {
            _guiding.value = status == NavigationStatus.ROUTE_DRIVE
        }
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) { _guiding.value = true }
        override fun onRoutingCompleted() { onEvent?.invoke("경로 탐색을 마쳤어요.") }
        override fun onRouteCancelled() { _guiding.value = false }
        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) { _guiding.value = false }
        override fun onError(errorCode: NaviErrorCode) {
            Log.w(TAG, "navi error $errorCode")
            onEvent?.invoke(
                when (errorCode) {
                    NaviErrorCode.CAUSE_SEARCH_ROUTE_TOO_SHORT_DISTANCE -> "이미 아주 가까이 있어요."
                    NaviErrorCode.CAUSE_SEARCH_ROUTE_OVER_DISTANCE -> "너무 멀어서 길을 찾을 수 없어요."
                    else -> "내비게이션이 길을 찾지 못했어요."
                },
            )
        }
    }

    fun start() {
        runCatching {
            val n = NaviHelper(context.applicationContext)
            navi = n
            n.initialize(
                onConnected = {
                    connected = true
                    n.addListener(listener)
                    _status.value = SdkStatus("NaviHelper", SdkState.PLEOS, "내비게이션 연결")
                },
                onFailed = { t -> _status.value = SdkStatus("NaviHelper", SdkState.UNAVAILABLE, t.message ?: "연결 실패") },
            )
        }.onFailure { _status.value = SdkStatus("NaviHelper", SdkState.UNAVAILABLE, it.message ?: "연결 실패") }
    }

    /** @return 사용자에게 들려줄 안내 문장 */
    fun goTo(lm: Landmark, asWaypoint: Boolean): String {
        val n = navi
        if (!connected || n == null) return "지금은 내비게이션에 연결할 수 없어요."
        return runCatching {
            if (asWaypoint || _guiding.value) {
                n.addWaypoint(
                    RequestWaypointInfo(
                        latitude = lm.lat,
                        longitude = lm.lng,
                        poiName = lm.name,
                        waypointIndex = WaypointIndex.FIRST,
                        poiId = "",
                        address = lm.address,
                        poiSubId = "0",
                    ),
                )
                "${lm.name}${Narration.josaEulReul(lm.name)} 경유지로 추가할게요."
            } else {
                n.requestRoute(
                    RouteInfo(
                        latitude = lm.lat,
                        longitude = lm.lng,
                        poiName = lm.name,
                        poiId = "",
                        address = lm.address,
                        poiSubId = "0",
                    ),
                )
                "${lm.name}까지 길 안내를 시작할게요."
            }
        }.getOrElse {
            Log.w(TAG, "route request failed", it)
            "내비게이션 요청에 실패했어요."
        }
    }

    fun release() {
        navi?.runCatching { removeListener(listener); release() }
        navi = null
        connected = false
    }

    companion object { private const val TAG = "WT/Navi" }
}
