package com.example.simpletranscriberapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.simpletranscriberapp.R
import com.example.simpletranscriberapp.TranscriberUiState
import com.example.simpletranscriberapp.engine.EngineType
import com.example.simpletranscriberapp.ui.theme.Gold

@Composable
fun TranscriberScreen(
    uiState: TranscriberUiState,
    onDismiss: () -> Unit,
    onCopyToClipboard: (String) -> Unit,
    onStartTranscription: () -> Unit,
    onUpdateApiKey: (String) -> Unit,
    onUpdateLanguage: (String) -> Unit,
    onUpdateCloudModel: (String) -> Unit,
    onEngineChange: (String) -> Unit,
    currentApiKey: String,
    currentLanguage: String,
    currentCloudModel: String = "gemini-flash-latest",
    currentEngine: String = "cloud",
    selectedModelName: String = "",
    isModelDownloaded: Boolean = false,
    isAICoreAvailable: Boolean = false
) {
    val engineType = EngineType.fromKey(currentEngine)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f)) // Sfondo semi-trasparente (scrim)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .clip(RoundedCornerShape(32.dp))
                .clickable(enabled = false, onClick = {}),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Translate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Transcription", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                }

                // Engine chip
                EngineChip(
                    engineType = engineType, 
                    selectedModelName = selectedModelName,
                    isAICoreAvailable = isAICoreAvailable,
                    onEngineChange = onEngineChange
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                val state = uiState
                when (state) {
                    is TranscriberUiState.Setup -> SetupContent(
                        apiKey = currentApiKey,
                        selectedLanguage = currentLanguage,
                        selectedCloudModel = currentCloudModel,
                        engineType = engineType,
                        selectedModelName = selectedModelName,
                        isModelDownloaded = isModelDownloaded,
                        onApiKeyChange = onUpdateApiKey,
                        onLanguageChange = onUpdateLanguage,
                        onCloudModelChange = onUpdateCloudModel,
                        onStart = onStartTranscription
                    )
                    is TranscriberUiState.Loading -> LoadingContent(state.progressMessage)
                    is TranscriberUiState.Success -> SuccessContent(state.text, onCopyToClipboard)
                    is TranscriberUiState.Error -> ErrorContent(
                        msg = state.message,
                        onRetry = { onEngineChange(currentEngine) }
                    )
                }

                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.btn_close))
                }
            }
        }
    }
}

