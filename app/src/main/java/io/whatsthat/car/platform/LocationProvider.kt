package io.whatsthat.car.platform

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.listener.ProximityAlertListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.location.fused.model.ProximityAlertEvent
import ai.pleos.playground.location.fused.model.ProximityAlertRequest
import ai.pleos.playground.location.fused.model.ProximityTransition
import io.whatsthat.car.domain.GeoMath
import io.whatsthat.car.domain.TrackPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 차량 위치 소스.
 * 1순위 Pleos FusedLocation SDK(차량 센서 융합 위치), 미지원 차량이면 AAOS LocationManager 로 대체한다.
 * 진행 방향 앞의 장소는 FusedLocation Proximity Alert 로 등록해 "곧 보여요" 해설의 트리거로 쓴다.
 */
class LocationProvider(private val context: Context) {

    private val _status = MutableStateFlow(SdkStatus("FusedLocation", SdkState.CONNECTING))
    val status: StateFlow<SdkStatus> = _status

    private var fused: FusedLocation? = null
    private var onFix: ((TrackPoint) -> Unit)? = null
    private var last: TrackPoint? = null
    private val alertIds = mutableMapOf<String, String>()   // alertId -> landmarkId
    private val registeredLandmarks = mutableMapOf<String, String>() // landmarkId -> alertId

    private val fusedListener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            location ?: return
            emit(location.latitude, location.longitude, location.heading, location.timestampMillis, null)
        }

        override fun onError(exception: Exception) {
            Log.w(TAG, "FusedLocation error: ${exception.message}")
            if (_status.value.state == SdkState.CONNECTING) startPlatformFallback("FusedLocation 오류: ${exception.message}")
        }
    }

    private val platformListener = LocationListener { loc: Location ->
        emit(loc.latitude, loc.longitude, if (loc.hasBearing()) loc.bearing else Float.NaN, System.currentTimeMillis(), if (loc.hasSpeed()) loc.speed * 3.6f else null)
    }

    fun start(onFix: (TrackPoint) -> Unit) {
        this.onFix = onFix
        runCatching {
            val f = FusedLocation(context.applicationContext)
            f.initialize()
            if (!f.isSupported()) error("이 차량은 FusedLocation 을 지원하지 않음")
            f.registerFusedLocationCallback(fusedListener)
            f.getLastKnownLocation(fusedListener)
            fused = f
            _status.value = SdkStatus("FusedLocation", SdkState.PLEOS, "차량 융합 위치 수신")
        }.onFailure {
            Log.w(TAG, "FusedLocation unavailable", it)
            fused?.runCatching { release() }
            fused = null
            startPlatformFallback(it.message ?: "연결 실패")
        }
    }

    @SuppressLint("MissingPermission")
    private fun startPlatformFallback(reason: String) {
        val lm = context.getSystemService(LocationManager::class.java)
        runCatching {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, platformListener, Looper.getMainLooper())
            _status.value = SdkStatus("FusedLocation", SdkState.FALLBACK, "AAOS 위치로 대체 ($reason)")
        }.onFailure {
            _status.value = SdkStatus("FusedLocation", SdkState.UNAVAILABLE, it.message ?: reason)
        }
    }

    private fun emit(lat: Double, lng: Double, heading: Float, time: Long, speedKmh: Float?) {
        val prev = last
        val dist = prev?.let { GeoMath.distanceM(it.lat, it.lng, lat, lng) } ?: 0.0
        val dt = prev?.let { (time - it.timeMs).coerceAtLeast(1) } ?: 1L
        val speed = speedKmh ?: if (prev != null) (dist / (dt / 1000.0) * 3.6).toFloat() else 0f
        val h = when {
            !heading.isNaN() && heading != 0f -> heading
            prev != null && dist > 3 -> GeoMath.bearingDeg(prev.lat, prev.lng, lat, lng).toFloat()
            else -> prev?.headingDeg ?: 0f
        }
        val p = TrackPoint(lat, lng, time, h, speed)
        last = p
        onFix?.invoke(p)
    }

    /** 진행 방향 앞 장소를 Proximity Alert 로 등록. 이미 등록된 곳은 건너뛴다. */
    fun watch(landmarkId: String, lat: Double, lng: Double, radiusM: Float, onEnter: (String) -> Unit) {
        val f = fused ?: return
        if (registeredLandmarks.containsKey(landmarkId) || registeredLandmarks.size >= MAX_ALERTS) return
        registeredLandmarks[landmarkId] = ""
        runCatching {
            f.addProximityAlert(
                ProximityAlertRequest(lat, lng, radiusM, setOf(ProximityTransition.ENTER)),
                object : ProximityAlertListener {
                    override fun onRegistered(alertId: String) {
                        alertIds[alertId] = landmarkId
                        registeredLandmarks[landmarkId] = alertId
                    }

                    override fun onTransition(event: ProximityAlertEvent) {
                        val id = alertIds[event.alertId] ?: return
                        if (event.transition == ProximityTransition.ENTER) onEnter(id)
                    }

                    override fun onError(exception: Exception) {
                        registeredLandmarks.remove(landmarkId)
                        Log.w(TAG, "Proximity alert error: ${exception.message}")
                    }
                },
            )
        }.onFailure { registeredLandmarks.remove(landmarkId) }
    }

    fun unwatchAll() {
        val f = fused ?: return
        registeredLandmarks.values.filter { it.isNotEmpty() }.forEach { runCatching { f.removeProximityAlert(it) } }
        registeredLandmarks.clear()
        alertIds.clear()
    }

    val proximitySupported get() = fused != null

    fun stop() {
        unwatchAll()
        fused?.runCatching {
            unregisterFusedLocationCallback(fusedListener)
            release()
        }
        fused = null
        runCatching { context.getSystemService(LocationManager::class.java).removeUpdates(platformListener) }
    }

    companion object {
        private const val TAG = "WT/Location"
        private const val MAX_ALERTS = 20
    }
}
