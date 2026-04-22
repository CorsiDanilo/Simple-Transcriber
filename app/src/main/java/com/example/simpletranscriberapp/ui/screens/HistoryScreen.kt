package com.example.simpletranscriberapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.simpletranscriberapp.data.TranscriptionItem
import com.example.simpletranscriberapp.ui.theme.DarkGray
import com.example.simpletranscriberapp.ui.theme.Gold
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    items: List<TranscriptionItem>,
    onClearHistory: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {}) { Icon(Icons.Default.Search, null) }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, null)
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Clear history") },
                                onClick = {
                                    showMenu = false
                                    onClearHistory()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                onClick = {
                                    showMenu = false
                                    onNavigateToSettings()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Tell a friend") },
                                onClick = { showMenu = false }
                            )
                        }
                    }
                }
            )
        }
    ) { p ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(p),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(items) { item ->
                HistoryItemCard(item)
            }
        }
    }
}

@Composable
fun HistoryItemCard(item: TranscriptionItem) {
    val dateStr = remember(item.timestamp) {
        val sdf = SimpleDateFormat("dd MMM • hh:mm a", Locale.ENGLISH)
        sdf.format(Date(item.timestamp))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkGray)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = dateStr,
                color = Gold,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 20.sp
            )
        }
    }
}
