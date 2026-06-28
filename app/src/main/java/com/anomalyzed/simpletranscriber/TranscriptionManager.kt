package com.anomalyzed.simpletranscriber

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TranscriptionManager {
    private val _uiState = MutableStateFlow<TranscriberUiState>(TranscriberUiState.Setup)
    val uiState: StateFlow<TranscriberUiState> = _uiState.asStateFlow()

    private val _taskStates = MutableStateFlow<Map<Long, TranscriberUiState>>(emptyMap())
    val taskStates: StateFlow<Map<Long, TranscriberUiState>> = _taskStates.asStateFlow()

    private var selectedTaskId: Long? = null

    fun setState(state: TranscriberUiState) {
        _uiState.value = state
    }

    @Synchronized
    fun clearState() {
        selectedTaskId = null
        _uiState.value = TranscriberUiState.Setup
    }

    @Synchronized
    fun setActiveTask(id: Long) {
        selectedTaskId = id
        _uiState.value = _taskStates.value[id] ?: TranscriberUiState.Setup
    }

    fun currentTaskId(): Long? = selectedTaskId

    fun getTaskState(id: Long): TranscriberUiState? = _taskStates.value[id]

    @Synchronized
    fun setTaskState(id: Long, state: TranscriberUiState) {
        _taskStates.value = _taskStates.value.toMutableMap().apply {
            this[id] = state
        }
        if (selectedTaskId == id) {
            _uiState.value = state
        }
    }

    @Synchronized
    fun clearTask(id: Long) {
        _taskStates.value = _taskStates.value.toMutableMap().apply {
            remove(id)
        }
        if (selectedTaskId == id) {
            selectedTaskId = null
            _uiState.value = TranscriberUiState.Setup
        }
    }
}
