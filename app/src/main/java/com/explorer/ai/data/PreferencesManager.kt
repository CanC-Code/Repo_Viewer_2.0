package com.explorer.ai.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "explorer_settings")

class PreferencesManager(private val context: Context) {

    companion object {
        private val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        private val SELECTED_MODEL  = stringPreferencesKey("selected_model")

        // Free-tier models as of June 2026.
        // gemini-2.0-* shut down 2026-06-01 — excluded.
        // gemini-2.5-pro included but flagged: only 50 RPD on free tier.
        val AVAILABLE_MODELS = listOf(
            GeminiModel("gemini-2.5-flash",      "2.5 Flash",      "Recommended — fast, capable, 1500 RPD"),
            GeminiModel("gemini-2.5-flash-lite",  "2.5 Flash Lite", "Fastest, lowest cost, 1500 RPD"),
            GeminiModel("gemini-3-flash-preview", "3 Flash Preview","Newest generation, 1500 RPD"),
            GeminiModel("gemini-2.5-pro",         "2.5 Pro",        "Most capable — free tier 50 RPD only")
        )

        val DEFAULT_MODEL = AVAILABLE_MODELS[0]
    }

    val apiKeyFlow: Flow<String?> = context.dataStore.data
        .map { it[GEMINI_API_KEY] }

    val selectedModelFlow: Flow<String> = context.dataStore.data
        .map { it[SELECTED_MODEL] ?: DEFAULT_MODEL.id }

    suspend fun saveApiKey(key: String) {
        context.dataStore.edit { it[GEMINI_API_KEY] = key }
    }

    suspend fun saveSelectedModel(modelId: String) {
        context.dataStore.edit { it[SELECTED_MODEL] = modelId }
    }

    suspend fun clearApiKey() {
        context.dataStore.edit { it.remove(GEMINI_API_KEY) }
    }
}

data class GeminiModel(
    val id: String,
    val displayName: String,
    val description: String
)
