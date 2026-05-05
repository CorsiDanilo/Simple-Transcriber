package com.anomalyzed.simpletranscriber

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
import com.anomalyzed.simpletranscriber.data.AppDatabase
import com.anomalyzed.simpletranscriber.data.ModelRepository
import com.anomalyzed.simpletranscriber.data.PreferenceManager
import com.anomalyzed.simpletranscriber.data.TranscriptionItem
import com.anomalyzed.simpletranscriber.engine.AICoreEngine
import com.anomalyzed.simpletranscriber.engine.CloudEngine
import com.anomalyzed.simpletranscriber.engine.EngineType
import com.anomalyzed.simpletranscriber.engine.LiteRTEngine
import com.anomalyzed.simpletranscriber.engine.TranscriptionEngine
import com.anomalyzed.simpletranscriber.engine.TranscriptionResult
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
        const val ACTION_REFRESH_NOTIFICATION = "ACTION_REFRESH_NOTIFICATION"
        const val EXTRA_AUDIO_URI = "EXTRA_AUDIO_URI"
        const val EXTRA_NOTIFICATION_TEXT = "EXTRA_NOTIFICATION_TEXT"
        const val EXTRA_NOTIFICATION_TITLE = "EXTRA_NOTIFICATION_TITLE"
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
        when (intent?.action) {
            ACTION_START -> {
                val uriString = intent.getStringExtra(EXTRA_AUDIO_URI)
                if (uriString != null) {
                    val uri = Uri.parse(uriString)
                    startForeground(
                        NOTIFICATION_ID,
                        createNotification(
                            title = "Transcriber",
                            text = "Starting transcription...",
                            ongoing = true,
                            autoCancel = false,
                            pendingIntent = createDefaultPendingIntent()
                        )
                    )
                    startTranscription(uri)
                } else {
                    stopSelf()
                }
            }
            ACTION_REFRESH_NOTIFICATION -> {
                val textExtra = intent.getStringExtra(EXTRA_NOTIFICATION_TEXT)
                val titleExtra = intent.getStringExtra(EXTRA_NOTIFICATION_TITLE)
                val displayText = textExtra?.ifBlank { "Transcribing..." }
                if (displayText != null) {
                    startForeground(
                        NOTIFICATION_ID,
                        createNotification(
                            title = titleExtra ?: "Transcriber",
                            text = displayText,
                            ongoing = true,
                            autoCancel = false,
                            pendingIntent = createDefaultPendingIntent()
                        )
                    )
                } else {
                    refreshNotificationFromState()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startTranscription(uri: Uri) {
        scope.launch {
            TranscriptionManager.setState(TranscriberUiState.Loading("Initializing..."))
            var engine: TranscriptionEngine? = null
            try {
                val settings = prefManager.settingsFlow.first()
                val engineType = EngineType.fromKey(settings.transcriptionEngine)

                engine = createEngine(engineType, settings.selectedModelId, settings.apiKey, settings.selectedCloudModel)

                val audioBytes = readUriToByteArray(uri)
                val mimeType = contentResolver.getType(uri) ?: "audio/ogg"

                val result = engine.transcribe(
                    audioBytes = audioBytes,
                    mimeType = mimeType,
                    language = settings.language,
                    onProgress = { progressMessage ->
                        TranscriptionManager.setState(TranscriberUiState.Loading(progressMessage))
                        updateNotification(
                            title = "Transcriber",
                            text = progressMessage,
                            ongoing = true,
                            autoCancel = false,
                            pendingIntent = createDefaultPendingIntent()
                        )
                    },
                    onPartialText = { text ->
                        TranscriptionManager.setState(TranscriberUiState.Streaming(text, isRefining = false))
                        val preview = buildPreview(text)
                        val displayText = if (preview.isNotEmpty()) {
                            "Transcribing: $preview"
                        } else {
                            "Transcribing..."
                        }
                        updateNotification(
                            title = "Transcriber",
                            text = displayText,
                            ongoing = true,
                            autoCancel = false,
                            pendingIntent = createDefaultPendingIntent()
                        )
                    }
                )

                when (result) {
                    is TranscriptionResult.Success -> {
                        TranscriptionManager.setState(TranscriberUiState.Loading("Refining text..."))
                        updateNotification(
                            title = "Transcriber",
                            text = "Refining text...",
                            ongoing = true,
                            autoCancel = false,
                            pendingIntent = createDefaultPendingIntent()
                        )
                        val refinedText = engine.refineText(result.text, settings.language) { text ->
                            TranscriptionManager.setState(TranscriberUiState.Streaming(text, isRefining = true))
                            val preview = buildPreview(text)
                            val displayText = if (preview.isNotEmpty()) {
                                "Refining: $preview"
                            } else {
                                "Refining..."
                            }
                            updateNotification(
                                title = "Transcriber",
                                text = displayText,
                                ongoing = true,
                                autoCancel = false,
                                pendingIntent = createDefaultPendingIntent()
                            )
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
                engine?.release()
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
                "Transcriber",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createPendingIntent(requestCode: Int, flags: Int): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            this.flags = flags
        }
        return PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createDefaultPendingIntent(): PendingIntent {
        return createPendingIntent(0, Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    private fun createCompletionPendingIntent(): PendingIntent {
        return createPendingIntent(1, Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }

    private fun createNotification(
        title: String,
        text: String,
        ongoing: Boolean,
        autoCancel: Boolean,
        pendingIntent: PendingIntent
    ): android.app.Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(ongoing)
            .setAutoCancel(autoCancel)
            .build()
    }

    private fun updateNotification(
        title: String,
        text: String,
        ongoing: Boolean,
        autoCancel: Boolean,
        pendingIntent: PendingIntent
    ) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(
            NOTIFICATION_ID,
            createNotification(title, text, ongoing, autoCancel, pendingIntent)
        )
    }

    private data class NotificationPayload(
        val title: String,
        val text: String,
        val ongoing: Boolean,
        val autoCancel: Boolean,
        val pendingIntent: PendingIntent
    )

    private fun buildNotificationPayload(state: TranscriberUiState): NotificationPayload? {
        return when (state) {
            is TranscriberUiState.Loading -> {
                val text = state.progressMessage.ifBlank { "Transcribing..." }
                NotificationPayload(
                    title = "Transcriber",
                    text = text,
                    ongoing = true,
                    autoCancel = false,
                    pendingIntent = createDefaultPendingIntent()
                )
            }
            is TranscriberUiState.Streaming -> {
                val preview = buildPreview(state.partialText)
                val text = if (state.isRefining) {
                    if (preview.isNotEmpty()) "Refining: $preview" else "Refining..."
                } else {
                    if (preview.isNotEmpty()) "Transcribing: $preview" else "Transcribing..."
                }
                NotificationPayload(
                    title = "Transcriber",
                    text = text,
                    ongoing = true,
                    autoCancel = false,
                    pendingIntent = createDefaultPendingIntent()
                )
            }
            is TranscriberUiState.Success -> {
                NotificationPayload(
                    title = "Transcriber Complete",
                    text = "Tap to view history",
                    ongoing = false,
                    autoCancel = true,
                    pendingIntent = createCompletionPendingIntent()
                )
            }
            is TranscriberUiState.Error -> {
                NotificationPayload(
                    title = "Transcriber Error",
                    text = state.message,
                    ongoing = false,
                    autoCancel = true,
                    pendingIntent = createDefaultPendingIntent()
                )
            }
            TranscriberUiState.Setup -> null
        }
    }

    private fun refreshNotificationFromState() {
        val payload = buildNotificationPayload(TranscriptionManager.uiState.value) ?: return
        if (payload.ongoing) {
            startForeground(
                NOTIFICATION_ID,
                createNotification(
                    title = payload.title,
                    text = payload.text,
                    ongoing = payload.ongoing,
                    autoCancel = payload.autoCancel,
                    pendingIntent = payload.pendingIntent
                )
            )
        } else {
            updateNotification(
                title = payload.title,
                text = payload.text,
                ongoing = payload.ongoing,
                autoCancel = payload.autoCancel,
                pendingIntent = payload.pendingIntent
            )
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

    private fun showSuccessNotification() {
        updateNotification(
            title = "Transcriber Complete",
            text = "Tap to view history",
            ongoing = false,
            autoCancel = true,
            pendingIntent = createCompletionPendingIntent()
        )
    }

    private fun showErrorNotification(msg: String) {
        updateNotification(
            title = "Transcriber Error",
            text = msg,
            ongoing = false,
            autoCancel = true,
            pendingIntent = createDefaultPendingIntent()
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
