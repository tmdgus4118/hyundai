package io.whatsthat.car

import android.app.Application
import io.whatsthat.car.data.JourneyStore
import io.whatsthat.car.data.LandmarkRepository
import io.whatsthat.car.platform.LocationProvider
import io.whatsthat.car.platform.NaviBridge
import io.whatsthat.car.platform.VehicleMonitor
import io.whatsthat.car.platform.VoiceInput
import io.whatsthat.car.platform.VoiceOutput

/** 앱 전역 의존성. SDK 연결은 Activity 수명과 무관하게 한 번만 맺는다. */
class WhatsThatApp : Application() {
    val landmarks by lazy { LandmarkRepository(this) }
    val journey by lazy { JourneyStore(this) }
    val location by lazy { LocationProvider(this) }
    val vehicle by lazy { VehicleMonitor(this) }
    val voiceOut by lazy { VoiceOutput(this, BuildConfig.PLEOS_CLIENT_ID, BuildConfig.PLEOS_CLIENT_SECRET) }
    val voiceIn by lazy { VoiceInput(this, BuildConfig.PLEOS_CLIENT_ID, BuildConfig.PLEOS_CLIENT_SECRET) }
    val navi by lazy { NaviBridge(this) }
}
