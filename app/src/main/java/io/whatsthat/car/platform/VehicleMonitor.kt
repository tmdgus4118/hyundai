package io.whatsthat.car.platform

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** 앱이 쓰는 차량 상태 요약. */
data class VehicleState(
    val gear: Gear = Gear.UNKNOWN,
    val turnSignal: TurnSignalValue = TurnSignalValue.OFF,
    val wiperOn: Boolean = false,
    val headlightOn: Boolean = false,
    val speedKmh: Float? = null,
    val tripKm: Float? = null,
    val uxRestricted: Boolean = false,
) {
    enum class Gear(val label: String) { PARK("P"), REVERSE("R"), NEUTRAL("N"), DRIVE("D"), UNKNOWN("-") }

    /** 정차(P) 이고 주행 규제가 없을 때만 상세 화면을 연다. */
    val parked get() = gear == Gear.PARK && !uxRestricted
    val signalling get() = turnSignal != TurnSignalValue.OFF
}

/**
 * Vehicle SDK 로 기어·방향지시등·와이퍼·전조등·트립 거리를 구독하고,
 * AAOS 표준 API 로 주행 규제(CarUxRestrictions)와 클러스터 차속(PERF_VEHICLE_SPEED)을 함께 본다.
 * (SDK API 정책: 표준 데이터는 AAOS, 특화 기능은 Pleos SDK)
 */
class VehicleMonitor(private val context: Context) {

    private val _state = MutableStateFlow(VehicleState())
    val state: StateFlow<VehicleState> = _state
    private val _status = MutableStateFlow(SdkStatus("Vehicle", SdkState.CONNECTING))
    val status: StateFlow<SdkStatus> = _status

