package com.anomalyzed.simpletranscriber.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class PreferenceManager(private val context: Context) {

    companion object {
        // Keep "gemini_api_key" key name for backward compatibility with existing user data,
        // even though it represents the Google API Key.
        val API_KEY = stringPreferencesKey("gemini_api_key")
        val IS_PREMIUM = booleanPreferencesKey("is_premium")
        val LANGUAGE = stringPreferencesKey("language")
        val OPACITY = floatPreferencesKey("ui_opacity")
        val THEME = stringPreferencesKey("app_theme")
        val PLAYBACK_SPEED_MIN = floatPreferencesKey("playback_speed_min")
        val PLAYBACK_SPEED_MAX = floatPreferencesKey("playback_speed_max")
        val ENABLE_PROXIMITY = booleanPreferencesKey("enable_proximity")
        val DEFAULT_ACTION = stringPreferencesKey("default_action")
        val TRANSCRIPTION_ENGINE = stringPreferencesKey("transcription_engine")
        val SELECTED_MODEL_ID = stringPreferencesKey("selected_model_id")
        val SELECTED_CLOUD_MODEL = stringPreferencesKey("selected_cloud_model")
        val MODEL_CATALOG_URL = stringPreferencesKey("model_catalog_url")
        
        const val DEFAULT_CATALOG_URL =
            "https://raw.githubusercontent.com/CorsiDanilo/simple-transcription-app/main/models.json"
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            apiKey = prefs[API_KEY] ?: "",
            isPremium = prefs[IS_PREMIUM] ?: true,
            language = prefs[LANGUAGE] ?: "Italian",
            opacity = prefs[OPACITY] ?: 0.95f,
            theme = prefs[THEME] ?: "System",
            playbackMinSpeed = prefs[PLAYBACK_SPEED_MIN] ?: 0.75f,
            playbackMaxSpeed = prefs[PLAYBACK_SPEED_MAX] ?: 1.75f,
            enableProximity = prefs[ENABLE_PROXIMITY] ?: false,
            defaultAction = prefs[DEFAULT_ACTION] ?: "Show actions",
            transcriptionEngine = prefs[TRANSCRIPTION_ENGINE] ?: "cloud",
            selectedModelId = prefs[SELECTED_MODEL_ID] ?: "",
            selectedCloudModel = prefs[SELECTED_CLOUD_MODEL] ?: "gemini-flash-latest",
            modelCatalogUrl = prefs[MODEL_CATALOG_URL] ?: DEFAULT_CATALOG_URL
        )
    }

    suspend fun updateApiKey(key: String) = context.dataStore.edit { it[API_KEY] = key }
    suspend fun updatePremium(value: Boolean) = context.dataStore.edit { it[IS_PREMIUM] = value }
    suspend fun updateLanguage(value: String) = context.dataStore.edit { it[LANGUAGE] = value }
    suspend fun updateOpacity(value: Float) = context.dataStore.edit { it[OPACITY] = value }
    suspend fun updateTheme(value: String) = context.dataStore.edit { it[THEME] = value }
    suspend fun updatePlaybackSpeeds(min: Float, max: Float) = context.dataStore.edit {
        it[PLAYBACK_SPEED_MIN] = min
        it[PLAYBACK_SPEED_MAX] = max
    }
    suspend fun updateProximity(value: Boolean) = context.dataStore.edit { it[ENABLE_PROXIMITY] = value }
    suspend fun updateDefaultAction(value: String) = context.dataStore.edit { it[DEFAULT_ACTION] = value }
    suspend fun updateTranscriptionEngine(value: String) = context.dataStore.edit { it[TRANSCRIPTION_ENGINE] = value }
    suspend fun updateSelectedModel(value: String) = context.dataStore.edit { it[SELECTED_MODEL_ID] = value }
    suspend fun updateSelectedCloudModel(value: String) = context.dataStore.edit { it[SELECTED_CLOUD_MODEL] = value }
    suspend fun updateModelCatalogUrl(value: String) = context.dataStore.edit { it[MODEL_CATALOG_URL] = value }
}

data class UserSettings(
    val apiKey: String,
    val isPremium: Boolean,
    val language: String,
    val opacity: Float,
    val theme: String,
    val playbackMinSpeed: Float,
    val playbackMaxSpeed: Float,
    val enableProximity: Boolean,
    val defaultAction: String,
    val transcriptionEngine: String,
    val selectedModelId: String,
    val selectedCloudModel: String,
    val modelCatalogUrl: String
)
