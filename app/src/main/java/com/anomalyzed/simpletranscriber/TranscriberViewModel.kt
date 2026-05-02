package com.anomalyzed.simpletranscriber

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anomalyzed.simpletranscriber.data.ModelRepository
import com.anomalyzed.simpletranscriber.data.PreferenceManager
import com.anomalyzed.simpletranscriber.engine.*
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

    /** Testo generato in streaming */
    data class Streaming(val partialText: String, val isRefining: Boolean = false) : TranscriberUiState()

    /** Trascrizione completata con successo */
    data class Success(val text: String) : TranscriberUiState()

    /** Errore durante la trascrizione */
    data class Error(val message: String) : TranscriberUiState()
}

class TranscriberViewModel(application: Application) : AndroidViewModel(application) {

    val uiState: StateFlow<TranscriberUiState> = TranscriptionManager.uiState

    // Variabili temporanee per l'audio in attesa
    private var pendingAudioUri: Uri? = null

    fun setPendingAudio(uri: Uri) {
        pendingAudioUri = uri
        TranscriptionManager.setState(TranscriberUiState.Setup)
    }

    /**
     * Avvia la trascrizione tramite il Foreground Service.
     */
    fun startTranscription(context: Context) {
        val uri = pendingAudioUri ?: return
        
        val intent = Intent(context, TranscriptionService::class.java).apply {
            action = TranscriptionService.ACTION_START
            putExtra(TranscriptionService.EXTRA_AUDIO_URI, uri.toString())
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun setError(msg: String) {
        TranscriptionManager.setState(TranscriberUiState.Error(msg))
    }

    fun clearError() {
        if (TranscriptionManager.uiState.value is TranscriberUiState.Error) {
            TranscriptionManager.setState(TranscriberUiState.Setup)
        }
    }
}
