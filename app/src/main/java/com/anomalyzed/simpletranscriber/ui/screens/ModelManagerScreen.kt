package com.anomalyzed.simpletranscriber.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anomalyzed.simpletranscriber.data.ModelBackend
import com.anomalyzed.simpletranscriber.data.ModelInfo
import com.anomalyzed.simpletranscriber.data.ModelStatus
import com.anomalyzed.simpletranscriber.data.ModelWithStatus
import com.anomalyzed.simpletranscriber.ui.theme.DarkGray
import com.anomalyzed.simpletranscriber.ui.theme.Gold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagerScreen(
    models: List<ModelWithStatus>,
    availableStorage: String,
    deviceRamMb: Int,
    isLoading: Boolean,
    errorMessage: String?,
    onNavigateBack: () -> Unit,
    onRefreshCatalog: () -> Unit,
    onDownloadModel: (ModelInfo) -> Unit,
    onCancelDownload: (String) -> Unit,
    onDeleteModel: (ModelInfo) -> Unit,
    onSelectModel: (String) -> Unit
) {
    var selectedBackend by remember { mutableStateOf<ModelBackend?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var downloadedOnly by remember { mutableStateOf(false) }
    var multilingualOnly by remember { mutableStateOf(false) }
    var quantizedOnly by remember { mutableStateOf(false) }
    var fitsDeviceOnly by remember { mutableStateOf(false) }
    var smallSizeOnly by remember { mutableStateOf(false) }

    val visibleModels = remember(
        models,
        selectedBackend,
        searchQuery,
        downloadedOnly,
        multilingualOnly,
        quantizedOnly,
        fitsDeviceOnly,
        smallSizeOnly,
        deviceRamMb
    ) {
        val query = searchQuery.trim().lowercase()
        models
            .asSequence()
            .filter { selectedBackend == null || it.info.backend == selectedBackend }
            .filter { model ->
                query.isBlank() ||
                    model.info.displayName.lowercase().contains(query) ||
                    model.info.description.lowercase().contains(query) ||
                    model.info.id.lowercase().contains(query) ||
                    model.info.fileName.lowercase().contains(query) ||
                    model.info.quantization.lowercase().contains(query)
            }
            .filter { !downloadedOnly || it.status is ModelStatus.Downloaded || it.status is ModelStatus.Selected }
            .filter { !multilingualOnly || !it.info.isEnglishOnly }
            .filter { !quantizedOnly || it.info.quantization.isNotBlank() }
            .filter { !fitsDeviceOnly || it.info.minRamMb == 0 || deviceRamMb >= it.info.minRamMb }
            .filter { !smallSizeOnly || it.info.sizeBytes <= 500L * 1024L * 1024L }
            .sortedWith(
                compareByDescending<ModelWithStatus> {
                    it.status is ModelStatus.Selected || it.status is ModelStatus.Downloaded
                }.thenBy { it.info.backend.ordinal }
                    .thenBy { it.info.sizeBytes }
                    .thenBy { it.info.displayName }
            )
            .toList()
    }
    val downloadedModels = remember(visibleModels) {
        visibleModels.filter {
            it.status is ModelStatus.Downloaded || it.status is ModelStatus.Selected
        }
    }
    val availableModels = remember(visibleModels) {
        visibleModels.filter {
            it.status is ModelStatus.Available || it.status is ModelStatus.Downloading
        }
    }

    // Carica il catalogo all'apertura della schermata
    LaunchedEffect(Unit) {
        onRefreshCatalog()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Model Manager", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onRefreshCatalog, enabled = !isLoading) {
                        Icon(Icons.Default.Refresh, "Refresh catalog")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // ── Storage Info ──
            item {
                StorageInfoCard(availableStorage)
            }

            item {
                ModelBackendFilter(
                    selectedBackend = selectedBackend,
                    onBackendSelected = { selectedBackend = it }
                )
            }

            item {
                ModelSearchAndFilters(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    downloadedOnly = downloadedOnly,
                    onDownloadedOnlyChange = { downloadedOnly = it },
                    multilingualOnly = multilingualOnly,
                    onMultilingualOnlyChange = { multilingualOnly = it },
                    quantizedOnly = quantizedOnly,
                    onQuantizedOnlyChange = { quantizedOnly = it },
                    fitsDeviceOnly = fitsDeviceOnly,
                    onFitsDeviceOnlyChange = { fitsDeviceOnly = it },
                    smallSizeOnly = smallSizeOnly,
                    onSmallSizeOnlyChange = { smallSizeOnly = it }
                )
            }

            // ── Error Message ──
            if (errorMessage != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                errorMessage,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // ── Loading ──
            if (isLoading && visibleModels.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(modifier = Modifier.size(36.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("Loading catalog...", color = Color.Gray, fontSize = 14.sp)
                        }
                    }
                }
            }

            // ── Downloaded Models ──
            if (downloadedModels.isNotEmpty()) {
                item {
                    Text(
                        "Downloaded",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                }
                items(
                    items = downloadedModels,
                    key = { it.info.id },
                    contentType = { "downloadedModel" }
                ) { model ->
                    DownloadedModelCard(
                        model = model,
                        onSelect = { onSelectModel(model.info.id) },
                        onDelete = { onDeleteModel(model.info) }
                    )
                }
            }

            // ── Available Models ──
            if (availableModels.isNotEmpty()) {
                item {
                    Text(
                        "Available",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                }
                items(
                    items = availableModels,
                    key = { it.info.id },
                    contentType = { "availableModel" }
                ) { model ->
                    AvailableModelCard(
                        model = model,
                        deviceRamMb = deviceRamMb,
                        onDownload = { onDownloadModel(model.info) },
                        onCancel = { onCancelDownload(model.info.id) }
                    )
                }
            }

            // ── Empty state ──
            if (visibleModels.isEmpty() && !isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.CloudOff,
                                null,
                                modifier = Modifier.size(48.dp),
                                tint = Color.Gray
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "No models available",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = onRefreshCatalog) {
                                Text("Refresh")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageInfoCard(availableStorage: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkGray)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.Storage,
                null,
                tint = Gold,
                modifier = Modifier.size(28.dp)
            )
            Column {
                Text("Available Storage", fontSize = 12.sp, color = Color.Gray)
                Text(
                    availableStorage,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelBackendFilter(
    selectedBackend: ModelBackend?,
    onBackendSelected: (ModelBackend?) -> Unit
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = selectedBackend == null,
            onClick = { onBackendSelected(null) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
            label = { Text("All") }
        )
        SegmentedButton(
            selected = selectedBackend == ModelBackend.LITERT,
            onClick = { onBackendSelected(ModelBackend.LITERT) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
            label = { Text("Gemma") }
        )
        SegmentedButton(
            selected = selectedBackend == ModelBackend.WHISPER_CPP,
            onClick = { onBackendSelected(ModelBackend.WHISPER_CPP) },
            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
            label = { Text("Whisper") }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelSearchAndFilters(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    downloadedOnly: Boolean,
    onDownloadedOnlyChange: (Boolean) -> Unit,
    multilingualOnly: Boolean,
    onMultilingualOnlyChange: (Boolean) -> Unit,
    quantizedOnly: Boolean,
    onQuantizedOnlyChange: (Boolean) -> Unit,
    fitsDeviceOnly: Boolean,
    onFitsDeviceOnlyChange: (Boolean) -> Unit,
    smallSizeOnly: Boolean,
    onSmallSizeOnlyChange: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search models") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Close, "Clear search")
                    }
                }
            },
            shape = RoundedCornerShape(12.dp)
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ModelFilterChip(
                label = "Downloaded",
                selected = downloadedOnly,
                onSelectedChange = onDownloadedOnlyChange
            )
            ModelFilterChip(
                label = "Multilingual",
                selected = multilingualOnly,
                onSelectedChange = onMultilingualOnlyChange
            )
            ModelFilterChip(
                label = "Quantized",
                selected = quantizedOnly,
                onSelectedChange = onQuantizedOnlyChange
            )
            ModelFilterChip(
                label = "Fits device",
                selected = fitsDeviceOnly,
                onSelectedChange = onFitsDeviceOnlyChange
            )
            ModelFilterChip(
                label = "< 500 MB",
                selected = smallSizeOnly,
                onSelectedChange = onSmallSizeOnlyChange
            )
        }
    }
}

@Composable
private fun ModelFilterChip(
    label: String,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = { onSelectedChange(!selected) },
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
        } else {
            null
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Gold.copy(alpha = 0.18f),
            selectedLabelColor = Gold,
            selectedLeadingIconColor = Gold
        )
    )
}

@Composable
private fun ModelBadges(model: ModelInfo) {
    Row(
        modifier = Modifier.padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ModelBadge(
            text = model.backend.label,
            color = if (model.backend == ModelBackend.LITERT) Gold else Color(0xFF64B5F6)
        )
        if (model.quantization.isNotBlank()) {
            ModelBadge(text = model.quantization, color = Color(0xFF81C784))
        }
        if (model.isEnglishOnly) {
            ModelBadge(text = "EN", color = Color(0xFFFFB74D))
        }
    }
}

@Composable
private fun ModelBadge(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.14f),
        contentColor = color
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun DownloadedModelCard(
    model: ModelWithStatus,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val isSelected = model.status is ModelStatus.Selected

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkGray),
        border = if (isSelected) {
            CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(Gold)
            )
        } else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icona + nome
                Icon(
                    if (isSelected) Icons.Default.CheckCircle else Icons.Default.DownloadDone,
                    null,
                    tint = if (isSelected) Gold else Color.LightGray,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        model.info.displayName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        model.info.description,
                        fontSize = 12.sp,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    ModelBadges(model.info)
                }
                Text(
                    model.info.formattedSize,
                    fontSize = 13.sp,
                    color = Color.Gray
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (isSelected) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Bolt,
                            null,
                            tint = Gold,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Selected", fontSize = 12.sp, color = Gold, fontWeight = FontWeight.Medium)
                    }
                } else {
                    TextButton(onClick = onSelect, contentPadding = PaddingValues(0.dp)) {
                        Text("Tap to select", fontSize = 12.sp)
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        "Delete model",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AvailableModelCard(
    model: ModelWithStatus,
    deviceRamMb: Int,
    onDownload: () -> Unit,
    onCancel: () -> Unit
) {
    val isDownloading = model.status is ModelStatus.Downloading
    val downloadProgress = (model.status as? ModelStatus.Downloading)?.progress ?: 0f
    val hasEnoughRam = deviceRamMb >= model.info.minRamMb || model.info.minRamMb == 0

    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkGray)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (isDownloading) Icons.Default.Downloading else Icons.Default.CloudDownload,
                    null,
                    tint = if (isDownloading) Gold else Color.LightGray,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        model.info.displayName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        model.info.description,
                        fontSize = 12.sp,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    ModelBadges(model.info)
                }
                Text(
                    model.info.formattedSize,
                    fontSize = 13.sp,
                    color = Color.Gray
                )
            }

            // RAM warning
            if (!hasEnoughRam) {
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        null,
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        "Requires ${model.info.minRamMb / 1024} GB RAM (your device: ${deviceRamMb / 1024} GB)",
                        fontSize = 11.sp,
                        color = Color(0xFFFF9800)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            if (isDownloading) {
                // Progress bar + cancel
                Column {
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = Gold,
                        trackColor = Color.DarkGray,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${(downloadProgress * 100).toInt()}%",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        TextButton(onClick = onCancel, contentPadding = PaddingValues(0.dp)) {
                            Icon(
                                Icons.Default.Close,
                                null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Cancel", fontSize = 12.sp)
                        }
                    }
                }
            } else {
                // Download button
                Button(
                    onClick = onDownload,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Gold.copy(alpha = 0.15f),
                        contentColor = Gold
                    )
                ) {
                    Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Download")
                }
            }
        }
    }
}
