package com.example.simpletranscriberapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.simpletranscriberapp.data.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val dao = db.transcriptionDao()
    private val prefManager = PreferenceManager(application)

    // Cronologia (Room)
    val historyItems: StateFlow<List<TranscriptionItem>> = dao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Impostazioni (DataStore)
    val settings: StateFlow<UserSettings> = prefManager.settingsFlow
        .stateIn(
            viewModelScope, 
            SharingStarted.WhileSubscribed(5000), 
            UserSettings("", true, "Italiano", 0.95f, "System", 0.75f, 1.75f, false, "Show actions")
        )

    fun saveTranscription(text: String) {
        viewModelScope.launch {
            dao.insert(TranscriptionItem(timestamp = System.currentTimeMillis(), text = text))
        }
    }

    fun clearHistory() {
        viewModelScope.launch { dao.clearAll() }
    }

    // Update settings
    fun updateLanguage(lang: String) = viewModelScope.launch { prefManager.updateLanguage(lang) }
    fun updateOpacity(value: Float) = viewModelScope.launch { prefManager.updateOpacity(value) }
    fun updateTheme(theme: String) = viewModelScope.launch { prefManager.updateTheme(theme) }
    fun updateProximity(enabled: Boolean) = viewModelScope.launch { prefManager.updateProximity(enabled) }
    fun updateDefaultAction(action: String) = viewModelScope.launch { prefManager.updateDefaultAction(action) }
    fun updateApiKey(key: String) = viewModelScope.launch { prefManager.updateApiKey(key) }
}
