package com.anomalyzed.simpletranscriber.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anomalyzed.simpletranscriber.data.UserSettings
import com.anomalyzed.simpletranscriber.engine.EngineType
import com.anomalyzed.simpletranscriber.ui.theme.DarkGray
import com.anomalyzed.simpletranscriber.ui.theme.Gold
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import com.anomalyzed.simpletranscriber.R
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: UserSettings,
    isAICoreAvailable: Boolean,
    selectedModelName: String,
    onNavigateBack: () -> Unit,
    onUpdateLanguage: (String) -> Unit,
    onUpdateTranscriptionEngine: (String) -> Unit,
    onUpdateApiKey: (String) -> Unit,
    onUpdateSelectedCloudModel: (String) -> Unit,
    onNavigateToModelManager: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onViewChangelog: () -> Unit,
    googleModels: List<Pair<String, String>> = emptyList(),
    googleModelsLoading: Boolean = false,
    googleModelsError: String? = null,
    onRetryGoogleModels: () -> Unit = {}
) {
    var showLanguageDialog by remember { mutableStateOf(false) }
    var selectedFamily by remember(settings.selectedCloudModel) {
        mutableStateOf(if (settings.selectedCloudModel.lowercase().contains("gemma")) "Gemma" else "Gemini")
    }
    val currentLanguageCode = remember { 
        AppCompatDelegate.getApplicationLocales().get(0)?.language ?: "" 
    }
    
    val currentLanguageLabel = when (currentLanguageCode) {
        "en" -> stringResource(R.string.settings_language_english)
        "it" -> stringResource(R.string.settings_language_italian)
        else -> stringResource(R.string.settings_language_system)
    }

    var isApiKeyVisible by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, null) }
                }
            )
        }
    ) { p ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(p)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            val currentEngine = EngineType.fromKey(settings.transcriptionEngine)

            // ── Transcription Engine ──
            SettingSection(stringResource(R.string.settings_engine_section)) {
                // Cloud
                EngineOption(
                    icon = Icons.Default.Cloud,
                    title = stringResource(R.string.settings_engine_cloud),
                    subtitle = stringResource(R.string.settings_engine_cloud_sub),
                    isSelected = currentEngine == EngineType.CLOUD,
                    isEnabled = true,
                    onClick = { onUpdateTranscriptionEngine(EngineType.CLOUD.key) }
                )

                HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)

                // AICore
                EngineOption(
                    icon = Icons.Default.Memory,
                    title = stringResource(R.string.settings_engine_aicore),
                    subtitle = if (isAICoreAvailable) {
                        stringResource(R.string.settings_engine_aicore_sub_ready)
                    } else {
                        stringResource(R.string.settings_engine_aicore_sub_missing)
                    },
                    isSelected = currentEngine == EngineType.AICORE,
                    isEnabled = isAICoreAvailable,
                    onClick = { onUpdateTranscriptionEngine(EngineType.AICORE.key) }
                )

                HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)

                // Local models
                EngineOption(
                    icon = Icons.Default.PhoneAndroid,
                    title = stringResource(R.string.settings_engine_local),
                    subtitle = if (selectedModelName.isNotBlank()) {
                        stringResource(R.string.settings_engine_local_sub_prefix, selectedModelName)
                    } else {
                        stringResource(R.string.settings_engine_local_sub_default)
                    },
                    isSelected = currentEngine == EngineType.LITERT,
                    isEnabled = true,
                    onClick = { onUpdateTranscriptionEngine(EngineType.LITERT.key) }
                )
            }

            // ── Dynamic Settings based on Engine ──
            if (currentEngine == EngineType.CLOUD) {
                // ── Cloud Settings (Gemini) ──
                SettingSection(stringResource(R.string.settings_cloud_section)) {
                    // API Key
                    OutlinedTextField(
                        value = settings.apiKey,
                        onValueChange = onUpdateApiKey,
                        label = { Text(stringResource(R.string.label_api_key)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                Icon(
                                    if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    null
                                )
                            }
                        }
                    )

                    Spacer(Modifier.height(8.dp))

                    if (settings.apiKey.isBlank()) {
                        Text(
                            text = stringResource(R.string.google_models_key_required),
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    } else if (googleModelsLoading) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = Gold, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.google_models_loading),
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    } else if (googleModelsError != null) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.google_models_error),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = onRetryGoogleModels,
                                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                            ) {
                                Text(stringResource(R.string.btn_retry), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else if (googleModels.isEmpty()) {
                        Text(
                            text = stringResource(R.string.google_models_empty),
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    } else {
                        // Model Family Selection
                        Text(stringResource(R.string.settings_cloud_model_family), fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(start = 4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { 
                                    selectedFamily = "Gemini"
                                    val geminiModels = googleModels.filter { it.first.lowercase().contains("gemini") }
                                    if (geminiModels.isNotEmpty() && geminiModels.none { it.first == settings.selectedCloudModel }) {
                                        val defaultModelName = geminiModels.find { it.first.contains("2.5-flash") }?.first
                                            ?: geminiModels.find { it.first.contains("1.5-flash") }?.first
                                            ?: geminiModels.first().first
                                        onUpdateSelectedCloudModel(defaultModelName)
                                    }
                                }
                            ) {
                                RadioButton(
                                    selected = selectedFamily == "Gemini",
                                    onClick = { 
                                        selectedFamily = "Gemini"
                                        val geminiModels = googleModels.filter { it.first.lowercase().contains("gemini") }
                                        if (geminiModels.isNotEmpty() && geminiModels.none { it.first == settings.selectedCloudModel }) {
                                            val defaultModelName = geminiModels.find { it.first.contains("2.5-flash") }?.first
                                                ?: geminiModels.find { it.first.contains("1.5-flash") }?.first
                                                ?: geminiModels.first().first
                                            onUpdateSelectedCloudModel(defaultModelName)
                                        }
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = Gold)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Gemini", fontSize = 14.sp)
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { 
                                    selectedFamily = "Gemma"
                                    val gemmaModels = googleModels.filter { it.first.lowercase().contains("gemma") }
                                    if (gemmaModels.isNotEmpty() && gemmaModels.none { it.first == settings.selectedCloudModel }) {
                                        onUpdateSelectedCloudModel(gemmaModels.first().first)
                                    }
                                }
                            ) {
                                RadioButton(
                                    selected = selectedFamily == "Gemma",
                                    onClick = { 
                                        selectedFamily = "Gemma"
                                        val gemmaModels = googleModels.filter { it.first.lowercase().contains("gemma") }
                                        if (gemmaModels.isNotEmpty() && gemmaModels.none { it.first == settings.selectedCloudModel }) {
                                            onUpdateSelectedCloudModel(gemmaModels.first().first)
                                        }
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = Gold)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Gemma", fontSize = 14.sp)
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // Filtered Model Selection
                        Text(stringResource(R.string.settings_cloud_model), fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(start = 4.dp))
                        val filteredModels = googleModels.filter { (id, _) ->
                            if (selectedFamily == "Gemini") id.lowercase().contains("gemini")
                            else id.lowercase().contains("gemma")
                        }
                        if (filteredModels.isEmpty()) {
                            Text(
                                text = stringResource(R.string.google_models_empty),
                                fontSize = 12.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        } else {
                            filteredModels.forEach { (id, label) ->
                                SettingRadio(
                                    title = label,
                                    selected = settings.selectedCloudModel == id,
                                    onClick = { onUpdateSelectedCloudModel(id) }
                                )
                            }
                        }
                    }
                }
            } else if (currentEngine == EngineType.LITERT) {
                // ── Local Model Settings ──
                SettingSection(stringResource(R.string.settings_local_section)) {
                    SettingItem(
                        icon = Icons.Default.Settings,
                        title = stringResource(R.string.settings_local_manage),
                        subtitle = stringResource(R.string.settings_local_manage_sub),
                        onClick = onNavigateToModelManager
                    )
                }
            }

            // ── Transcription ──
            SettingSection(stringResource(R.string.settings_transcriber_section)) {
                // Default Language (Target language)
                Text(stringResource(R.string.settings_default_language), fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(start = 4.dp))
                Spacer(Modifier.height(4.dp))
                var expanded by remember { mutableStateOf(false) }
                val languages = listOf("Italian", "English", "Spanish", "French", "German")

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedCard(
                        onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(settings.language)
                            Icon(Icons.Default.ArrowDropDown, null)
                        }
                    }

                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        languages.forEach { lang ->
                            DropdownMenuItem(
                                text = { Text(lang) },
                                onClick = {
                                    onUpdateLanguage(lang)
                                    expanded = false
                                }
                            )
                        }
                    }
                }

            }

            // ── App Language ──
            SettingSection(stringResource(R.string.settings_section_language)) {
                SettingItem(
                    icon = Icons.Default.Language,
                    title = stringResource(R.string.settings_section_language),
                    subtitle = currentLanguageLabel,
                    onClick = { showLanguageDialog = true }
                )
            }

            // ── Updates ──
            SettingSection(stringResource(R.string.settings_updates_section)) {
                SettingItem(
                    icon = Icons.Default.SystemUpdate,
                    title = stringResource(R.string.settings_updates_check),
                    subtitle = stringResource(R.string.settings_updates_check_sub),
                    onClick = onCheckForUpdates
                )
                HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)
                SettingItem(
                    icon = Icons.Default.Article,
                    title = stringResource(R.string.settings_updates_changelog),
                    subtitle = stringResource(R.string.settings_updates_changelog_sub),
                    onClick = onViewChangelog
                )
            }
        }
    }

    if (showLanguageDialog) {
        val languages = listOf(
            "" to stringResource(R.string.settings_language_system),
            "en" to stringResource(R.string.settings_language_english),
            "it" to stringResource(R.string.settings_language_italian)
        )

        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.settings_section_language)) },
            text = {
                Column {
                    languages.forEach { (code, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val localeList = if (code.isEmpty()) {
                                        LocaleListCompat.getEmptyLocaleList()
                                    } else {
                                        LocaleListCompat.forLanguageTags(code)
                                    }
                                    AppCompatDelegate.setApplicationLocales(localeList)
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentLanguageCode == code,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = Gold)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.btn_cancel), color = Gold)
                }
            }
        )
    }
}

