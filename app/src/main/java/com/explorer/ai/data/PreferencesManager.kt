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
        private val CHAT_HISTORY = stringPreferencesKey("chat_history_json")
        private val SELECTED_MODEL = stringPreferencesKey("selected_model")

        // Single source of truth for the default model name.
        // "gemini-1.5-flash" is removed from the v1beta endpoint — use 2.0-flash.
        const val DEFAULT_MODEL = "gemini-2.0-flash"

        val AVAILABLE_MODELS = listOf(
            "gemini-2.0-flash",
            "gemini-2.0-flash-lite",
            "gemini-1.5-flash-latest",
            "gemini-1.5-pro-latest"
        )
    }

    val apiKeyFlow: Flow<String?> = context.dataStore.data.map { it[GEMINI_API_KEY] }

    val chatHistoryFlow: Flow<String?> = context.dataStore.data.map { it[CHAT_HISTORY] }

    // Emits the saved model name, falling back to DEFAULT_MODEL if none saved.
    val selectedModelFlow: Flow<String> = context.dataStore.data.map {
        it[SELECTED_MODEL] ?: DEFAULT_MODEL
    }

    suspend fun saveApiKey(key: String) {
        context.dataStore.edit { it[GEMINI_API_KEY] = key }
    }

    suspend fun clearApiKey() {
        context.dataStore.edit { it.remove(GEMINI_API_KEY) }
    }

    suspend fun saveChatHistory(jsonArrayString: String) {
        context.dataStore.edit { it[CHAT_HISTORY] = jsonArrayString }
    }

    suspend fun clearChatHistory() {
        context.dataStore.edit { it.remove(CHAT_HISTORY) }
    }

    suspend fun saveSelectedModel(modelName: String) {
        context.dataStore.edit { it[SELECTED_MODEL] = modelName }
    }
}
