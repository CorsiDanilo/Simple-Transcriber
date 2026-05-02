package com.anomalyzed.simpletranscriber

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TranscriptionManager {
    private val _uiState = MutableStateFlow<TranscriberUiState>(TranscriberUiState.Setup)
    val uiState: StateFlow<TranscriberUiState> = _uiState.asStateFlow()

    fun setState(state: TranscriberUiState) {
        _uiState.value = state
    }

    fun clearState() {
        _uiState.value = TranscriberUiState.Setup
    }
}
