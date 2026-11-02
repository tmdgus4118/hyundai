package io.whatsthat.car.platform

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.TextToSpeech as AndroidTts
import android.util.Log
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import ai.pleos.playground.tts.listener.OnServerConnectionListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * 답변 음성 출력.
 * Pleos TextToSpeech SDK 를 기본으로 쓰고(차량 오디오 시스템·오디오 포커스 정책 연동),
 * 클라이언트 ID 가 있으면 HYBRID(서버 우선, 끊기면 온디바이스), 없으면 ON_DEVICE 로 동작한다.
 * SDK 를 쓸 수 없을 때만 Android TextToSpeech 로 대체한다.
 */
class VoiceOutput(private val context: Context, private val clientId: String, private val clientSecret: String) {

    private val _status = MutableStateFlow(SdkStatus("TextToSpeech", SdkState.CONNECTING))
    val status: StateFlow<SdkStatus> = _status
    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking

    private var pleos: TextToSpeech? = null
    private var pleosReady = false
    private var fallbackTts: AndroidTts? = null
    private var androidReady = false
    private val pending = ArrayDeque<String>()
    var onDone: (() -> Unit)? = null

    private val events = object : EventListener {
        override fun onReady() = Unit
        override fun onStart() { _speaking.value = true }
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onDone() { _speaking.value = false; onDone?.invoke() }
        override fun onStop() { _speaking.value = false }
        override fun onError(errorType: ErrorType) {
            _speaking.value = false
            Log.w(TAG, "Pleos TTS error: $errorType")
            if (errorType == ErrorType.PERMISSION_DENIED || errorType == ErrorType.ON_DEVICE_ENGINE_ERROR) fallback("Pleos TTS $errorType")
        }
    }

    fun start() {
        val useServer = clientId.isNotBlank() && clientSecret.isNotBlank()
        runCatching {
            val tts = TextToSpeech(context.applicationContext, if (useServer) Mode.HYBRID else Mode.ON_DEVICE)
            pleos = tts
            tts.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    tts.addEventListener(events)
                    tts.setLocale(Locale.KOREA)
                    if (useServer) {
                        tts.registerApp(clientId, clientSecret, object : OnServerConnectionListener {
                            override fun onConnected() = ready("HYBRID · 서버 연결")
                            override fun onFailed(errorType: ErrorType) = ready("온디바이스 ($errorType)")
                        })
                    } else ready("ON_DEVICE 엔진")
                }

                override fun onFailure(errorType: ErrorType) = fallback("초기화 실패: $errorType")
            })
        }.onFailure { fallback(it.message ?: "연결 실패") }
    }

    private fun ready(note: String) {
        pleosReady = true
        _status.value = SdkStatus("TextToSpeech", SdkState.PLEOS, note)
        flush()
    }

    private fun fallback(reason: String) {
        if (fallbackTts != null) return
        pleosReady = false
        fallbackTts = AndroidTts(context.applicationContext) { code ->
            androidReady = code == AndroidTts.SUCCESS
            fallbackTts?.language = Locale.KOREA
            fallbackTts?.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
            )
            fallbackTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) { _speaking.value = true }
                override fun onDone(id: String?) { _speaking.value = false; onDone?.invoke() }
                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) { _speaking.value = false }
            })
            _status.value = SdkStatus(
                "TextToSpeech",
                if (androidReady) SdkState.FALLBACK else SdkState.UNAVAILABLE,
                if (androidReady) "Android TTS 대체 ($reason)" else reason,
            )
            flush()
        }
    }

    fun speak(text: String) {
        stop()
        pending.clear()
        pending.addLast(text)
        flush()
    }

    private fun flush() {
        val text = pending.removeFirstOrNull() ?: return
        when {
            pleosReady -> pleos?.speak(text, true)
            androidReady -> fallbackTts?.speak(text, AndroidTts.QUEUE_FLUSH, null, "wt-${System.nanoTime()}")
            else -> pending.addFirst(text) // 초기화가 끝나면 말한다
        }
    }

    fun stop() {
        if (pleosReady) pleos?.runCatching { stop() }
        if (androidReady) fallbackTts?.stop()
        _speaking.value = false
    }

    fun release() {
        pleos?.runCatching { removeEventListener(events); release() }
        pleos = null
        fallbackTts?.shutdown()
        fallbackTts = null
    }

    companion object { private const val TAG = "WT/TTS" }
}
