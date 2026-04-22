package com.example.simpletranscriberapp.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import com.example.simpletranscriberapp.R
import com.example.simpletranscriberapp.TranscriberUiState
import com.example.simpletranscriberapp.ui.theme.SimpleTranscriberAppTheme

@Composable
fun TranscriberScreen(
    uiState: TranscriberUiState,
    onDismiss: () -> Unit,
    onCopyToClipboard: (String) -> Unit,
    onStartTranscription: () -> Unit,
    onUpdateApiKey: (String) -> Unit,
    onUpdateLanguage: (String) -> Unit,
    currentApiKey: String,
    currentLanguage: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
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
                    Text("SimpleTranscriber", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant)

                AnimatedContent(
                    targetState = uiState,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "stateAnim"
                ) { state ->
                    when (state) {
                        is TranscriberUiState.Setup -> SetupContent(
                            apiKey = currentApiKey,
                            selectedLanguage = currentLanguage,
                            onApiKeyChange = onUpdateApiKey,
                            onLanguageChange = onUpdateLanguage,
                            onStart = onStartTranscription
                        )
                        is TranscriberUiState.Loading -> LoadingContent()
                        is TranscriberUiState.Success -> SuccessContent(state.text, onCopyToClipboard)
                        is TranscriberUiState.Error -> ErrorContent(state.message)
                    }
                }

                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.btn_close))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupContent(
    apiKey: String,
    selectedLanguage: String,
    onApiKeyChange: (String) -> Unit,
    onLanguageChange: (String) -> Unit,
    onStart: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var isApiKeyVisible by remember { mutableStateOf(false) }
    // Una lista espandibile con le principali lingue. Per "tutte" usiamo un subset comune.
    val languages = listOf("Italian", "English", "Spanish", "French", "German", "Portuguese", "Russian", "Chinese", "Japanese", "Arabic")

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // API Key Input
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

        // Language Dropdown
        Box(modifier = Modifier.fillMaxWidth()) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = selectedLanguage,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Transcription Language") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    languages.forEach { lang ->
                        DropdownMenuItem(
                            text = { Text(lang) },
                            onClick = {
                                onLanguageChange(lang)
                                expanded = false
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
            enabled = apiKey.isNotBlank()
        ) {
            Icon(Icons.Default.GraphicEq, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.btn_start))
        }
    }
}

@Composable
fun LoadingContent() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(50.dp))
        Text(stringResource(R.string.title_transcribing), style = MaterialTheme.typography.bodyLarge)
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
fun ErrorContent(msg: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(40.dp))
        Text(msg, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
    }
}
