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
import com.example.simpletranscriberapp.ui.theme.DarkGray
import com.example.simpletranscriberapp.ui.theme.Gold
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: UserSettings,
    onNavigateBack: () -> Unit,
    onUpdateLanguage: (String) -> Unit,
    onUpdateOpacity: (Float) -> Unit,
    onUpdateTheme: (String) -> Unit,
    onUpdateProximity: (Boolean) -> Unit,
    onUpdateDefaultAction: (String) -> Unit
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
            // Sezione Premium
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WorkspacePremium, null, tint = Gold)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("You are a premium user", fontWeight = FontWeight.Bold)
                        Text("Thank you for using Transcriber for WhatsApp!", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }

            // Social
            SettingSection("Social") {
                SettingItem(Icons.Default.CenterFocusStrong, "Follow me!", "Follow me on Instagram to stay updated about upcoming projects, collaborations, app updates and more!")
            }

            // Language
            SettingSection("Language") {
                SettingItem(Icons.Default.Language, "Language in use is", settings.language, onClick = { onUpdateLanguage("Italiano") })
                SettingToggle("Choose language before every conversation", true, {})
            }

            // UI
            SettingSection("UI") {
                SettingSlider(
                    icon = Icons.Default.Opacity,
                    title = "Opacity",
                    value = settings.opacity,
                    onValueChange = onUpdateOpacity
                )
                Divider(color = Color.DarkGray, thickness = 0.5.dp)
                SettingItem(Icons.Default.Palette, "Theme", settings.theme, onClick = { onUpdateTheme("System") })
            }

            // Voice Playback
            SettingSection("Voice message playback") {
                SettingItem(Icons.Default.FastForward, "Min speed", "0.75X")
                SettingItem(Icons.Default.FastForward, "Max speed", "1.75X")
                Divider(color = Color.DarkGray, thickness = 0.5.dp)
                SettingToggle("Enable proximity sensor", settings.enableProximity, onUpdateProximity)
            }

            // Actions
            SettingSection("Actions") {
                val actions = listOf("Show actions", "Transcribe", "Play voice message in incognito")
                actions.forEach { action ->
                    SettingRadio(action, selected = settings.defaultAction == action) {
                        onUpdateDefaultAction(action)
                    }
                }
            }

            // Apps you might like
            SettingSection("Apps you might like") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
                ) {
                    AppIconPlaceholder(Color(0xFFE3F2FD))
                    AppIconPlaceholder(Color(0xFFFCE4EC))
                    AppIconPlaceholder(Color(0xFFFFF3E0))
                    AppIconPlaceholder(Color(0xFFE8F5E9))
                }
            }

            // Footer
            Text(
                text = "Made in Italy with ♥",
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                textAlign = TextAlign.Center,
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
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
