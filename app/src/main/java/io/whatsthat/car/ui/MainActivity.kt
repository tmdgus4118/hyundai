package io.whatsthat.car.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import io.whatsthat.car.BuildConfig
import io.whatsthat.car.ui.screen.MainScreen
import io.whatsthat.car.ui.theme.WhatsThatTheme

class MainActivity : ComponentActivity() {

    private val vm: WhatsThatViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.start() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WhatsThatTheme {
                val state by vm.ui.collectAsStateWithLifecycle()
                MainScreen(state, vm)
            }
        }
        if (BuildConfig.DEBUG) registerDebugAsk()
        val missing = RUNTIME_PERMISSIONS.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) vm.start() else permissionLauncher.launch(missing.toTypedArray())
    }

    /**
     * 디버그 빌드 전용: 음성 인식 결과를 adb 로 주입해 음성 명령 전체 흐름을 검증한다.
     *   adb shell am broadcast -a io.whatsthat.car.DEBUG_ASK --es q "방금 왼쪽에 지나간 거 뭐야"
     */
    private fun registerDebugAsk() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { i.getStringExtra("q")?.let(vm::ask) }
        }
        ContextCompat.registerReceiver(this, receiver, IntentFilter(DEBUG_ASK), ContextCompat.RECEIVER_EXPORTED)
        debugReceiver = receiver
    }

    private var debugReceiver: BroadcastReceiver? = null

    override fun onDestroy() {
        debugReceiver?.let { unregisterReceiver(it) }
        super.onDestroy()
    }

    companion object {
        private const val DEBUG_ASK = "io.whatsthat.car.DEBUG_ASK"

        /** dangerous 보호 수준이라 실행 중 동의가 필요한 권한. 위치·차속은 "지나친 곳" 계산에만 쓰고 외부로 보내지 않는다. */
        val RUNTIME_PERMISSIONS = arrayOf(
            "pleos.car.permission.FUSED_LOCATION",
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            "android.car.permission.CAR_SPEED",
        )
    }
}