    private var vehicle: Vehicle? = null
    private var car: Car? = null
    private var uxManager: CarUxRestrictionsManager? = null
    private var propertyManager: CarPropertyManager? = null
    private val frontWindshield = WindowArea(WindowValue.FRONT_WINDSHIELD)
    private val subscribed = mutableListOf<String>()

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            Log.d(TAG, "gear changed: $gear")
            _state.update { it.copy(gear = map(gear)) }
        }
        override fun onFailed(e: Exception) = fail("gear", e)
    }
    private val turnListener = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(state: TurnSignalValue?) = _state.update { it.copy(turnSignal = state ?: TurnSignalValue.OFF) }
        override fun onFailed(e: Exception) = fail("turnSignal", e)
    }
    private val wiperListener = object : WiperStateListener {
        override fun onWiperStateUpdated(state: WiperState?) = _state.update { it.copy(wiperOn = state == WiperState.ON) }
        override fun onFailed(e: Exception) = fail("wiper", e)
    }
    private val lightListener = object : LightStateListener {
        override fun onLightStateUpdated(state: LightState?) = _state.update { it.copy(headlightOn = state == LightState.ON) }
        override fun onFailed(e: Exception) = fail("lowBeam", e)
    }
    private val tripListener = object : DistanceDrivenListener {
        override fun onDistanceDrivenUpdated(value: Float?) = _state.update { it.copy(tripKm = value) }
        override fun onFailed(e: Exception) = fail("tripDistance", e)
    }
    private val uxListener = CarUxRestrictionsManager.OnUxRestrictionsChangedListener { r -> applyUx(r) }
    private val speedCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            (value.value as? Float)?.let { mps -> _state.update { it.copy(speedKmh = mps * 3.6f) } }
        }
        override fun onErrorEvent(propId: Int, zone: Int) = Unit
    }

    fun start() {
        startPleosVehicle()
        startAaos()
    }

    private fun startPleosVehicle() {
        runCatching {
            val v = Vehicle(context.applicationContext)
            v.initialize()
            vehicle = v
            v.getGear().getCurrentGearSelectionState({ g -> Log.d(TAG, "gear initial: $g"); _state.update { it.copy(gear = map(g)) } }, { fail("gear", it) })
            v.getGear().registerGearSelection(gearListener); subscribed += "기어"
            // 등록 직후 캐시된 이전 값이 늦게 도착하는 경우가 있어 한 번 더 현재 값을 맞춘다
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                vehicle?.getGear()?.getCurrentGearSelectionState({ g -> _state.update { it.copy(gear = map(g)) } }, { fail("gear", it) })
            }, 1_500L)
            v.getTurnSignal().getCurrentTurnSignalState({ t -> _state.update { it.copy(turnSignal = t ?: TurnSignalValue.OFF) } }, { fail("turnSignal", it) })
            v.getTurnSignal().registerTurnSignalState(turnListener); subscribed += "방향지시등"
            v.getWiper().registerWiperState(frontWindshield, wiperListener); subscribed += "와이퍼"
            v.getLight().registerLowBeamLightState(lightListener); subscribed += "전조등"
            v.getOdometer().registerTripCurrentDrivingDistance(tripListener); subscribed += "트립 거리"
            _status.value = SdkStatus("Vehicle", SdkState.PLEOS, subscribed.joinToString(" · ") + " 구독")
        }.onFailure {
            Log.w(TAG, "Vehicle SDK unavailable", it)
            _status.value = SdkStatus("Vehicle", SdkState.UNAVAILABLE, it.message ?: "연결 실패")
        }
    }

    private fun startAaos() {
        runCatching {
            val c = Car.createCar(context.applicationContext) ?: return
            car = c
            uxManager = (c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager)?.also {
                it.registerListener(uxListener)
                applyUx(it.currentCarUxRestrictions)
            }
            propertyManager = (c.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager)?.also {
                it.registerCallback(speedCallback, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_ONCHANGE)
            }
        }.onFailure { Log.w(TAG, "AAOS car service unavailable: ${it.message}") }
    }

    private fun applyUx(r: CarUxRestrictions?) {
        r ?: return
        _state.update { it.copy(uxRestricted = r.isRequiresDistractionOptimization) }
    }

    private fun fail(what: String, e: Exception) {
        Log.w(TAG, "$what failed: ${e.message}")
        // 미지원 속성은 구독 목록에서 빼고 상태에 표시한다 (기능은 계속 동작)
        LABELS[what]?.let { label ->
            if (subscribed.remove(label) && vehicle != null) {
                _status.value = SdkStatus("Vehicle", SdkState.PLEOS, subscribed.joinToString(" · ") + " 구독 ($label 미지원)")
            }
        }
    }

    /** 시연 모드에서 Virtual Console 없이 기어를 바꿔볼 때 사용. */
    fun overrideGear(gear: VehicleState.Gear) = _state.update { it.copy(gear = gear) }

    fun stop() {
        vehicle?.runCatching {
            getGear().unregisterGearSelection(gearListener)
            getTurnSignal().unregisterTurnSignalState(turnListener)
            getWiper().unregisterWiperState(frontWindshield, wiperListener)
            getLight().unregisterLowBeamLightState(lightListener)
            getOdometer().unregisterTripCurrentDrivingDistance(tripListener)
            release()
        }
        vehicle = null
        runCatching { uxManager?.unregisterListener() }
        runCatching { propertyManager?.unregisterCallback(speedCallback) }
        runCatching { car?.disconnect() }
        car = null
    }

    private fun map(g: VehicleGear?) = when (g) {
        VehicleGear.GEAR_PARK -> VehicleState.Gear.PARK
        VehicleGear.GEAR_REVERSE -> VehicleState.Gear.REVERSE
        VehicleGear.GEAR_NEUTRAL -> VehicleState.Gear.NEUTRAL
        null, VehicleGear.GEAR_UNKNOWN -> VehicleState.Gear.UNKNOWN
        else -> VehicleState.Gear.DRIVE
    }

    companion object {
        private const val TAG = "WT/Vehicle"
        private val LABELS = mapOf("gear" to "기어", "turnSignal" to "방향지시등", "wiper" to "와이퍼", "lowBeam" to "전조등", "tripDistance" to "트립 거리")
    }
}
