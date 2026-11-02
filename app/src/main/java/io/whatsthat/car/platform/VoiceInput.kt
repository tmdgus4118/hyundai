package io.whatsthat.car.platform

import android.content.Context
import android.util.Log
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.OnServerConnectionListener
import ai.pleos.playground.stt.listener.ResultListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/** 음성 질의 입력. Pleos SpeechToText SDK 로 차량 마이크 음성을 인식한다. */
class VoiceInput(private val context: Context, private val clientId: String, private val clientSecret: String) {

    enum class Phase { IDLE, LISTENING, RECOGNIZING }

    private val _status = MutableStateFlow(SdkStatus("SpeechToText", SdkState.CONNECTING))
    val status: StateFlow<SdkStatus> = _status
    private val _phase = MutableStateFlow(Phase.IDLE)
    val phase: StateFlow<Phase> = _phase
    private val _partial = MutableStateFlow("")
    val partial: StateFlow<String> = _partial
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level

    private var stt: SpeechToText? = null
    private var ready = false
    private var onFinal: ((String) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val timeout = Runnable {
        if (_phase.value != Phase.IDLE) {
            cancel()
            onError?.invoke("말씀을 듣지 못했어요. 다시 눌러 주세요.")
        }
    }

    private val listener = object : ResultListener {
        override fun onReady() { _phase.value = Phase.LISTENING }
        override fun onStartedRecognition() { _phase.value = Phase.RECOGNIZING }
        override fun onUpdated(stt: String, completed: Boolean) {
            _partial.value = stt
            if (completed && stt.isNotBlank()) {
                handler.removeCallbacks(timeout)
                _phase.value = Phase.IDLE
                onFinal?.invoke(stt)
                onFinal = null
            }
        }
        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onUpdatedRms(rms: Double) { _level.value = (rms / 10.0).toFloat().coerceIn(0f, 1f) }
        override fun onEndedRecognition() {
            _phase.value = Phase.IDLE
            _level.value = 0f
        }
        override fun onError(errorType: ErrorType) {
            handler.removeCallbacks(timeout)
            _phase.value = Phase.IDLE
            Log.w(TAG, "STT error: $errorType")
            onError?.invoke(message(errorType))
            onFinal = null
        }
    }

    val available get() = ready

    fun start() {
        val useServer = clientId.isNotBlank() && clientSecret.isNotBlank()
        runCatching {
            val s = SpeechToText(context.applicationContext, if (useServer) Mode.HYBRID else Mode.ON_DEVICE)
            stt = s
            s.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    s.addListener(listener)
                    s.setLocale(Locale.KOREA)
                    if (useServer) {
                        s.registerApp(clientId, clientSecret, object : OnServerConnectionListener {
                            override fun onConnected() = markReady("HYBRID · 서버 연결")
                            override fun onFailed(errorType: ErrorType) = markReady("온디바이스 ($errorType)")
                        })
                    } else markReady("ON_DEVICE 엔진")
                }

                override fun onFailure(errorType: ErrorType) {
                    _status.value = SdkStatus("SpeechToText", SdkState.UNAVAILABLE, "초기화 실패: $errorType")
                }
            })
        }.onFailure { _status.value = SdkStatus("SpeechToText", SdkState.UNAVAILABLE, it.message ?: "연결 실패") }
    }

    private fun markReady(note: String) {
        ready = true
        _status.value = SdkStatus("SpeechToText", SdkState.PLEOS, note)
    }

    fun listen(onFinal: (String) -> Unit, onError: (String) -> Unit) {
        val s = stt
        if (!ready || s == null) { onError("음성 인식을 사용할 수 없어요. 아래 버튼으로 물어봐 주세요."); return }
        this.onFinal = onFinal
        this.onError = onError
        _partial.value = ""
        _phase.value = Phase.LISTENING
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, LISTEN_TIMEOUT_MS)
        runCatching { s.request() }.onFailure {
            _phase.value = Phase.IDLE
            onError("음성 인식을 시작하지 못했어요.")
        }
    }

    fun cancel() {
        handler.removeCallbacks(timeout)
        onFinal = null
        if (_phase.value != Phase.IDLE) stt?.runCatching { stop() }
        _phase.value = Phase.IDLE
    }

    fun release() {
        stt?.runCatching { removeListener(listener); release() }
        stt = null
    }

    private fun message(e: ErrorType) = when (e) {
        ErrorType.MIC_ERROR -> "마이크를 사용할 수 없어요."
        ErrorType.PERMISSION_DENIED -> "음성 인식 권한이 없어요."
        ErrorType.TIMEOUT -> "말씀을 듣지 못했어요. 다시 눌러 주세요."
        ErrorType.NETWORK_ERROR -> "네트워크가 불안정해요."
        else -> "음성을 알아듣지 못했어요. 다시 말해 주세요."
    }

    companion object {
        private const val TAG = "WT/STT"
        private const val LISTEN_TIMEOUT_MS = 8_000L
    }
}
