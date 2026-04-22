package com.example.simpletranscriberapp.ui.screens

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.simpletranscriberapp.data.UserSettings
import com.example.simpletranscriberapp.engine.EngineType
import com.example.simpletranscriberapp.ui.theme.DarkGray
import com.example.simpletranscriberapp.ui.theme.Gold
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: UserSettings,
    isAICoreAvailable: Boolean,
    selectedModelName: String,
    onNavigateBack: () -> Unit,
    onUpdateLanguage: (String) -> Unit,
    onUpdateOpacity: (Float) -> Unit,
    onUpdateTheme: (String) -> Unit,
    onUpdateProximity: (Boolean) -> Unit,
    onUpdateDefaultAction: (String) -> Unit,
    onUpdateTranscriptionEngine: (String) -> Unit,
    onNavigateToModelManager: () -> Unit
) {
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
            // ── Transcription Engine ──
            SettingSection("Transcription Engine") {
                val currentEngine = EngineType.fromKey(settings.transcriptionEngine)

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

                // LiteRT
                EngineOption(
                    icon = Icons.Default.PhoneAndroid,
                    title = "Local Model (LiteRT-LM)",
                    subtitle = if (selectedModelName.isNotBlank()) {
                        "Selected: $selectedModelName"
                    } else {
                        "Full control, works offline"
                    },
                    isSelected = currentEngine == EngineType.LITERT,
                    isEnabled = true,
                    onClick = { onUpdateTranscriptionEngine(EngineType.LITERT.key) }
                )

                // Link al Model Manager (visibile solo se LiteRT è selezionato)
                if (currentEngine == EngineType.LITERT) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = onNavigateToModelManager,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Manage Models")
                        Spacer(Modifier.weight(1f))
                        Icon(
                            Icons.Default.ChevronRight,
                            null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
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
            onClick = if (isEnabled) onClick else null,
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
            Column { // Corretto: rimosso content=content
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
