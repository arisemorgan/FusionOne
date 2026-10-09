package com.fusionone.app.feature.urlscanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fusionone.app.feature.urlscanner.model.UrlScanResult
import com.fusionone.app.feature.urlscanner.repository.UrlScanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ScanUiState {
    data object Idle : ScanUiState
    data object Loading : ScanUiState
    data class Success(val result: UrlScanResult) : ScanUiState
    data class Error(val message: String) : ScanUiState
}

@HiltViewModel
class UrlScannerViewModel @Inject constructor(
    private val repository: UrlScanRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    val history = repository.observeHistory()

    fun onUrlInputChanged(value: String) {
        _urlInput.value = value
    }

    fun scanCurrentInput() = scan(_urlInput.value)

    fun scan(url: String) {
        if (url.isBlank()) {
            _uiState.value = ScanUiState.Error("Enter or share a URL to scan")
            return
        }
        _uiState.value = ScanUiState.Loading
        viewModelScope.launch {
            runCatching { repository.scan(url) }
                .onSuccess { _uiState.value = ScanUiState.Success(it) }
                .onFailure { _uiState.value = ScanUiState.Error(it.message ?: "Scan failed — check your connection") }
        }
    }

    fun clearHistory() = viewModelScope.launch { repository.deleteHistory() }
}
