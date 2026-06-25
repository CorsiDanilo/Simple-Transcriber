package com.anomalyzed.simpletranscriber

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.app.NotificationManagerCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.anomalyzed.simpletranscriber.ui.TranscriberScreen
import com.anomalyzed.simpletranscriber.ui.screens.HistoryScreen
import com.anomalyzed.simpletranscriber.ui.screens.ModelManagerScreen
import com.anomalyzed.simpletranscriber.ui.screens.SettingsScreen
import com.anomalyzed.simpletranscriber.ui.theme.TranscriberTheme
import com.anomalyzed.simpletranscriber.updater.AppUpdater
import com.anomalyzed.simpletranscriber.updater.UpdateInfo
import com.anomalyzed.simpletranscriber.updater.DownloadReceiver
import com.anomalyzed.simpletranscriber.updater.UpdateIntegrity
import com.anomalyzed.simpletranscriber.ui.updater.UpdateDialog
import com.anomalyzed.simpletranscriber.engine.EngineType
import android.app.DownloadManager
import android.app.NotificationManager
import android.os.Environment
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import java.io.File
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import com.anomalyzed.simpletranscriber.data.PreferenceManager

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SHOW_TRANSCRIBER_DIALOG = "EXTRA_SHOW_TRANSCRIBER_DIALOG"
        const val EXTRA_TRANSCRIPTION_ID = "EXTRA_TRANSCRIPTION_ID"
        const val NO_TRANSCRIPTION_ID = -1L
        private const val REQUEST_POST_NOTIFICATIONS = 101
    }

    private val mainViewModel: MainViewModel by viewModels()
    private val transcriberViewModel: TranscriberViewModel by viewModels()
    private var isShareFlow by mutableStateOf(false)
    private var showTranscriberDialog by mutableStateOf(false)
    private var startTranscriptionAfterNotificationPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        isShareFlow = intent?.action == Intent.ACTION_SEND
        showTranscriberDialog = intent?.getBooleanExtra(EXTRA_SHOW_TRANSCRIBER_DIALOG, false) == true
        intent?.getLongExtra(EXTRA_TRANSCRIPTION_ID, NO_TRANSCRIPTION_ID)
            ?.takeIf { it != NO_TRANSCRIPTION_ID }
            ?.let { transcriberViewModel.selectTranscription(it) }

        // Gestione Intent di condivisione (Transcriber)
        if (savedInstanceState == null && isShareFlow) {
            handleIncomingIntent()
        }

        setContent {
            val settings by mainViewModel.settings.collectAsState()
            val history by mainViewModel.historyItems.collectAsState()
            val transcriberState by transcriberViewModel.uiState.collectAsState()
            val modelsWithStatus by mainViewModel.modelsWithStatus.collectAsState()
            val catalogLoading by mainViewModel.catalogLoading.collectAsState()
            val catalogError by mainViewModel.catalogError.collectAsState()
            val googleModels by mainViewModel.googleModels.collectAsState()
            val googleModelsLoading by mainViewModel.googleModelsLoading.collectAsState()
            val googleModelsError by mainViewModel.googleModelsError.collectAsState()

            var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
            var changelogInfo by remember { mutableStateOf<UpdateInfo?>(null) }
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val lifecycleOwner = LocalLifecycleOwner.current

            DisposableEffect(lifecycleOwner, isShareFlow, settings.transcriptionEngine) {
                val observer = LifecycleEventObserver { _, event ->
                    val shouldCheckForUpdates =
                        !isShareFlow && EngineType.fromKey(settings.transcriptionEngine) == EngineType.CLOUD

                    if (event == Lifecycle.Event.ON_RESUME && shouldCheckForUpdates) {
                        scope.launch {
                            try {
                                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                                val currentVersion = packageInfo.versionName ?: "1.0.0"
                                
                                val updater = AppUpdater()
                                val info = updater.checkForUpdate(currentVersion)
                                if (info.updateAvailable) {
                                    updateInfo = info
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            LaunchedEffect(Unit) {
                scope.launch {
                    try {
                        // Pulisce i vecchi APK residui nella cartella Download
                        val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                        downloadsDir?.listFiles()?.forEach { file ->
                            if (file.isFile && file.name.endsWith(".apk") && file.name.startsWith("transcriber-")) {
                                file.delete()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            // Calcola il nome e lo stato del modello selezionato
            val selectedModelInfo = remember(modelsWithStatus, settings.selectedModelId) {
                modelsWithStatus.find { it.info.id == settings.selectedModelId }
            }
            val selectedModelName = selectedModelInfo?.info?.displayName ?: ""
            val isModelDownloaded = selectedModelInfo?.status == com.anomalyzed.simpletranscriber.data.ModelStatus.Selected || 
                                    selectedModelInfo?.status == com.anomalyzed.simpletranscriber.data.ModelStatus.Downloaded

            val downloadedModels = remember(modelsWithStatus) {
                modelsWithStatus.filter { 
                    it.status is com.anomalyzed.simpletranscriber.data.ModelStatus.Selected || 
                    it.status is com.anomalyzed.simpletranscriber.data.ModelStatus.Downloaded
                }.map { it.info }
            }

            TranscriberTheme {
                if (isShareFlow || showTranscriberDialog) {
                    // In modalita condivisione/notifica mostriamo solo il popup del transcriber.
                    LaunchedEffect(transcriberState) {
                        if (isShareFlow && transcriberState is TranscriberUiState.Success) {
                            mainViewModel.saveTranscription((transcriberState as TranscriberUiState.Success).text)
                        }
                    }

                    val dismissAction: () -> Unit = {
                        if (transcriberState is TranscriberUiState.Loading || transcriberState is TranscriberUiState.Streaming) {
                            transcriberViewModel.refreshNotification(this@MainActivity, transcriberState)
                        }
                        finish()
                    }

                    BackHandler {
                        dismissAction()
                    }

                    TranscriberScreen(
                        uiState = transcriberState,
                        currentApiKey = settings.apiKey,
                        currentLanguage = settings.language,
                        currentCloudModel = settings.selectedCloudModel,
                        currentEngine = settings.transcriptionEngine,
                        selectedModelName = selectedModelName,
                        isModelDownloaded = isModelDownloaded,
                        isAICoreAvailable = mainViewModel.isAICoreAvailable,
                        downloadedModels = downloadedModels,
                        currentLocalModelId = settings.selectedModelId,
                        onUpdateLocalModel = { mainViewModel.updateSelectedModel(it) },
                        onDismiss = dismissAction,
                        onCopyToClipboard = { text -> copyToClipboard(text) },
                        onCancelTranscription = { transcriberViewModel.cancelTranscription(this@MainActivity) },
                        onUpdateApiKey = { mainViewModel.updateApiKey(it) },
                        onUpdateLanguage = { mainViewModel.updateLanguage(it) },
                        onUpdateCloudModel = { mainViewModel.updateSelectedCloudModel(it) },
                        onEngineChange = { 
                            mainViewModel.updateTranscriptionEngine(it)
                            transcriberViewModel.clearError()
                        },
                        onStartTranscription = { startTranscriptionWithNotificationPermission() },
                        googleModels = googleModels
                    )
                } else {
                    val navController = rememberNavController()

                    // Layer principale per navigazione History/Settings/ModelManager
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (!isShareFlow) {
                            Spacer(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                        }
                        NavHost(navController = navController, startDestination = "history") {
                            composable("history") {
                                HistoryScreen(
                                    items = history,
                                    onClearHistory = { mainViewModel.clearHistory() },
                                    onDeleteItem = { mainViewModel.deleteTranscription(it) },
                                    onDeleteItems = { mainViewModel.deleteTranscriptions(it) },
                                    onCopyToClipboard = { copyToClipboard(it) },
                                    onNavigateToSettings = { navController.navigate("settings") }
                                )
                            }
                            composable("settings") {
                                 SettingsScreen(
                                     settings = settings,
                                     isAICoreAvailable = mainViewModel.isAICoreAvailable,
                                     selectedModelName = selectedModelName,
                                     googleModels = googleModels,
                                     googleModelsLoading = googleModelsLoading,
                                     googleModelsError = googleModelsError,
                                     onRetryGoogleModels = { mainViewModel.refreshGoogleModels() },
                                     onNavigateBack = { navController.popBackStack() },
                                     onUpdateLanguage = { mainViewModel.updateLanguage(it) },
                                     onUpdateTranscriptionEngine = { mainViewModel.updateTranscriptionEngine(it) },
                                     onUpdateApiKey = { mainViewModel.updateApiKey(it) },
                                     onUpdateSelectedCloudModel = { mainViewModel.updateSelectedCloudModel(it) },
                                     onNavigateToModelManager = { navController.navigate("model_manager") },
                                    onCheckForUpdates = {
                                        scope.launch {
                                            try {
                                                Toast.makeText(context, "Checking for updates...", Toast.LENGTH_SHORT).show()
                                                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                                                val currentVersion = packageInfo.versionName ?: "1.0.0"
                                                
                                                val updater = AppUpdater()
                                                val info = updater.checkForUpdate(currentVersion)
                                                if (info.updateAvailable) {
                                                    updateInfo = info
                                                } else {
                                                    Toast.makeText(context, "App is already up to date", Toast.LENGTH_SHORT).show()
                                                }
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Failed to check for updates", Toast.LENGTH_SHORT).show()
                                                e.printStackTrace()
                                            }
                                        }
                                    },
                                    onViewChangelog = {
                                        scope.launch {
                                            try {
                                                Toast.makeText(context, "Loading changelog...", Toast.LENGTH_SHORT).show()
                                                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                                                val currentVersion = packageInfo.versionName ?: "1.0.0"
                                                val updater = AppUpdater()
                                                val info = updater.checkForUpdate(currentVersion)
                                                if (info.changelog.isNotBlank()) {
                                                    changelogInfo = info
                                                } else {
                                                    Toast.makeText(context, "Could not load the changelog.", Toast.LENGTH_SHORT).show()
                                                }
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Loading failed", Toast.LENGTH_SHORT).show()
                                                e.printStackTrace()
                                            }
                                        }
                                    }
                                )
                            }
                            composable("model_manager") {
                                ModelManagerScreen(
                                    models = modelsWithStatus,
                                    availableStorage = mainViewModel.modelRepository.getFormattedAvailableStorage(),
                                    deviceRamMb = mainViewModel.modelRepository.getDeviceRamMb(),
                                    isLoading = catalogLoading,
                                    errorMessage = catalogError,
                                    onNavigateBack = { navController.popBackStack() },
                                    onRefreshCatalog = { mainViewModel.refreshCatalog() },
                                    onDownloadModel = { mainViewModel.downloadModel(it) },
                                    onCancelDownload = { mainViewModel.cancelDownload(it) },
                                    onDeleteModel = { mainViewModel.deleteModel(it) },
                                    onSelectModel = { mainViewModel.selectModel(it) }
                                )
                            }
                        }

                    }
                }

                updateInfo?.let { info ->
                    UpdateDialog(
                        updateInfo = info,
                        onDismiss = { updateInfo = null },
                        onConfirm = {
                            val downloadUrl = info.downloadUrl
                            val expectedSha256 = info.expectedSha256
                            if (downloadUrl != null && expectedSha256 != null) {
                                downloadUpdate(downloadUrl, info.versionName, expectedSha256)
                            } else {
                                Toast.makeText(
                                    context,
                                    "Update blocked: missing integrity metadata",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            updateInfo = null
                        }
                    )
                }

                changelogInfo?.let { info ->
                    AlertDialog(
                        onDismissRequest = { changelogInfo = null },
                        title = { Text(text = "Changelog ${info.versionName}") },
                        text = {
                            Column(
                                modifier = Modifier.verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = com.anomalyzed.simpletranscriber.ui.utils.parseMarkdown(info.changelog)
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { changelogInfo = null }) {
                                Text("Close")
                            }
                        }
                    )
                }
            }
        }
    }

    private fun startTranscriptionWithNotificationPermission() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            startTranscriptionAfterNotificationPermission = true
            requestPermissions(
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_POST_NOTIFICATIONS
            )
            return
        }

        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            Toast.makeText(
                this,
                "Enable notifications to run transcription in background.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            val channel = notificationManager?.getNotificationChannel(TranscriptionService.CHANNEL_ID)
            if (channel?.importance == NotificationManager.IMPORTANCE_NONE) {
                Toast.makeText(
                    this,
                    "Enable the Transcriber notification channel to run transcription in background.",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
        }

        transcriberViewModel.startTranscription(this)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode != REQUEST_POST_NOTIFICATIONS || !startTranscriptionAfterNotificationPermission) {
            return
        }

        startTranscriptionAfterNotificationPermission = false
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        if (granted) {
            startTranscriptionWithNotificationPermission()
        } else {
            Toast.makeText(
                this,
                "Notification permission is required to run transcription in background.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun downloadUpdate(url: String, versionName: String, expectedSha256: String) {
        try {
            val normalizedSha256 = UpdateIntegrity.normalizeSha256(expectedSha256)
            if (normalizedSha256 == null) {
                Toast.makeText(this, "Update blocked: invalid SHA-256 metadata", Toast.LENGTH_LONG).show()
                return
            }

            val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            if (dir != null && !dir.exists()) {
                dir.mkdirs()
            }

            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("Transcriber Update")
                .setDescription("Downloading version $versionName")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, "transcriber-$versionName.apk")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = downloadManager.enqueue(request)
            DownloadReceiver.setPendingDownload(downloadId, normalizedSha256, versionName)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Download error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        isShareFlow = intent.action == Intent.ACTION_SEND
        showTranscriberDialog = intent.getBooleanExtra(EXTRA_SHOW_TRANSCRIBER_DIALOG, false)
        intent.getLongExtra(EXTRA_TRANSCRIPTION_ID, NO_TRANSCRIPTION_ID)
            .takeIf { it != NO_TRANSCRIPTION_ID }
            ?.let { transcriberViewModel.selectTranscription(it) }
        if (isShareFlow) {
            handleIncomingIntent()
        }
    }

    private fun handleIncomingIntent() {
        val audioUri: Uri? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(android.content.Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(android.content.Intent.EXTRA_STREAM)
        }
        audioUri?.let { transcriberViewModel.setPendingAudio(it) }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Transcriber", text))
        Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show()
    }
}
