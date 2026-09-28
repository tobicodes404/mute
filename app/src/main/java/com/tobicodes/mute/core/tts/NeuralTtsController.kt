package com.tobicodes.mute.core.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.k2fsa.sherpa.onnx.Tts
import com.k2fsa.sherpa.onnx.TtsConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

/** Offline neural TTS (VITS-MMS Bangla) — human-like female voice */
class NeuralTtsController(private val context: Context) {

    private val _state = MutableStateFlow(TtsManager.State.IDLE)
    val state: StateFlow<TtsManager.State> = _state

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex

    private var tts: Tts? = null
    private var sentences: List<Sentence> = emptyList()
    private var track: AudioTrack? = null
    private var playing = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Loads model from internal storage (auto-copies from Download if needed) */
    fun init(): Boolean {
        if (tts != null) return true
        val dir = File(context.filesDir, "vits-bn-female")
        val src = File("/sdcard/Download/vits-bn-female")
        if (!File(dir, "tokens.txt").exists() && src.exists()) {
            runCatching { src.copyRecursively(dir, true) }
        }
        val modelFile = when {
            File(dir, "model.int8.onnx").exists() -> File(dir, "model.int8.onnx")
            File(dir, "model.onnx").exists() -> File(dir, "model.onnx")
            else -> return false
        }
        val tokens = File(dir, "tokens.txt")
        if (!tokens.exists()) return false
        val lexicon = File(dir, "lexicon.txt")
        val dataDir = File(dir, "espeak-ng-data")
        return runCatching {
            val cfg = TtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = modelFile.absolutePath,
                        lexicon = if (lexicon.exists()) lexicon.absolutePath else "",
                        tokens = tokens.absolutePath,
                        dataDir = if (dataDir.exists()) dataDir.absolutePath else "",
                    ),
                    numThreads = 2,
                ),
                ruleFsts = "",
            )
            tts = Tts(cfg, context)
            true
        }.getOrDefault(false)
    }

    fun load(s: List<Sentence>) { sentences = s }

    fun playPause() {
        when (_state.value) {
            TtsManager.State.PLAYING -> pause()
            else -> playFrom(if (_currentIndex.value < 0) 0 else _currentIndex.value)
        }
    }

    fun playFrom(index: Int) {
        if (sentences.isEmpty()) return
        _currentIndex.value = index.coerceIn(0, sentences.size - 1)
        _state.value = TtsManager.State.PLAYING
        playing = true
        scope.launch { runLoop() }
    }

    private fun runLoop() {
        while (playing && _currentIndex.value in sentences.indices) {
            val text = sentences[_currentIndex.value].text
            val t = tts ?: break
            val samples = runCatching { t.generate(text, 0, 1.0f) }.getOrNull()
            if (samples == null || samples.isEmpty()) {
                advance()
                continue
            }
            playSamples(samples, t.sampleRate)
            advance()
        }
        if (!playing) return
        _state.value = TtsManager.State.IDLE
        _currentIndex.value = -1
    }

    private fun advance() {
        if (_currentIndex.value < sentences.size - 1) _currentIndex.value += 1
        else playing = false
    }

    private fun playSamples(samples: FloatArray, sampleRate: Int) {
        val shorts = ShortArray(samples.size) {
            (samples[it] * 32767).toInt().coerceIn(-32768, 32767).toShort()
        }
        val bufSize = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val at = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
            bufSize * 2,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        track = at
        at.play()
        var i = 0
        while (playing && i < shorts.size) {
            val n = minOf(4096, shorts.size - i)
            at.write(shorts, i, n)
            i += n
        }
        runCatching { at.stop(); at.release() }
        track = null
    }

    fun pause() {
        playing = false
        _state.value = TtsManager.State.PAUSED
        runCatching { track?.stop() }
    }

    fun stop() {
        playing = false
        _state.value = TtsManager.State.IDLE
        _currentIndex.value = -1
        runCatching { track?.stop() }
    }

    fun nextSentence() { if (_currentIndex.value < sentences.size - 1) playFrom(_currentIndex.value + 1) }
    fun previousSentence() { if (_currentIndex.value > 0) playFrom(_currentIndex.value - 1) }

    fun release() {
        stop()
        scope.cancel()
        runCatching { tts?.release() }
    }
}
