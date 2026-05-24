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
    onViewChangelog: () -> Unit
) {
    var isApiKeyVisible by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
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
            SettingSection("Transcriber Engine") {
                // Cloud
                EngineOption(
                    icon = Icons.Default.Cloud,
                    title = "Cloud (Gemini API)",
                    subtitle = "Requires API Key & Internet",
                    isSelected = currentEngine == EngineType.CLOUD,
                    isEnabled = true,
                    onClick = { onUpdateTranscriptionEngine(EngineType.CLOUD.key) }
                )

                HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)

                // AICore
                EngineOption(
                    icon = Icons.Default.Memory,
                    title = "AICore (On-Device)",
                    subtitle = if (isAICoreAvailable) {
                        "System-managed, no setup needed"
                    } else {
                        "⚠️ Not available on this device"
                    },
                    isSelected = currentEngine == EngineType.AICORE,
                    isEnabled = isAICoreAvailable,
                    onClick = { onUpdateTranscriptionEngine(EngineType.AICORE.key) }
                )

                HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)

                // Local models
                EngineOption(
                    icon = Icons.Default.PhoneAndroid,
                    title = "Local Model",
                    subtitle = if (selectedModelName.isNotBlank()) {
                        "Selected: $selectedModelName"
                    } else {
                        "Gemma and Whisper downloads"
                    },
                    isSelected = currentEngine == EngineType.LITERT,
                    isEnabled = true,
                    onClick = { onUpdateTranscriptionEngine(EngineType.LITERT.key) }
                )
            }

            // ── Dynamic Settings based on Engine ──
            if (currentEngine == EngineType.CLOUD) {
                // ── Cloud Settings (Gemini) ──
                SettingSection("Cloud Settings (Gemini)") {
                    // API Key
                    OutlinedTextField(
                        value = settings.apiKey,
                        onValueChange = onUpdateApiKey,
                        label = { Text("Gemini API Key") },
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

                    // Model Selection
                    Text("Cloud Model", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(start = 4.dp))
                    
                    val cloudModels = listOf(
                        "gemini-flash-latest" to "Gemini Flash (Latest)",
                        "gemini-flash-lite-latest" to "Gemini Flash-Lite (Latest)"
                    )

                    cloudModels.forEach { (id, label) ->
                        SettingRadio(
                            title = label,
                            selected = settings.selectedCloudModel == id,
                            onClick = { onUpdateSelectedCloudModel(id) }
                        )
                    }
                }
            } else if (currentEngine == EngineType.LITERT) {
                // ── Local Model Settings ──
                SettingSection("Local Model Settings") {
                    SettingItem(
                        icon = Icons.Default.Settings,
                        title = "Manage Models",
                        subtitle = "Download or delete local AI models",
                        onClick = onNavigateToModelManager
                    )
                }
            }

            // ── Transcription ──
            SettingSection("Transcriber Settings") {
                Text("Default Language", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(start = 4.dp))
                
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

            // ── Updates ──
            SettingSection("App Updates") {
                SettingItem(
                    icon = Icons.Default.SystemUpdate,
                    title = "Check for Updates",
                    subtitle = "Manually check if a new version is available",
                    onClick = onCheckForUpdates
                )
                HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)
                SettingItem(
                    icon = Icons.Default.Article,
                    title = "View Changelog",
                    subtitle = "See what's new in recent releases",
                    onClick = onViewChangelog
                )
            }
        }
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
            Column(modifier = Modifier.fillMaxWidth()) { // Corretto: rimosso content=content
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
