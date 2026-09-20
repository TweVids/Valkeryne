package com.twevids.valkeryne.data

import android.content.Context
import android.content.SharedPreferences
import com.twevids.valkeryne.model.AppSettings
import com.twevids.valkeryne.model.LiveModels

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("valkeryne_settings_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL_ID = "model_id"
        private const val KEY_VOICE_NAME = "voice_name"
        private const val KEY_SYSTEM_INSTRUCTION = "system_instruction"
    }

    fun loadSettings(): AppSettings {
        return AppSettings(
            apiKey = prefs.getString(KEY_API_KEY, "") ?: "",
            modelId = prefs.getString(KEY_MODEL_ID, "gemini-3.8-live") ?: "gemini-3.8-live",
            voiceName = prefs.getString(KEY_VOICE_NAME, "Puck") ?: "Puck",
            systemInstruction = prefs.getString(
                KEY_SYSTEM_INSTRUCTION,
                LiveModels.DEFAULT_SYSTEM_INSTRUCTION
            ) ?: LiveModels.DEFAULT_SYSTEM_INSTRUCTION
        )
    }

    fun saveSettings(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_API_KEY, settings.apiKey.trim())
            .putString(KEY_MODEL_ID, settings.modelId)
            .putString(KEY_VOICE_NAME, settings.voiceName)
            .putString(KEY_SYSTEM_INSTRUCTION, settings.systemInstruction.trim())
            .apply()
    }
}
