package com.example.simpletranscriberapp

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.simpletranscriberapp.data.ModelRepository
import com.example.simpletranscriberapp.data.PreferenceManager
import com.example.simpletranscriberapp.engine.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Stati dell'UI per il flusso di trascrizione.
 * Loading ora include un messaggio di progresso opzionale.
 */
sealed class TranscriberUiState {
    /** Schermata di setup (API Key, lingua, etc.) */
    object Setup : TranscriberUiState()

    /** Trascrizione in corso, con messaggio di stato opzionale */
    data class Loading(val progressMessage: String = "") : TranscriberUiState()

    /** Trascrizione completata con successo */
    data class Success(val text: String) : TranscriberUiState()

    /** Errore durante la trascrizione */
    data class Error(val message: String) : TranscriberUiState()
}

class TranscriberViewModel(application: Application) : AndroidViewModel(application) {

    private val prefManager = PreferenceManager(application)
    private val modelRepository = ModelRepository(application)
    
    // Cache for engines to avoid repeated initialization
    private var cachedLiteRTEngine: LiteRTEngine? = null
    
    private val _uiState = MutableStateFlow<TranscriberUiState>(TranscriberUiState.Setup)
    val uiState: StateFlow<TranscriberUiState> = _uiState.asStateFlow()

    // Variabili temporanee per l'audio in attesa
    private var pendingAudioUri: Uri? = null

    fun setPendingAudio(uri: Uri) {
        pendingAudioUri = uri
        _uiState.value = TranscriberUiState.Setup
    }

    /**
     * Avvia la trascrizione usando l'engine selezionato nelle impostazioni.
     */
    fun startTranscription(contentResolver: ContentResolver) {
        val uri = pendingAudioUri ?: return

        viewModelScope.launch {
            _uiState.value = TranscriberUiState.Loading()
            try {
                // Leggi le impostazioni correnti
                val settings = prefManager.settingsFlow.first()
                val engineType = EngineType.fromKey(settings.transcriptionEngine)

                // Crea l'engine appropriato
                val engine = createEngine(engineType, settings.selectedModelId, settings.apiKey, settings.selectedCloudModel)

                // Leggi l'audio
                val audioBytes = withContext(Dispatchers.IO) {
                    readUriToByteArray(contentResolver, uri)
                }
                val mimeType = contentResolver.getType(uri) ?: "audio/ogg"

                // Trascrivi con progress reporting
                val result = engine.transcribe(
                    audioBytes = audioBytes,
                    mimeType = mimeType,
                    language = settings.language
                ) { progressMessage ->
                    _uiState.value = TranscriberUiState.Loading(progressMessage)
                }

                // Gestisci risultato
                when (result) {
                    is TranscriptionResult.Success -> {
                        _uiState.value = TranscriberUiState.Loading("Refining text...")
                        val refinedText = withContext(Dispatchers.IO) {
                            engine.refineText(result.text, settings.language)
                        }
                        _uiState.value = TranscriberUiState.Success(refinedText)
                    }
                    is TranscriptionResult.Error -> {
                        _uiState.value = TranscriberUiState.Error(result.message)
                    }
                }
            } catch (e: Exception) {
                _uiState.value = TranscriberUiState.Error(
                    e.localizedMessage ?: "Transcription failed."
                )
            }
        }
    }

    /**
     * Factory method per creare l'engine corretto basato sulle impostazioni.
     */
    private suspend fun createEngine(
        engineType: EngineType,
        selectedModelId: String,
        apiKey: String,
        selectedCloudModel: String
    ): TranscriptionEngine {
        return when (engineType) {
            EngineType.CLOUD -> CloudEngine(apiKey, selectedCloudModel)
            EngineType.AICORE -> AICoreEngine(getApplication())
            EngineType.LITERT -> {
                // Recupera il catalogo per trovare il path del modello
                val settings = prefManager.settingsFlow.first()
                val catalogResult = modelRepository.fetchModelCatalog(settings.modelCatalogUrl)
                val catalog = catalogResult.getOrDefault(emptyList())
                val modelPath = modelRepository.getModelPathById(selectedModelId, catalog)
                val modelName = catalog.find { it.id == selectedModelId }?.displayName ?: "Local Model"
                
                // Reuse cached engine if model path is the same
                if (cachedLiteRTEngine?.modelPath == modelPath) {
                    cachedLiteRTEngine!!
                } else {
                    LiteRTEngine(getApplication(), modelPath, modelName).also {
                        cachedLiteRTEngine = it
                    }
                }
            }
        }
    }

    fun setError(msg: String) {
        _uiState.value = TranscriberUiState.Error(msg)
    }

    fun clearError() {
        if (_uiState.value is TranscriberUiState.Error) {
            _uiState.value = TranscriberUiState.Setup
        }
    }

    private fun readUriToByteArray(contentResolver: ContentResolver, uri: Uri): ByteArray {
        return contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("Cannot read file stream.")
    }
}