@Composable
private fun EngineChip(
    engineType: EngineType, 
    selectedModelName: String,
    isAICoreAvailable: Boolean,
    onEngineChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val (icon, label, color) = when (engineType) {
        EngineType.CLOUD -> Triple(Icons.Default.Cloud, "Cloud", Color(0xFF64B5F6))
        EngineType.AICORE -> Triple(Icons.Default.Memory, "AICore", Color(0xFF81C784))
        EngineType.LITERT -> Triple(
            Icons.Default.PhoneAndroid,
            selectedModelName.ifBlank { "Local" },
            Gold
        )
    }

    Box {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = color.copy(alpha = 0.15f),
            contentColor = color,
            modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { expanded = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(icon, null, modifier = Modifier.size(14.dp))
                Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(16.dp))
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Cloud (Gemini API)") },
                onClick = { onEngineChange(EngineType.CLOUD.key); expanded = false },
                leadingIcon = { Icon(Icons.Default.Cloud, null, modifier = Modifier.size(20.dp), tint = Color(0xFF64B5F6)) }
            )
            DropdownMenuItem(
                text = { Text("AICore (On-Device)") },
                onClick = { onEngineChange(EngineType.AICORE.key); expanded = false },
                leadingIcon = { Icon(Icons.Default.Memory, null, modifier = Modifier.size(20.dp), tint = Color(0xFF81C784)) },
                enabled = isAICoreAvailable
            )
            DropdownMenuItem(
                text = { Text("Local Model (LiteRT-LM)") },
                onClick = { onEngineChange(EngineType.LITERT.key); expanded = false },
                leadingIcon = { Icon(Icons.Default.PhoneAndroid, null, modifier = Modifier.size(20.dp), tint = Gold) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupContent(
    apiKey: String,
    selectedLanguage: String,
    selectedCloudModel: String,
    engineType: EngineType,
    selectedModelName: String,
    isModelDownloaded: Boolean,
    onApiKeyChange: (String) -> Unit,
    onLanguageChange: (String) -> Unit,
    onCloudModelChange: (String) -> Unit,
    onStart: () -> Unit
) {
    var langExpanded by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }
    var isApiKeyVisible by remember { mutableStateOf(false) }
    // Una lista espandibile con le principali lingue. Per "tutte" usiamo un subset comune.
    val languages = listOf("Italian", "English", "Spanish", "French", "German", "Portuguese", "Russian", "Chinese", "Japanese", "Arabic")

    // Determina se il pulsante Start è abilitato
    val isStartEnabled = when (engineType) {
        EngineType.CLOUD -> apiKey.isNotBlank()
        EngineType.AICORE -> true
        EngineType.LITERT -> selectedModelName.isNotBlank() && isModelDownloaded
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // API Key Input — solo per Cloud
        if (engineType == EngineType.CLOUD) {
            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                label = { Text(stringResource(R.string.label_api_key)) },
                placeholder = { Text(stringResource(R.string.placeholder_api_key)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Default.VpnKey, null) },
                trailingIcon = {
                    IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                        Icon(
                            imageVector = if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (isApiKeyVisible) "Hide API key" else "Show API key"
                        )
                    }
                },
                visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true
            )

            // Cloud Model Dropdown
            Box(modifier = Modifier.fillMaxWidth()) {
                ExposedDropdownMenuBox(
                    expanded = modelExpanded,
                    onExpandedChange = { modelExpanded = !modelExpanded }
                ) {
                    val cloudModels = listOf(
                        "gemini-flash-latest" to "Gemini Flash (Latest)",
                        "gemini-flash-lite-latest" to "Gemini Flash-Lite (Latest)"
                    )
                    val currentModelLabel = cloudModels.find { it.first == selectedCloudModel }?.second ?: selectedCloudModel

                    OutlinedTextField(
                        value = currentModelLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Cloud Model") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    ExposedDropdownMenu(
                        expanded = modelExpanded,
                        onDismissRequest = { modelExpanded = false }
                    ) {
                        cloudModels.forEach { (id, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    onCloudModelChange(id)
                                    modelExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // Model info — solo per LiteRT
        if (engineType == EngineType.LITERT) {
            if (selectedModelName.isNotBlank() && isModelDownloaded) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Gold.copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            null,
                            tint = Gold,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "Model Ready: $selectedModelName",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            if (selectedModelName.isBlank()) "No model selected. Go to Settings → Manage Models." 
                            else "Model not downloaded. Go to Settings → Manage Models.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // AICore info
        if (engineType == EngineType.AICORE) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF81C784).copy(alpha = 0.1f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.Memory,
                        null,
                        tint = Color(0xFF81C784),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "Using on-device AICore engine",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Language Dropdown
        Box(modifier = Modifier.fillMaxWidth()) {
            ExposedDropdownMenuBox(
                expanded = langExpanded,
                onExpandedChange = { langExpanded = !langExpanded }
            ) {
                OutlinedTextField(
                    value = selectedLanguage,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Transcription Language") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = langExpanded) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                ExposedDropdownMenu(
                    expanded = langExpanded,
                    onDismissRequest = { langExpanded = false }
                ) {
                    languages.forEach { lang ->
                        DropdownMenuItem(
                            text = { Text(lang) },
                            onClick = {
                                onLanguageChange(lang)
                                langExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(14.dp),
            enabled = isStartEnabled
        ) {
            Icon(Icons.Default.GraphicEq, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.btn_start))
        }
    }
}

@Composable
fun LoadingContent(progressMessage: String = "") {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(50.dp))
        Text(stringResource(R.string.title_transcribing), style = MaterialTheme.typography.bodyLarge)
        if (progressMessage.isNotBlank()) {
            Text(
                progressMessage,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun SuccessContent(text: String, onCopy: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
        Button(onClick = { onCopy(text) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.ContentCopy, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.btn_copy))
        }
    }
}

@Composable
fun ErrorContent(msg: String, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.ErrorOutline, 
                    null, 
                    tint = MaterialTheme.colorScheme.error, 
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    "Transcription Error",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    msg, 
                    textAlign = TextAlign.Center, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.SettingsBackupRestore, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Back to Setup / Change Engine")
        }
    }
}
