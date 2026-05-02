package com.example.simpletranscriberapp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
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
import com.example.simpletranscriberapp.ui.TranscriberScreen
import com.example.simpletranscriberapp.ui.screens.HistoryScreen
import com.example.simpletranscriberapp.ui.screens.ModelManagerScreen
import com.example.simpletranscriberapp.ui.screens.SettingsScreen
import com.example.simpletranscriberapp.ui.theme.SimpleTranscriberAppTheme

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

            // Calcola il nome e lo stato del modello selezionato
            val selectedModelInfo = remember(modelsWithStatus, settings.selectedModelId) {
                modelsWithStatus.find { it.info.id == settings.selectedModelId }
            }
            val selectedModelName = selectedModelInfo?.info?.displayName ?: ""
            val isModelDownloaded = selectedModelInfo?.status == com.example.simpletranscriberapp.data.ModelStatus.Selected || 
                                    selectedModelInfo?.status == com.example.simpletranscriberapp.data.ModelStatus.Downloaded

            SimpleTranscriberAppTheme {
                if (isShareFlow) {
                    // In modalita condivisione mostriamo solo il popup del transcriber.
                    LaunchedEffect(transcriberState) {
                        if (transcriberState is TranscriberUiState.Success) {
                            mainViewModel.saveTranscription((transcriberState as TranscriberUiState.Success).text)
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
                        onDismiss = { finish() },
                        onCopyToClipboard = { text -> copyToClipboard(text) },
                        onUpdateApiKey = { mainViewModel.updateApiKey(it) },
                        onUpdateLanguage = { mainViewModel.updateLanguage(it) },
                        onUpdateCloudModel = { mainViewModel.updateSelectedCloudModel(it) },
                        onEngineChange = { 
                            mainViewModel.updateTranscriptionEngine(it)
                            transcriberViewModel.clearError()
                        },
                        onStartTranscription = { transcriberViewModel.startTranscription(contentResolver) }
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
                                    onUpdateOpacity = { mainViewModel.updateOpacity(it) },
                                    onUpdateTheme = { mainViewModel.updateTheme(it) },
                                    onUpdateProximity = { mainViewModel.updateProximity(it) },
                                    onUpdateDefaultAction = { mainViewModel.updateDefaultAction(it) },
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
            }
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
        clipboard.setPrimaryClip(ClipData.newPlainText("Transcription", text))
        Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show()
    }
}
