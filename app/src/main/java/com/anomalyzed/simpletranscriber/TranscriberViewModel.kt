package com.anomalyzed.simpletranscriber

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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
    private var activeTranscriptionId: Long? = null

    fun setPendingAudio(uri: Uri) {
        pendingAudioUri = uri
        activeTranscriptionId = null
        TranscriptionManager.clearState()
    }

    /**
     * Avvia la trascrizione tramite il Foreground Service.
     */
    fun startTranscription(context: Context) {
        val uri = pendingAudioUri ?: return
        val transcriptionId = System.currentTimeMillis() * 1_000 + (System.nanoTime() % 1_000)
        activeTranscriptionId = transcriptionId
        TranscriptionManager.setActiveTask(transcriptionId)
        
        val intent = Intent(context, TranscriptionService::class.java).apply {
            action = TranscriptionService.ACTION_START
            putExtra(TranscriptionService.EXTRA_AUDIO_URI, uri.toString())
            putExtra(TranscriptionService.EXTRA_TRANSCRIPTION_ID, transcriptionId)
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun selectTranscription(id: Long) {
        activeTranscriptionId = id
        TranscriptionManager.setActiveTask(id)
    }

    fun refreshNotification(context: Context, state: TranscriberUiState) {
        val displayText = buildNotificationText(state) ?: return
        val transcriptionId = activeTranscriptionId ?: TranscriptionManager.currentTaskId() ?: return
        val intent = Intent(context, TranscriptionService::class.java).apply {
            action = TranscriptionService.ACTION_REFRESH_NOTIFICATION
            putExtra(TranscriptionService.EXTRA_TRANSCRIPTION_ID, transcriptionId)
            putExtra(TranscriptionService.EXTRA_NOTIFICATION_TEXT, displayText)
            putExtra(TranscriptionService.EXTRA_NOTIFICATION_TITLE, "Transcriber")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun cancelTranscription(context: Context) {
        val transcriptionId = activeTranscriptionId ?: TranscriptionManager.currentTaskId() ?: return
        val intent = Intent(context, TranscriptionService::class.java).apply {
            action = TranscriptionService.ACTION_CANCEL
            putExtra(TranscriptionService.EXTRA_TRANSCRIPTION_ID, transcriptionId)
        }
        context.startService(intent)
    }

    private fun buildNotificationText(state: TranscriberUiState): String? {
        return when (state) {
            is TranscriberUiState.Loading -> state.progressMessage.ifBlank { "Transcribing..." }
            is TranscriberUiState.Streaming -> {
                val preview = buildPreview(state.partialText)
                if (state.isRefining) {
                    if (preview.isNotEmpty()) "Refining: $preview" else "Refining..."
                } else {
                    if (preview.isNotEmpty()) "Transcribing: $preview" else "Transcribing..."
                }
            }
            else -> null
        }
    }

    private fun buildPreview(text: String, maxLength: Int = 40): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return ""
        }

        return if (trimmed.length > maxLength) {
            trimmed.substring(0, maxLength) + "..."
        } else {
            trimmed
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
