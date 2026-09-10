package com.anomalyzed.simpletranscriber.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anomalyzed.simpletranscriber.data.TranscriptionItem
import com.anomalyzed.simpletranscriber.ui.theme.DarkGray
import com.anomalyzed.simpletranscriber.ui.theme.Gold
import androidx.compose.ui.res.stringResource
import com.anomalyzed.simpletranscriber.R
import com.anomalyzed.simpletranscriber.ui.utils.parseMarkdown
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    items: List<TranscriptionItem>,
    onClearHistory: () -> Unit,
    onDeleteItem: (Int) -> Unit,
    onDeleteItems: (Set<Int>) -> Unit,
    onCopyToClipboard: (String) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    // Selection mode state
    var selectedIds by remember { mutableStateOf(setOf<Int>()) }
    val isSelectionMode = selectedIds.isNotEmpty()

    // Map to track whether summary or transcription is selected per item
    val summaryViewMap = remember { mutableStateMapOf<Int, Boolean>() }

    fun getDisplayTextForItem(item: TranscriptionItem): String {
        val isShowingSummary = item.isSummaryOnly || (item.summary != null && summaryViewMap[item.id] == true)
        return if (isShowingSummary && item.summary != null) item.summary else item.text
    }

    // Dialog state
    var itemToDelete by remember { mutableStateOf<TranscriptionItem?>(null) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }

    val filteredItems = remember(items, searchQuery) {
        if (searchQuery.isBlank()) items
        else items.filter {
            it.text.contains(searchQuery, ignoreCase = true) ||
            (it.summary != null && it.summary.contains(searchQuery, ignoreCase = true))
        }
    }

    // Single-item delete confirmation dialog
    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text(stringResource(R.string.delete_transcription_title)) },
            text = { Text(stringResource(R.string.delete_transcription_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteItem(item.id)
                        itemToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.btn_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    // Bulk delete confirmation dialog
    if (showBulkDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_transcriptions_title)) },
            text = { Text(stringResource(R.string.delete_transcriptions_message, selectedIds.size)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteItems(selectedIds)
                        selectedIds = emptySet()
                        showBulkDeleteConfirm = false
                    }
                ) {
                    Text(stringResource(R.string.btn_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteConfirm = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                // Selection mode top bar
                TopAppBar(
                    title = { Text(stringResource(R.string.selected_count, selectedIds.size), fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { selectedIds = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.content_desc_clear_selection))
                        }
                    },
                    actions = {
                        // Select All
                        IconButton(onClick = {
                            selectedIds = filteredItems.map { it.id }.toSet()
                        }) {
                            Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.content_desc_select_all))
                        }
                        // Copy selected to clipboard
                        IconButton(onClick = {
                            val combined = items
                                .filter { it.id in selectedIds }
                                .joinToString("\n\n") { getDisplayTextForItem(it) }
                            onCopyToClipboard(combined)
                            selectedIds = emptySet()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.content_desc_copy_selected))
                        }
                        // Delete selected
                        IconButton(onClick = { showBulkDeleteConfirm = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.content_desc_delete_selected),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                )
            } else {
                // Normal top bar
                TopAppBar(
                    title = { Text(stringResource(R.string.history_title), fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = { isSearchActive = !isSearchActive }) {
                            Icon(Icons.Default.Search, null)
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, null)
                        }
                    }
                )
            }
        }
    ) { p ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(p)
        ) {
            if (isSearchActive && !isSelectionMode) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text(stringResource(R.string.search_placeholder)) },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(filteredItems, key = { it.id }) { item ->
                    HistoryItemCard(
                        item = item,
                        isSelected = item.id in selectedIds,
                        isSelectionMode = isSelectionMode,
                        showSummary = summaryViewMap[item.id] ?: false,
                        onToggleShowSummary = { summaryViewMap[item.id] = it },
                        onCopyToClipboard = { textToCopy -> onCopyToClipboard(textToCopy) },
                        onDeleteRequest = { itemToDelete = item },
                        onClick = {
                            if (isSelectionMode) {
                                selectedIds = if (item.id in selectedIds) {
                                    selectedIds - item.id
                                } else {
                                    selectedIds + item.id
                                }
                            }
                        },
                        onLongClick = {
                            if (!isSelectionMode) {
                                selectedIds = setOf(item.id)
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HistoryItemCard(
    item: TranscriptionItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    showSummary: Boolean,
    onToggleShowSummary: (Boolean) -> Unit,
    onCopyToClipboard: (String) -> Unit,
    onDeleteRequest: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val isSummaryActive = (showSummary || item.isSummaryOnly) && item.summary != null
    val displayText = if (isSummaryActive) item.summary!! else item.text

    val dateStr = remember(item.timestamp) {
        val sdf = SimpleDateFormat("dd MMM • hh:mm a", Locale.getDefault())
        sdf.format(Date(item.timestamp))
    }

    val currentDisplayText by rememberUpdatedState(displayText)
    val currentOnCopy by rememberUpdatedState(onCopyToClipboard)
    val currentOnDelete by rememberUpdatedState(onDeleteRequest)

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    // Swipe sx→dx: copia nella clipboard il testo attualmente visualizzato
                    currentOnCopy(currentDisplayText)
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    // Swipe dx→sx: chiedi conferma eliminazione
                    currentOnDelete()
                }
                else -> Unit
            }
            // Sempre false: lo snap-back avviene sempre, la conferma è nei dialog
            false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = !isSelectionMode,
        enableDismissFromEndToStart = !isSelectionMode,
        backgroundContent = {
            val direction = dismissState.dismissDirection

            val color by animateColorAsState(
                targetValue = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Color(0xFF1B8E4E)  // verde
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                    else -> Color.Transparent
                },
                label = "swipe_bg_color"
            )

            val scale by animateFloatAsState(
                targetValue = if (direction == SwipeToDismissBoxValue.Settled) 0.75f else 1.1f,
                animationSpec = tween(150),
                label = "swipe_icon_scale"
            )

            val alignment = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                else -> Alignment.CenterEnd
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment
            ) {
                when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = Color.White,
                        modifier = Modifier.scale(scale)
                    )
                    SwipeToDismissBoxValue.EndToStart -> Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.scale(scale)
                    )
                    else -> {}
                }
            }
        }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isSelected)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    DarkGray
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dateStr,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Gold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (item.isSummaryOnly) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.badge_summary_only),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    } else if (item.summary != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.badge_summary_available),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                if (item.engineMode != null && item.modelName != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.mode_model_info, item.engineMode, item.modelName),
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }

                if (item.summary != null && !item.isSummaryOnly) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = !showSummary,
                            onClick = { onToggleShowSummary(false) },
                            label = { Text(stringResource(R.string.tab_transcription), fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = showSummary,
                            onClick = { onToggleShowSummary(true) },
                            label = { Text(stringResource(R.string.tab_summary), fontSize = 11.sp) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = parseMarkdown(displayText),
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp
                )
            }
        }
    }
}
