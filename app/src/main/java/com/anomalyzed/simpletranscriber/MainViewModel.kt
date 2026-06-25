package com.anomalyzed.simpletranscriber

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anomalyzed.simpletranscriber.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

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
                language = "Italian",
                opacity = 0.95f,
                theme = "System",
                playbackMinSpeed = 0.75f,
                playbackMaxSpeed = 1.75f,
                enableProximity = false,
                defaultAction = "Show actions",
                transcriptionEngine = "cloud",
                selectedModelId = "",
                selectedCloudModel = "gemini-flash-latest",
                modelCatalogUrl = PreferenceManager.DEFAULT_CATALOG_URL
            )
        )

    // ── Catalogo modelli ─────────────────────────────────────────────

    private val _modelCatalog = MutableStateFlow<List<ModelInfo>>(emptyList())
    private val _modelStateVersion = MutableStateFlow(0)

    private val _catalogLoading = MutableStateFlow(false)
    val catalogLoading: StateFlow<Boolean> = _catalogLoading.asStateFlow()

    private val _catalogError = MutableStateFlow<String?>(null)
    val catalogError: StateFlow<String?> = _catalogError.asStateFlow()

    // ── Dynamic Google Cloud Models ──────────────────────────────────
    private val _googleModels = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val googleModels: StateFlow<List<Pair<String, String>>> = _googleModels.asStateFlow()

    private val _googleModelsLoading = MutableStateFlow(false)
    val googleModelsLoading: StateFlow<Boolean> = _googleModelsLoading.asStateFlow()

    private val _googleModelsError = MutableStateFlow<String?>(null)
    val googleModelsError: StateFlow<String?> = _googleModelsError.asStateFlow()

    init {
        refreshCatalog()
        viewModelScope.launch {
            settings.map { it.apiKey }.distinctUntilChanged().collect { apiKey ->
                refreshGoogleModels(apiKey)
            }
        }
    }

    /** Modelli con stato (combinazione catalogo + download attivi + selezione) */
    val modelsWithStatus: StateFlow<List<ModelWithStatus>> = combine(
        _modelCatalog,
        settings.map { it.selectedModelId }.distinctUntilChanged(),
        modelRepository.activeDownloads,
        _modelStateVersion
    ) { catalog, selectedId, _, _ ->
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
            _modelStateVersion.value += 1
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
            _modelStateVersion.value += 1
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

    fun deleteTranscription(id: Int) {
        viewModelScope.launch { dao.deleteById(id) }
    }

    fun deleteTranscriptions(ids: Set<Int>) {
        viewModelScope.launch { dao.deleteByIds(ids) }
    }

    // ── Update settings ──────────────────────────────────────────────

    fun updateLanguage(lang: String) = viewModelScope.launch { prefManager.updateLanguage(lang) }

    fun updateApiKey(key: String) = viewModelScope.launch { prefManager.updateApiKey(key) }
    fun updateTranscriptionEngine(engine: String) = viewModelScope.launch { prefManager.updateTranscriptionEngine(engine) }
    fun updateSelectedModel(modelId: String) = viewModelScope.launch { prefManager.updateSelectedModel(modelId) }
    fun updateSelectedCloudModel(modelName: String) = viewModelScope.launch { prefManager.updateSelectedCloudModel(modelName) }

    // ── Dynamic Google Models Retrieval ──────────────────────────────

    fun refreshGoogleModels(apiKey: String? = null) {
        val key = apiKey ?: settings.value.apiKey
        if (key.isBlank()) {
            _googleModels.value = emptyList()
            _googleModelsError.value = null
            _googleModelsLoading.value = false
            return
        }

        viewModelScope.launch {
            _googleModelsLoading.value = true
            _googleModelsError.value = null
            try {
                val models = fetchGoogleModelsList(key)
                _googleModels.value = models
                
                val currentSelected = settings.value.selectedCloudModel
                if (models.isNotEmpty() && models.none { it.first == currentSelected }) {
                    // Try to autoselect gemini-2.5-flash or gemini-1.5-flash if they exist in retrieved models list,
                    // otherwise default to first available model.
                    val defaultModelName = models.find { it.first.contains("2.5-flash") }?.first
                        ?: models.find { it.first.contains("1.5-flash") }?.first
                        ?: models.first().first
                    updateSelectedCloudModel(defaultModelName)
                }
            } catch (e: Exception) {
                _googleModelsError.value = e.localizedMessage ?: "Unknown error"
                _googleModels.value = emptyList()
            } finally {
                _googleModelsLoading.value = false
            }
        }
    }

    private suspend fun fetchGoogleModelsList(apiKey: String): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val urlConnection = java.net.URL("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
            .openConnection() as java.net.HttpURLConnection
        urlConnection.connectTimeout = 10_000
        urlConnection.readTimeout = 10_000
        urlConnection.requestMethod = "GET"
        
        try {
            val responseCode = urlConnection.responseCode
            if (responseCode != 200) {
                val errorStream = urlConnection.errorStream?.bufferedReader()?.use { it.readText() }
                val errorMsg = try {
                    if (!errorStream.isNullOrBlank()) {
                        org.json.JSONObject(errorStream)
                            .getJSONObject("error")
                            .getString("message")
                    } else null
                } catch (jsonEx: Exception) {
                    null
                }
                throw IOException(errorMsg ?: "HTTP error code: $responseCode")
            }
            
            val jsonString = urlConnection.inputStream.bufferedReader().use { it.readText() }
            val root = org.json.JSONObject(jsonString)
            val modelsArray = root.getJSONArray("models")
            val retrievedModels = mutableListOf<Pair<String, String>>()
            
            val excludeKeywords = listOf("embed", "audio", "image", "tts", "video", "tool", "robotics", "computer")
            
            for (i in 0 until modelsArray.length()) {
                val modelObj = modelsArray.getJSONObject(i)
                val name = modelObj.getString("name")
                val nameLower = name.lowercase()
                val displayName = modelObj.optString("displayName", name)
                
                val supportedMethods = modelObj.optJSONArray("supportedGenerationMethods")
                var isGenerative = false
                if (supportedMethods != null) {
                    for (j in 0 until supportedMethods.length()) {
                        if (supportedMethods.getString(j) == "generateContent") {
                            isGenerative = true
                            break
                        }
                    }
                }
                
                if (isGenerative && (nameLower.contains("gemini") || nameLower.contains("gemma"))) {
                    if (excludeKeywords.any { nameLower.contains(it) }) {
                        continue
                    }
                    val cleanName = name.replace("models/", "")
                    retrievedModels.add(cleanName to displayName)
                }
            }
            
            sortGoogleModels(retrievedModels)
            retrievedModels
        } finally {
            urlConnection.disconnect()
        }
    }

    private fun sortGoogleModels(models: MutableList<Pair<String, String>>) {
        val regex = Regex("(?:gemini|gemma)-?(\\d+(?:\\.\\d+)?)")
        
        models.sortWith { a, b ->
            val aName = a.first.lowercase()
            val bName = b.first.lowercase()
            
            val aLatest = if (aName.contains("latest")) 0 else 1
            val bLatest = if (bName.contains("latest")) 0 else 1
            if (aLatest != bLatest) return@sortWith aLatest.compareTo(bLatest)
            
            val aVersion = extractVersion(aName, regex)
            val bVersion = extractVersion(bName, regex)
            if (aVersion != bVersion) return@sortWith bVersion.compareTo(aVersion)
            
            val aBrand = if (aName.contains("gemini")) 1 else 2
            val bBrand = if (bName.contains("gemini")) 1 else 2
            if (aBrand != bBrand) return@sortWith aBrand.compareTo(bBrand)
            
            val aFlavor = when {
                aName.contains("flash") -> 1
                aName.contains("pro") -> 2
                else -> 3
            }
            val bFlavor = when {
                bName.contains("flash") -> 1
                bName.contains("pro") -> 2
                else -> 3
            }
            if (aFlavor != bFlavor) return@sortWith aFlavor.compareTo(bFlavor)
            
            bName.compareTo(aName)
        }
    }

    private fun extractVersion(name: String, regex: Regex): Double {
        val match = regex.find(name)
        if (match != null) {
            val valStr = match.groupValues[1]
            val fullMatch = match.value
            val idx = name.indexOf(fullMatch) + fullMatch.length
            if (idx < name.length && name[idx] == 'b') {
                return 1.0
            }
            return valStr.toDoubleOrNull() ?: 1.0
        }
        return 1.0
    }
}
