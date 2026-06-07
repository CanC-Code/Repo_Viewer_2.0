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
    }

    val apiKeyFlow: Flow<String?> = context.dataStore.data.map { it[GEMINI_API_KEY] }
    
    val chatHistoryFlow: Flow<String?> = context.dataStore.data.map { it[CHAT_HISTORY] }

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
}
