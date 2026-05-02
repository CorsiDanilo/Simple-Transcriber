package com.example.simpletranscriberapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.simpletranscriberapp.data.AppDatabase
import com.example.simpletranscriberapp.data.ModelRepository
import com.example.simpletranscriberapp.data.PreferenceManager
import com.example.simpletranscriberapp.data.TranscriptionItem
import com.example.simpletranscriberapp.engine.AICoreEngine
import com.example.simpletranscriberapp.engine.CloudEngine
import com.example.simpletranscriberapp.engine.EngineType
import com.example.simpletranscriberapp.engine.LiteRTEngine
import com.example.simpletranscriberapp.engine.TranscriptionEngine
import com.example.simpletranscriberapp.engine.TranscriptionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.IOException

class TranscriptionService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    
    private lateinit var prefManager: PreferenceManager
    private lateinit var modelRepository: ModelRepository
    private lateinit var db: AppDatabase

    companion object {
        const val ACTION_START = "ACTION_START"
        const val EXTRA_AUDIO_URI = "EXTRA_AUDIO_URI"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "transcription_channel"
    }

    override fun onCreate() {
        super.onCreate()
        prefManager = PreferenceManager(this)
        modelRepository = ModelRepository(this)
        db = AppDatabase.getDatabase(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START) {
            val uriString = intent.getStringExtra(EXTRA_AUDIO_URI)
            if (uriString != null) {
                val uri = Uri.parse(uriString)
                startForeground(NOTIFICATION_ID, createNotification("Starting transcription..."))
                startTranscription(uri)
            } else {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startTranscription(uri: Uri) {
        scope.launch {
            TranscriptionManager.setState(TranscriberUiState.Loading("Initializing..."))
            try {
                val settings = prefManager.settingsFlow.first()
                val engineType = EngineType.fromKey(settings.transcriptionEngine)

                val engine = createEngine(engineType, settings.selectedModelId, settings.apiKey, settings.selectedCloudModel)

                val audioBytes = readUriToByteArray(uri)
                val mimeType = contentResolver.getType(uri) ?: "audio/ogg"

                val result = engine.transcribe(
                    audioBytes = audioBytes,
                    mimeType = mimeType,
                    language = settings.language,
                    onProgress = { progressMessage ->
                        TranscriptionManager.setState(TranscriberUiState.Loading(progressMessage))
                        updateNotification(progressMessage)
                    },
                    onPartialText = { text ->
                        TranscriptionManager.setState(TranscriberUiState.Streaming(text, isRefining = false))
                    }
                )

                when (result) {
                    is TranscriptionResult.Success -> {
                        TranscriptionManager.setState(TranscriberUiState.Loading("Refining text..."))
                        updateNotification("Refining text...")
                        val refinedText = engine.refineText(result.text, settings.language) { text ->
                            TranscriptionManager.setState(TranscriberUiState.Streaming(text, isRefining = true))
                        }
                        
                        db.transcriptionDao().insert(TranscriptionItem(timestamp = System.currentTimeMillis(), text = refinedText))
                        TranscriptionManager.setState(TranscriberUiState.Success(refinedText))
                        showSuccessNotification()
                    }
                    is TranscriptionResult.Error -> {
                        TranscriptionManager.setState(TranscriberUiState.Error(result.message))
                        showErrorNotification(result.message)
                    }
                }
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: "Transcription failed."
                TranscriptionManager.setState(TranscriberUiState.Error(msg))
                showErrorNotification(msg)
            } finally {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_DETACH)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
            }
        }
    }

    private suspend fun createEngine(
        engineType: EngineType,
        selectedModelId: String,
        apiKey: String,
        selectedCloudModel: String
    ): TranscriptionEngine {
        return when (engineType) {
            EngineType.CLOUD -> CloudEngine(apiKey, selectedCloudModel)
            EngineType.AICORE -> AICoreEngine(this)
            EngineType.LITERT -> {
                val settings = prefManager.settingsFlow.first()
                val catalogResult = modelRepository.fetchModelCatalog(settings.modelCatalogUrl)
                val catalog = catalogResult.getOrDefault(emptyList())
                val modelPath = modelRepository.getModelPathById(selectedModelId, catalog)
                val modelName = catalog.find { it.id == selectedModelId }?.displayName ?: "Local Model"
                
                LiteRTEngine(this, modelPath, modelName)
            }
        }
    }

    private fun readUriToByteArray(uri: Uri): ByteArray {
        return contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("Cannot read file stream.")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Transcription",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Transcription")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, createNotification(text))
    }

    private fun showSuccessNotification() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Transcription Complete")
            .setContentText("Tap to view history")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID + 1, notification)
    }

    private fun showErrorNotification(msg: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Transcription Error")
            .setContentText(msg)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .build()
        
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID + 2, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
