package com.k2fsa.sherpa.onnx

import android.content.Context

data class OfflineTtsVitsModelConfig(
    var model: String = "",
    var lexicon: String = "",
    var tokens: String = "",
    var dataDir: String = "",
    var noiseScale: Float = 0.667f,
    var noiseScaleW: Float = 0.8f,
    var lengthScale: Float = 1.0f,
)

data class OfflineTtsModelConfig(
    var vits: OfflineTtsVitsModelConfig = OfflineTtsVitsModelConfig(),
    var numThreads: Int = 1,
    var debug: Boolean = false,
    var provider: String = "cpu",
)

data class TtsConfig(
    var model: OfflineTtsModelConfig = OfflineTtsModelConfig(),
    var ruleFsts: String = "",
    var maxNumSentences: Int = 1,
)

/** JNI wrapper — class/package name MUST match native library symbols */
class Tts(var config: TtsConfig, var context: Context) {
    val ptr: Long = init(context, config)
    val sampleRate: Int = getSampleRate(ptr)

    fun generate(text: String, sid: Int = 0, speed: Float = 1.0f): FloatArray =
        generate(ptr, text, sid, speed)

    fun release() = release(ptr)

    private external fun init(context: Context, config: TtsConfig): Long
    private external fun release(ptr: Long)
    private external fun generate(ptr: Long, text: String, sid: Int, speed: Float): FloatArray
    private external fun getSampleRate(ptr: Long): Int

    companion object {
        init {
            System.loadLibrary("sherpa-onnx-jni")
        }
    }
}
