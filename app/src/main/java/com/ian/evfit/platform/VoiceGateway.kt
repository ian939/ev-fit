// 음성 안내(TTS)와 음성 명령(STT) 게이트웨이 — Pleos ON_DEVICE 우선, TTS는 안드로이드 기본 엔진으로 대체
package com.ian.evfit.platform

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.stt.constant.Mode as SttMode
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInit
import ai.pleos.playground.tts.TextToSpeech as PleosTts
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInit
import android.speech.tts.TextToSpeech as AndroidTts

interface VoiceGateway {
    val available: StateFlow<Boolean>
    val sttAvailable: StateFlow<Boolean>
    fun start()
    fun speak(text: String)
    fun listen(onText: (String) -> Unit)
    fun stop()
}

class PleosVoiceGateway(private val context: Context) : VoiceGateway {
    private val _available = MutableStateFlow(false)
    private val _stt = MutableStateFlow(false)
    override val available: StateFlow<Boolean> = _available
    override val sttAvailable: StateFlow<Boolean> = _stt

    private var pleosTts: PleosTts? = null
    private var pleosReady = false
    private var androidTts: AndroidTts? = null
    private var androidReady = false
    private var stt: SpeechToText? = null
    private var pending: ((String) -> Unit)? = null

    override fun start() {
        runCatching {
            val tts = PleosTts(context, TtsMode.ON_DEVICE)
            pleosTts = tts
            tts.initialize(object : TtsInit {
                override fun onSuccess() {
                    pleosReady = true
                    _available.value = true
                }

                override fun onFailure(errorType: TtsError) {
                    Log.w(TAG, "Pleos TTS 초기화 실패: $errorType — 안드로이드 TTS로 대체")
                    startAndroidTts()
                }
            })
        }.onFailure {
            Log.w(TAG, "Pleos TTS 생성 실패: $it — 안드로이드 TTS로 대체")
            startAndroidTts()
        }

        runCatching {
            val s = SpeechToText(context, SttMode.ON_DEVICE)
            stt = s
            s.addListener(object : ResultListener {
                override fun onUpdated(stt: String, completed: Boolean) {
                    if (completed && stt.isNotBlank()) pending?.invoke(stt).also { pending = null }
                }

                override fun onUpdatedEpdData(on: Long, off: Long) {}
                override fun onStartedRecognition() {}
                override fun onEndedRecognition() {}
                override fun onError(errorType: SttError) {
                    Log.w(TAG, "STT 오류: $errorType")
                    pending = null
                }

                override fun onReady() {}
                override fun onUpdatedRms(rms: Double) {}
            })
            s.initialize(object : SttInit {
                override fun onSuccess() {
                    _stt.value = true
                }

                override fun onFailure(errorType: SttError) {
                    Log.w(TAG, "STT 초기화 실패: $errorType")
                }
            })
        }.onFailure { Log.w(TAG, "STT 생성 실패: $it") }
    }

    private fun startAndroidTts() {
        if (androidTts != null) return
        androidTts = AndroidTts(context) { status ->
            if (status == AndroidTts.SUCCESS) {
                androidTts?.language = Locale.KOREAN
                androidReady = true
                _available.value = true
            }
        }
    }

    override fun speak(text: String) {
        when {
            pleosReady -> runCatching { pleosTts?.speak(text, true) }.onFailure { Log.w(TAG, "TTS 실패: $it") }
            androidReady -> androidTts?.speak(text, AndroidTts.QUEUE_FLUSH, null, "evfit")
            else -> Log.i(TAG, "TTS 미사용: $text")
        }
    }

    override fun listen(onText: (String) -> Unit) {
        val s = stt ?: return
        pending = onText
        runCatching { s.request() }.onFailure {
            Log.w(TAG, "STT 요청 실패: $it")
            pending = null
        }
    }

    override fun stop() {
        runCatching { pleosTts?.release() }
        runCatching { androidTts?.shutdown() }
        runCatching { stt?.release() }
    }
}
