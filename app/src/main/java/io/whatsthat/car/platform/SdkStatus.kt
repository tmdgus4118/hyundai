package io.whatsthat.car.platform

/** 각 SDK 연동 상태. 정차 화면의 "연결 상태" 패널과 로그에 쓰인다. */
enum class SdkState(val label: String) {
    CONNECTING("연결 중"),
    PLEOS("Pleos SDK"),
    FALLBACK("대체 경로"),
    DEMO("시연 데이터"),
    UNAVAILABLE("사용 불가"),
}

data class SdkStatus(val name: String, val state: SdkState, val note: String = "")
