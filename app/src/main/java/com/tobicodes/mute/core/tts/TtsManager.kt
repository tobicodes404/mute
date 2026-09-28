package com.tobicodes.mute.core.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

data class Sentence(val pageIndex: Int, val text: String)

class TtsManager(private val context: Context) {

    enum class State { IDLE, PLAYING, PAUSED }

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex

    private var tts: TextToSpeech? = null
    private var sentences: List<Sentence> = emptyList()
    private var speed = 1.0f
    private var pitch = 1.0f
    private var lastUtterId: String? = null
    private var ready = false

    val isReady: Boolean get() = ready

    /** Initialize with a specific engine package (null = system default) */
    fun init(enginePackage: String? = null, onReady: (Boolean) -> Unit) {
        val listener = TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                tts?.setSpeechRate(speed)
                tts?.setPitch(pitch)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) {
                        if (id == lastUtterId && _state.value == State.PLAYING) advance()
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(id: String?) {
                        if (id == lastUtterId && _state.value == State.PLAYING) advance()
                    }
                })
                onReady(true)
            } else {
                onReady(false)
            }
        }
        tts = if (enginePackage != null) {
            TextToSpeech(context, listener, enginePackage)
        } else {
            TextToSpeech(context, listener)
        }
    }

    fun configureFor(locale: Locale): Boolean {
        val t = tts ?: return false
        val result = t.setLanguage(locale)
        val supported = result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED
        if (supported) preferFemaleVoiceFor(locale)
        return supported
    }

    private fun preferFemaleVoiceFor(locale: Locale) {
        runCatching {
            val candidates = tts?.voices?.filter { it.locale.language == locale.language } ?: return
            val female = candidates.firstOrNull { it.name.contains("female", true) }
                ?: candidates.firstOrNull { it.name.contains("sfg") || it.name.contains("sfa") }
            if (female != null) tts?.voice = female
        }
    }

    fun load(newSentences: List<Sentence>) {
        sentences = newSentences
    }

    val sentenceCount: Int get() = sentences.size

    fun playPause() {
        when (_state.value) {
            State.PLAYING -> pause()
            State.PAUSED -> resume()
            State.IDLE -> playFrom(if (_currentIndex.value < 0) 0 else _currentIndex.value)
        }
    }

    fun playFrom(index: Int) {
        if (!ready || sentences.isEmpty()) return
        _currentIndex.value = index.coerceIn(0, sentences.size - 1)
        _state.value = State.PLAYING
        speakCurrent()
    }

    fun pause() {
        _state.value = State.PAUSED
        tts?.stop()
    }

    fun resume() {
        if (sentences.isEmpty()) return
        _state.value = State.PLAYING
        speakCurrent()
    }

    fun nextSentence() {
        if (_currentIndex.value < sentences.size - 1) playFrom(_currentIndex.value + 1)
    }

    fun previousSentence() {
        if (_currentIndex.value > 0) playFrom(_currentIndex.value - 1)
    }

    fun stop() {
        _state.value = State.IDLE
        tts?.stop()
        _currentIndex.value = -1
    }

    fun setSpeed(s: Float) {
        speed = s
        tts?.setSpeechRate(s)
        if (_state.value == State.PLAYING) speakCurrent()
    }

    fun setPitch(p: Float) {
        pitch = p
        val res = tts?.setPitch(p) ?: android.speech.tts.TextToSpeech.SUCCESS
        context.getSharedPreferences("mute_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("pitch_supported", res != android.speech.tts.TextToSpeech.ERROR).apply()
        if (_state.value == State.PLAYING) speakCurrent()
    }

    private fun speakCurrent() {
        val t = tts ?: return
        val i = _currentIndex.value
        if (i !in sentences.indices) {
            _state.value = State.IDLE
            return
        }
        val id = "utter_$i"
        lastUtterId = id
        t.speak(sentences[i].text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    private fun advance() {
        if (_currentIndex.value < sentences.size - 1) {
            _currentIndex.value += 1
            speakCurrent()
        } else {
            _state.value = State.IDLE
            _currentIndex.value = -1
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}
