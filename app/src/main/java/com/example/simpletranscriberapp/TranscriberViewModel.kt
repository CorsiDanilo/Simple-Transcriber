package com.example.simpletranscriberapp

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.simpletranscriberapp.TranscriberUiState.*
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

sealed class TranscriberUiState {
    object Setup : TranscriberUiState() // Inserimento API Key e Lingua
    object Loading : TranscriberUiState()
    data class Success(val text: String) : TranscriberUiState()
    data class Error(val message: String) : TranscriberUiState()
}

class TranscriberViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("transcriber_prefs", Context.MODE_PRIVATE)
    
    // Stato per la API Key e la Lingua (persistenze)
    private val _apiKey = MutableStateFlow(prefs.getString("GEMINI_API_KEY", "") ?: "")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _selectedLanguage = MutableStateFlow(prefs.getString("TARGET_LANGUAGE", "Italian") ?: "Italian")
    val selectedLanguage: StateFlow<String> = _selectedLanguage.asStateFlow()

    private val _uiState = MutableStateFlow<TranscriberUiState>(
        if (_apiKey.value.isEmpty()) Setup else Setup
    )
    val uiState: StateFlow<TranscriberUiState> = _uiState.asStateFlow()

    // Variabili temporanee per l'audio in attesa
    private var pendingAudioUri: Uri? = null

    fun setPendingAudio(uri: Uri) {
        pendingAudioUri = uri
        // Se abbiamo già l'API Key, restiamo comunque in Setup per confermare la lingua
        _uiState.value = Setup
    }

    fun updateApiKey(newKey: String) {
        _apiKey.value = newKey
        prefs.edit().putString("GEMINI_API_KEY", newKey).apply()
    }

    fun updateLanguage(newLang: String) {
        _selectedLanguage.value = newLang
        prefs.edit().putString("TARGET_LANGUAGE", newLang).apply()
    }

    /**
     * Avvia la trascrizione ufficiale usando i dati correnti.
     */
    fun startTranscription(contentResolver: ContentResolver) {
        val uri = pendingAudioUri ?: return
        val key = _apiKey.value
        val lang = _selectedLanguage.value

        if (key.isBlank()) {
            _uiState.value = Error("API Key is missing. Please enter it first.")
            return
        }

        viewModelScope.launch {
            _uiState.value = Loading
            try {
                // Inizializza il modello al volo con la chiave inserita dall'utente
                val generativeModel = GenerativeModel(
                    modelName = "gemini-2.5-flash",
                    apiKey = key
                )

                val audioBytes = withContext(Dispatchers.IO) {
                    readUriToByteArray(contentResolver, uri)
                }
                
                val mimeType = contentResolver.getType(uri) ?: "audio/ogg"

                val requestContent = content {
                    blob(mimeType, audioBytes)
                    text(
                        "Transcribe this audio exactly word-for-word and translate it into $lang. " +
                        "Respond ONLY with the text in $lang language, no introductory or concluding sentences. " +
                        "Maintain the original structure and tone."
                    )
                }

                val response = generativeModel.generateContent(requestContent)
                val text = response.text?.trim()

                if (!text.isNullOrBlank()) {
                    _uiState.value = Success(text)
                } else {
                    _uiState.value = Error("Gemini returned no text. Check audio volume or content.")
                }

            } catch (e: Exception) {
                _uiState.value = Error(e.localizedMessage ?: "Transcription failed.")
            }
        }
    }

    fun setError(msg: String) {
        _uiState.value = Error(msg)
    }

    private fun readUriToByteArray(contentResolver: ContentResolver, uri: Uri): ByteArray {
        return contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("Cannot read file stream.")
    }
}
