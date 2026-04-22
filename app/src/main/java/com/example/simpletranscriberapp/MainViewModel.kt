package com.example.simpletranscriberapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.simpletranscriberapp.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val dao = db.transcriptionDao()
    private val prefManager = PreferenceManager(application)
    val modelRepository = ModelRepository(application)

    // Cronologia (Room)
    val historyItems: StateFlow<List<TranscriptionItem>> = dao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Impostazioni (DataStore)
    val settings: StateFlow<UserSettings> = prefManager.settingsFlow
        .stateIn(
            viewModelScope, 
            SharingStarted.WhileSubscribed(5000), 
            UserSettings(
                apiKey = "",
                isPremium = true,
                language = "English",
                opacity = 0.95f,
                theme = "System",
                playbackMinSpeed = 0.75f,
                playbackMaxSpeed = 1.75f,
                enableProximity = false,
                defaultAction = "Show actions",
                transcriptionEngine = "cloud",
                selectedModelId = "",
                modelCatalogUrl = PreferenceManager.DEFAULT_CATALOG_URL
            )
        )

    // ── Catalogo modelli ─────────────────────────────────────────────

    private val _modelCatalog = MutableStateFlow<List<ModelInfo>>(emptyList())

    private val _catalogLoading = MutableStateFlow(false)
    val catalogLoading: StateFlow<Boolean> = _catalogLoading.asStateFlow()

    private val _catalogError = MutableStateFlow<String?>(null)
    val catalogError: StateFlow<String?> = _catalogError.asStateFlow()

    init {
        refreshCatalog()
    }

    /** Modelli con stato (combinazione catalogo + download attivi + selezione) */
    val modelsWithStatus: StateFlow<List<ModelWithStatus>> = combine(
        _modelCatalog,
        settings.map { it.selectedModelId }.distinctUntilChanged(),
        modelRepository.activeDownloads
    ) { catalog, selectedId, _ ->
        modelRepository.getModelsWithStatus(catalog, selectedId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Verifica se AICore è disponibile sul device */
    val isAICoreAvailable: Boolean by lazy {
        try {
            getApplication<Application>().packageManager.getPackageInfo(
                "com.google.android.aicore",
                android.content.pm.PackageManager.GET_META_DATA
            )
            true
        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
            false
        }
    }

    // ── Azioni catalogo ──────────────────────────────────────────────

    fun refreshCatalog() {
        viewModelScope.launch {
            _catalogLoading.value = true
            _catalogError.value = null
            
            val url = settings.value.modelCatalogUrl
            val result = modelRepository.fetchModelCatalog(url)
            
            result.fold(
                onSuccess = { models ->
                    _modelCatalog.value = models
                },
                onFailure = { error ->
                    _catalogError.value = error.localizedMessage ?: "Failed to load model catalog"
                }
            )
            
            _catalogLoading.value = false
        }
    }

    fun downloadModel(model: ModelInfo) {
        viewModelScope.launch {
            val result = modelRepository.downloadModel(model)
            result.onFailure { error ->
                if (error !is kotlinx.coroutines.CancellationException) {
                    _catalogError.value = "Download failed: ${error.localizedMessage}"
                }
            }
            // Dopo il download, aggiorna la lista
            _modelCatalog.value = _modelCatalog.value // trigger recomposition
        }
    }

    fun cancelDownload(modelId: String) {
        modelRepository.cancelDownload(modelId)
    }

    fun deleteModel(model: ModelInfo) {
        viewModelScope.launch {
            // Se il modello eliminato era selezionato, deselezionalo
            if (settings.value.selectedModelId == model.id) {
                prefManager.updateSelectedModel("")
            }
            modelRepository.deleteModel(model)
            // Trigger recomposition
            _modelCatalog.value = _modelCatalog.value.toList()
        }
    }

    fun selectModel(modelId: String) {
        viewModelScope.launch {
            prefManager.updateSelectedModel(modelId)
        }
    }

    // ── Trascrizioni ─────────────────────────────────────────────────

    fun saveTranscription(text: String) {
        viewModelScope.launch {
            dao.insert(TranscriptionItem(timestamp = System.currentTimeMillis(), text = text))
        }
    }

    fun clearHistory() {
        viewModelScope.launch { dao.clearAll() }
    }

    // ── Update settings ──────────────────────────────────────────────

    fun updateLanguage(lang: String) = viewModelScope.launch { prefManager.updateLanguage(lang) }
    fun updateOpacity(value: Float) = viewModelScope.launch { prefManager.updateOpacity(value) }
    fun updateTheme(theme: String) = viewModelScope.launch { prefManager.updateTheme(theme) }
    fun updateProximity(enabled: Boolean) = viewModelScope.launch { prefManager.updateProximity(enabled) }
    fun updateDefaultAction(action: String) = viewModelScope.launch { prefManager.updateDefaultAction(action) }
    fun updateApiKey(key: String) = viewModelScope.launch { prefManager.updateApiKey(key) }
    fun updateTranscriptionEngine(engine: String) = viewModelScope.launch { prefManager.updateTranscriptionEngine(engine) }
    fun updateSelectedModel(modelId: String) = viewModelScope.launch { prefManager.updateSelectedModel(modelId) }
}
