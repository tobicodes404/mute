package com.tobicodes.mute.core.tts

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo

data class TtsEngine(val packageName: String, val label: String)

object TtsEngineHelper {

    fun installedEngines(context: Context): List<TtsEngine> {
        val pm = context.packageManager
        val intent = Intent("android.intent.action.TTS_SERVICE")
        val services: List<ResolveInfo> = pm.queryIntentServices(intent, 0) ?: emptyList()
        return services.map { info ->
            TtsEngine(
                packageName = info.serviceInfo.packageName,
                label = info.loadLabel(pm).toString()
            )
        }
    }

    fun savedEngine(context: Context): String? =
        context.getSharedPreferences("mute_prefs", Context.MODE_PRIVATE)
            .getString("tts_engine", null)

    fun saveEngine(context: Context, packageName: String?) {
        context.getSharedPreferences("mute_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("tts_engine", packageName)
            .apply()
    }
}