@Composable
private fun EngineOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isSelected: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isEnabled) Modifier.noRippleClickable { onClick() }
                else Modifier
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon, null,
            modifier = Modifier.size(24.dp),
            tint = when {
                isSelected -> Gold
                !isEnabled -> Color.Gray.copy(alpha = 0.5f)
                else -> Color.LightGray
            }
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (isEnabled) Color.Unspecified else Color.Gray.copy(alpha = 0.5f)
            )
            Text(
                subtitle,
                fontSize = 12.sp,
                color = if (isEnabled) Color.Gray else Color.Gray.copy(alpha = 0.4f)
            )
        }
        RadioButton(
            selected = isSelected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = Gold,
                unselectedColor = if (isEnabled) Color.Gray else Color.Gray.copy(alpha = 0.3f)
            ),
            enabled = isEnabled
        )
    }
}

@Composable
fun SettingSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        SettingsCard {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
fun SettingsCard(content: @Composable BoxScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkGray)
    ) {
        Box(modifier = Modifier.padding(16.dp)) { content() }
    }
}

@Composable
fun SettingItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().noRippleClickable { onClick() }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, modifier = Modifier.size(24.dp), tint = Color.LightGray)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 12.sp, color = Color.Gray)
        }
    }
}

@Composable
fun SettingToggle(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, fontSize = 14.sp)
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = Gold)
        )
    }
}

@Composable
fun SettingRadio(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().noRippleClickable { onClick() }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, fontSize = 14.sp)
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = Gold)
        )
    }
}

@Composable
fun SettingSlider(icon: ImageVector, title: String, value: Float, onValueChange: (Float) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(20.dp), tint = Color.Gray)
            Spacer(Modifier.width(12.dp))
            Text(title, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            Text("${(value * 100).roundToInt()}%", fontSize = 12.sp, color = Color.Gray)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Gold)
        )
    }
}

@Composable
fun AppIconPlaceholder(color: Color) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color)
    )
}

/**
 * Estensione per cliccare senza l'effetto ripple (onda semitrasparente).
 */
private fun Modifier.noRippleClickable(onClick: () -> Unit) = composed {
    this.clickable(
        indication = null,
        interactionSource = remember { MutableInteractionSource() }
    ) { onClick() }
}
