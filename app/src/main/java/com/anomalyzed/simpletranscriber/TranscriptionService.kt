package com.anomalyzed.simpletranscriber

import android.app.NotificationChannel
import com.anomalyzed.simpletranscriber.R
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.anomalyzed.simpletranscriber.data.AppDatabase
import com.anomalyzed.simpletranscriber.data.FinalState
import com.anomalyzed.simpletranscriber.data.ModelBackend
import com.anomalyzed.simpletranscriber.data.ModelRepository
import com.anomalyzed.simpletranscriber.data.PreferenceManager
import com.anomalyzed.simpletranscriber.data.TranscriptionItem
import com.anomalyzed.simpletranscriber.data.TranscriptionStateStore
import com.anomalyzed.simpletranscriber.engine.AICoreEngine
import com.anomalyzed.simpletranscriber.engine.CloudEngine
import com.anomalyzed.simpletranscriber.engine.EngineType
import com.anomalyzed.simpletranscriber.engine.ErrorHumanizer
import com.anomalyzed.simpletranscriber.engine.LiteRTEngine
import com.anomalyzed.simpletranscriber.engine.TranscriptionEngine
import com.anomalyzed.simpletranscriber.engine.TranscriptionResult
import com.anomalyzed.simpletranscriber.engine.WhisperCppEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class TranscriptionService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    
    private lateinit var prefManager: PreferenceManager
    private lateinit var modelRepository: ModelRepository
    private lateinit var db: AppDatabase
    private val activeTranscriptionJobs = ConcurrentHashMap<Long, Job>()

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_CANCEL = "ACTION_CANCEL"
        const val ACTION_COPY = "ACTION_COPY"
        const val ACTION_REFRESH_NOTIFICATION = "ACTION_REFRESH_NOTIFICATION"
        const val EXTRA_AUDIO_URI = "EXTRA_AUDIO_URI"
        const val EXTRA_TRANSCRIPTION_ID = "EXTRA_TRANSCRIPTION_ID"
        const val EXTRA_NOTIFICATION_TEXT = "EXTRA_NOTIFICATION_TEXT"
        const val EXTRA_NOTIFICATION_TITLE = "EXTRA_NOTIFICATION_TITLE"
        const val NOTIFICATION_ID_BASE = 1001
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
                    val fallbackId = System.currentTimeMillis() * 1_000 + (System.nanoTime() % 1_000)
                    val transcriptionId = intent.getLongExtra(EXTRA_TRANSCRIPTION_ID, fallbackId)
                    TranscriptionManager.setActiveTask(transcriptionId)
                    startForeground(
                        notificationId(transcriptionId),
                        createNotification(
                            title = getString(R.string.app_name),
                            text = getString(R.string.notif_starting),
                            ongoing = true,
                            autoCancel = false,
                            pendingIntent = createDefaultPendingIntent(transcriptionId),
                            transcriptionId = transcriptionId
                        )
                    )
                    startTranscription(transcriptionId, uri)
                } else {
                    stopSelf()
                }
            }
            ACTION_CANCEL -> {
                val transcriptionId = intent.getLongExtra(EXTRA_TRANSCRIPTION_ID, MainActivity.NO_TRANSCRIPTION_ID)
                if (transcriptionId != MainActivity.NO_TRANSCRIPTION_ID) {
                    cancelTranscription(transcriptionId)
                }
            }
            ACTION_COPY -> {
                val transcriptionId = intent.getLongExtra(EXTRA_TRANSCRIPTION_ID, MainActivity.NO_TRANSCRIPTION_ID)
                if (transcriptionId != MainActivity.NO_TRANSCRIPTION_ID) {
                    copyTranscriptionToClipboard(transcriptionId)
                }
                if (activeTranscriptionJobs.isEmpty()) {
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startTranscription(transcriptionId: Long, uri: Uri) {
        val transcriptionJob = scope.launch(start = CoroutineStart.LAZY) {
            TranscriptionManager.setTaskState(transcriptionId, TranscriberUiState.Loading(getString(R.string.notif_initializing)))
            var engine: TranscriptionEngine? = null
            try {
                val settings = prefManager.settingsFlow.first()
                val engineType = EngineType.fromKey(settings.transcriptionEngine)
                
                var engineModeStr = engineType.name
                var modelNameStr: String? = null
                
                if (engineType == EngineType.LITERT) {
                    val catalogResult = modelRepository.fetchModelCatalog(settings.modelCatalogUrl)
                    val catalog = catalogResult.getOrDefault(emptyList())
                    val selectedModel = catalog.find { it.id == settings.selectedModelId }
                    modelNameStr = selectedModel?.displayName ?: "Local Model"
                } else if (engineType == EngineType.CLOUD) {
                    modelNameStr = settings.selectedCloudModel
                } else if (engineType == EngineType.AICORE) {
                    modelNameStr = "Gemini Nano"
                }

                engine = createEngine(engineType, settings.selectedModelId, settings.apiKey, settings.selectedCloudModel)

                val audioBytes = readUriToByteArray(uri)
                val mimeType = contentResolver.getType(uri) ?: "audio/ogg"

                val result = engine.transcribe(
                    audioBytes = audioBytes,
                    mimeType = mimeType,
                    language = settings.language,
                    onProgress = { progressMessage ->
                        TranscriptionManager.setTaskState(transcriptionId, TranscriberUiState.Loading(progressMessage))
                        updateNotification(
                            transcriptionId = transcriptionId,
                            title = getString(R.string.app_name),
                            text = progressMessage,
                            ongoing = true,
                            autoCancel = false,
                            pendingIntent = createDefaultPendingIntent(transcriptionId)
                        )
                    },
                    onPartialText = { text ->
                        TranscriptionManager.setTaskState(transcriptionId, TranscriberUiState.Streaming(text, isRefining = false))
                        val preview = buildPreview(text)
                        val displayText = if (preview.isNotEmpty()) {
                            getString(R.string.notif_transcribing_preview, preview)
                        } else {
                            getString(R.string.notif_transcribing)
                        }
                        updateNotification(
                            transcriptionId = transcriptionId,
                            title = getString(R.string.app_name),
                            text = displayText,
                            ongoing = true,
                            autoCancel = false,
                            pendingIntent = createDefaultPendingIntent(transcriptionId)
                        )
                    }
                )

                when (result) {
                    is TranscriptionResult.Success -> {
                        val finalText = if (engine.performsRefinementDuringTranscription()) {
                            result.text
                        } else {
                            TranscriptionManager.setTaskState(transcriptionId, TranscriberUiState.Loading(getString(R.string.notif_refining)))
                            updateNotification(
                                transcriptionId = transcriptionId,
                                title = getString(R.string.app_name),
                                text = getString(R.string.notif_refining),
                                ongoing = true,
                                autoCancel = false,
                                pendingIntent = createDefaultPendingIntent(transcriptionId)
                            )
                            engine.refineText(result.text, settings.language) { text ->
                                TranscriptionManager.setTaskState(transcriptionId, TranscriberUiState.Streaming(text, isRefining = true))
                                val preview = buildPreview(text)
                                val displayText = if (preview.isNotEmpty()) {
                                    getString(R.string.notif_refining_preview, preview)
                                } else {
                                    getString(R.string.notif_refining)
                                }
                                updateNotification(
                                    transcriptionId = transcriptionId,
                                    title = getString(R.string.app_name),
                                    text = displayText,
                                    ongoing = true,
                                    autoCancel = false,
                                    pendingIntent = createDefaultPendingIntent(transcriptionId)
                                )
                            }
                        }
                        
                        db.transcriptionDao().insert(
                            TranscriptionItem(
                                timestamp = System.currentTimeMillis(), 
                                text = finalText,
                                engineMode = engineModeStr,
                                modelName = modelNameStr
                            )
                        )
                        TranscriptionManager.setTaskState(transcriptionId, TranscriberUiState.Success(finalText, engineModeStr, modelNameStr))
                        TranscriptionStateStore(this@TranscriptionService).persist(transcriptionId, FinalState.Success(finalText, engineModeStr, modelNameStr))
                        showSuccessNotification(transcriptionId, finalText)
                    }
                    is TranscriptionResult.Error -> {
                        val humanMsg = ErrorHumanizer.humanize(result.message, this@TranscriptionService)
                        TranscriptionManager.setTaskState(transcriptionId, TranscriberUiState.Error(humanMsg))
                        TranscriptionStateStore(this@TranscriptionService).persist(transcriptionId, FinalState.Error(humanMsg))
                        showErrorNotification(transcriptionId, humanMsg)
                    }
                }
            } catch (e: CancellationException) {
                TranscriptionManager.clearTask(transcriptionId)
            } catch (e: Exception) {
                val humanMsg = ErrorHumanizer.humanize(e, this@TranscriptionService)
                TranscriptionManager.setTaskState(transcriptionId, TranscriberUiState.Error(humanMsg))
                TranscriptionStateStore(this@TranscriptionService).persist(transcriptionId, FinalState.Error(humanMsg))
                showErrorNotification(transcriptionId, humanMsg)
            } finally {
                engine?.release()
                activeTranscriptionJobs.remove(transcriptionId, coroutineContext[Job])
                finishServiceIfIdle()
            }
        }
        activeTranscriptionJobs[transcriptionId] = transcriptionJob
        transcriptionJob.start()
    }

    private fun cancelTranscription(transcriptionId: Long) {
        activeTranscriptionJobs.remove(transcriptionId)?.cancel(CancellationException(getString(R.string.settings_cancel_transcription)))
        TranscriptionManager.clearTask(transcriptionId)

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(notificationId(transcriptionId))

        finishServiceIfIdle(removeForegroundNotification = true)
    }

    private fun copyTranscriptionToClipboard(transcriptionId: Long) {
        val state = TranscriptionManager.getTaskState(transcriptionId) as? TranscriberUiState.Success
        val text = state?.text.orEmpty()
        if (text.isBlank()) {
            Toast.makeText(this, getString(R.string.toast_no_transcription_to_copy), Toast.LENGTH_SHORT).show()
            return
        }

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), text))
        Toast.makeText(this, getString(R.string.toast_copied), Toast.LENGTH_SHORT).show()
    }

    private fun finishServiceIfIdle(removeForegroundNotification: Boolean = false) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(
                if (removeForegroundNotification) {
                    STOP_FOREGROUND_REMOVE
                } else {
                    STOP_FOREGROUND_DETACH
                }
            )
        } else {
            @Suppress("DEPRECATION")
            stopForeground(removeForegroundNotification)
        }

        if (activeTranscriptionJobs.isNotEmpty()) {
            promoteForegroundNotification()
            return
        }
        stopSelf()
    }

    private fun promoteForegroundNotification() {
        val transcriptionId = activeTranscriptionJobs.keys.firstOrNull() ?: return
        val state = TranscriptionManager.getTaskState(transcriptionId)
            ?: TranscriberUiState.Loading(getString(R.string.notif_transcribing))
        val payload = buildNotificationPayload(transcriptionId, state) ?: return
        if (!payload.ongoing) return

        startForeground(
            notificationId(transcriptionId),
            createNotification(
                title = payload.title,
                text = payload.text,
                ongoing = payload.ongoing,
                autoCancel = payload.autoCancel,
                pendingIntent = payload.pendingIntent,
                transcriptionId = transcriptionId
            )
        )
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
                val selectedModel = catalog.find { it.id == selectedModelId }
                val modelPath = modelRepository.getModelPathById(selectedModelId, catalog)
                val modelName = selectedModel?.displayName ?: "Local Model"
                
                when (selectedModel?.backend) {
                    ModelBackend.WHISPER_CPP -> WhisperCppEngine(modelPath, modelName)
                    else -> LiteRTEngine(this, modelPath, modelName)
                }
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
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun notificationId(transcriptionId: Long): Int {
        val suffix = kotlin.math.abs((transcriptionId % 1_000_000_000).toInt())
        return NOTIFICATION_ID_BASE + suffix
    }

    private fun createPendingIntent(transcriptionId: Long, requestCode: Int, flags: Int): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            // FLAG_ACTIVITY_NEW_TASK ensures the Activity is started in its own task even
            // when the process is relaunched from scratch via the notification. Without it,
            // Android may fail to start the Activity when there is no existing foreground task.
            this.flags = flags or Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(MainActivity.EXTRA_SHOW_TRANSCRIBER_DIALOG, true)
            putExtra(MainActivity.EXTRA_TRANSCRIPTION_ID, transcriptionId)
        }
        return PendingIntent.getActivity(
            this,
            requestCode + notificationId(transcriptionId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createDefaultPendingIntent(transcriptionId: Long): PendingIntent {
        return createPendingIntent(transcriptionId, 0, Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    private fun createCompletionPendingIntent(transcriptionId: Long): PendingIntent {
        return createPendingIntent(transcriptionId, 1, Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    private fun createCancelPendingIntent(transcriptionId: Long): PendingIntent {
        val intent = Intent(this, TranscriptionService::class.java).apply {
            action = ACTION_CANCEL
            putExtra(EXTRA_TRANSCRIPTION_ID, transcriptionId)
        }
        return PendingIntent.getService(
            this,
            2 + notificationId(transcriptionId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createCopyPendingIntent(transcriptionId: Long): PendingIntent {
        val intent = Intent(this, TranscriptionService::class.java).apply {
            action = ACTION_COPY
            putExtra(EXTRA_TRANSCRIPTION_ID, transcriptionId)
        }
        return PendingIntent.getService(
            this,
            3 + notificationId(transcriptionId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotification(
        title: String,
        text: String,
        ongoing: Boolean,
        autoCancel: Boolean,
        pendingIntent: PendingIntent,
        transcriptionId: Long,
        bigText: String? = null,
        showCopyAction: Boolean = false
    ): android.app.Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(ongoing)
            .setAutoCancel(autoCancel)

        if (ongoing) {
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.btn_cancel),
                createCancelPendingIntent(transcriptionId)
            )
        }

        if (bigText != null) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
        }

        if (showCopyAction) {
            builder.addAction(
                android.R.drawable.ic_menu_save,
                getString(R.string.btn_copy_notification),
                createCopyPendingIntent(transcriptionId)
            )
        }

        return builder.build()
    }

    private fun updateNotification(
        transcriptionId: Long,
        title: String,
        text: String,
        ongoing: Boolean,
        autoCancel: Boolean,
        pendingIntent: PendingIntent,
        bigText: String? = null,
        showCopyAction: Boolean = false
    ) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(
            notificationId(transcriptionId),
            createNotification(
                title = title,
                text = text,
                ongoing = ongoing,
                autoCancel = autoCancel,
                pendingIntent = pendingIntent,
                transcriptionId = transcriptionId,
                bigText = bigText,
                showCopyAction = showCopyAction
            )
        )
    }

    private data class NotificationPayload(
        val title: String,
        val text: String,
        val ongoing: Boolean,
        val autoCancel: Boolean,
        val pendingIntent: PendingIntent,
        val bigText: String? = null,
        val showCopyAction: Boolean = false
    )

    private fun buildNotificationPayload(transcriptionId: Long, state: TranscriberUiState): NotificationPayload? {
        return when (state) {
            is TranscriberUiState.Loading -> {
                val text = state.progressMessage.ifBlank { getString(R.string.notif_transcribing) }
                NotificationPayload(
                    title = getString(R.string.app_name),
                    text = text,
                    ongoing = true,
                    autoCancel = false,
                    pendingIntent = createDefaultPendingIntent(transcriptionId)
                )
            }
            is TranscriberUiState.Streaming -> {
                val preview = buildPreview(state.partialText)
                val text = if (state.isRefining) {
                    if (preview.isNotEmpty()) getString(R.string.notif_refining_preview, preview) else getString(R.string.notif_refining)
                } else {
                    if (preview.isNotEmpty()) getString(R.string.notif_transcribing_preview, preview) else getString(R.string.notif_transcribing)
                }
                NotificationPayload(
                    title = getString(R.string.app_name),
                    text = text,
                    ongoing = true,
                    autoCancel = false,
                    pendingIntent = createDefaultPendingIntent(transcriptionId)
                )
            }
            is TranscriberUiState.Success -> {
                NotificationPayload(
                    title = getString(R.string.notif_complete_title),
                    text = state.text,
                    ongoing = false,
                    autoCancel = true,
                    pendingIntent = createCompletionPendingIntent(transcriptionId),
                    bigText = state.text,
                    showCopyAction = true
                )
            }
            is TranscriberUiState.Error -> {
                NotificationPayload(
                    title = getString(R.string.title_transcribing_error),
                    text = state.message,
                    ongoing = false,
                    autoCancel = true,
                    pendingIntent = createDefaultPendingIntent(transcriptionId)
                )
            }
            TranscriberUiState.Setup -> null
        }
    }

    private fun refreshNotificationFromState(transcriptionId: Long) {
        val state = TranscriptionManager.getTaskState(transcriptionId) ?: return
        val payload = buildNotificationPayload(transcriptionId, state) ?: return
        if (payload.ongoing) {
            startForeground(
                notificationId(transcriptionId),
                createNotification(
                    title = payload.title,
                    text = payload.text,
                    ongoing = payload.ongoing,
                    autoCancel = payload.autoCancel,
                    pendingIntent = payload.pendingIntent,
                    transcriptionId = transcriptionId,
                    bigText = payload.bigText,
                    showCopyAction = payload.showCopyAction
                )
            )
        } else {
            updateNotification(
                transcriptionId = transcriptionId,
                title = payload.title,
                text = payload.text,
                ongoing = payload.ongoing,
                autoCancel = payload.autoCancel,
                pendingIntent = payload.pendingIntent,
                bigText = payload.bigText,
                showCopyAction = payload.showCopyAction
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

    private fun showSuccessNotification(transcriptionId: Long, text: String) {
        updateNotification(
            transcriptionId = transcriptionId,
            title = getString(R.string.notif_complete_title),
            text = text,
            ongoing = false,
            autoCancel = true,
            pendingIntent = createCompletionPendingIntent(transcriptionId),
            bigText = text,
            showCopyAction = true
        )
    }

    private fun showErrorNotification(transcriptionId: Long, msg: String) {
        updateNotification(
            transcriptionId = transcriptionId,
            title = getString(R.string.title_transcribing_error),
            text = msg,
            ongoing = false,
            autoCancel = true,
            pendingIntent = createDefaultPendingIntent(transcriptionId)
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
