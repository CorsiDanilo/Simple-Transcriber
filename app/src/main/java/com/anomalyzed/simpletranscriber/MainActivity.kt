package com.anomalyzed.simpletranscriber

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
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
import com.anomalyzed.simpletranscriber.ui.updater.UpdateDialog
import android.app.DownloadManager
import android.os.Environment
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()
    private val transcriberViewModel: TranscriberViewModel by viewModels()
    private var isShareFlow by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        isShareFlow = intent?.action == Intent.ACTION_SEND

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

            var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
            val context = LocalContext.current
            val scope = rememberCoroutineScope()

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

            // Calcola il nome e lo stato del modello selezionato
            val selectedModelInfo = remember(modelsWithStatus, settings.selectedModelId) {
                modelsWithStatus.find { it.info.id == settings.selectedModelId }
            }
            val selectedModelName = selectedModelInfo?.info?.displayName ?: ""
            val isModelDownloaded = selectedModelInfo?.status == com.anomalyzed.simpletranscriber.data.ModelStatus.Selected || 
                                    selectedModelInfo?.status == com.anomalyzed.simpletranscriber.data.ModelStatus.Downloaded

            TranscriberTheme {
                if (isShareFlow) {
                    // In modalita condivisione mostriamo solo il popup del transcriber.
                    LaunchedEffect(transcriberState) {
                        if (transcriberState is TranscriberUiState.Success) {
                            mainViewModel.saveTranscription((transcriberState as TranscriberUiState.Success).text)
                        }
                    }

                    BackHandler {
                        if (transcriberState is TranscriberUiState.Loading || transcriberState is TranscriberUiState.Streaming) {
                            moveTaskToBack(true)
                        } else {
                            finish()
                        }
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
                        onDismiss = {
                            if (transcriberState is TranscriberUiState.Loading || transcriberState is TranscriberUiState.Streaming) {
                                moveTaskToBack(true)
                            } else {
                                finish()
                            }
                        },
                        onCopyToClipboard = { text -> copyToClipboard(text) },
                        onUpdateApiKey = { mainViewModel.updateApiKey(it) },
                        onUpdateLanguage = { mainViewModel.updateLanguage(it) },
                        onUpdateCloudModel = { mainViewModel.updateSelectedCloudModel(it) },
                        onEngineChange = { 
                            mainViewModel.updateTranscriptionEngine(it)
                            transcriberViewModel.clearError()
                        },
                        onStartTranscription = { 
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
                            }
                            transcriberViewModel.startTranscription(this@MainActivity) 
                        }
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
                                    onNavigateBack = { navController.popBackStack() },
                                    onUpdateLanguage = { mainViewModel.updateLanguage(it) },
                                    onUpdateTranscriptionEngine = { mainViewModel.updateTranscriptionEngine(it) },
                                    onUpdateApiKey = { mainViewModel.updateApiKey(it) },
                                    onUpdateSelectedCloudModel = { mainViewModel.updateSelectedCloudModel(it) },
                                    onNavigateToModelManager = { navController.navigate("model_manager") }
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
                            info.downloadUrl?.let { url ->
                                downloadUpdate(url, info.versionName)
                            }
                            updateInfo = null
                        }
                    )
                }
            }
        }
    }

    private fun downloadUpdate(url: String, versionName: String) {
        try {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("Aggiornamento Transcriber")
                .setDescription("Scaricando la versione $versionName")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, "transcriber-$versionName.apk")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                
            val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            DownloadReceiver.enqueuedDownloadId = downloadManager.enqueue(request)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Errore nel download: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        isShareFlow = intent.action == Intent.ACTION_SEND
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
