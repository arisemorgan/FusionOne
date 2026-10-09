package com.fusionone.app.feature.photoanalysis

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fusionone.app.core.util.ExifLocationExtractor
import com.fusionone.app.core.util.PayloadAnalysis
import com.fusionone.app.core.util.PayloadDetector
import com.fusionone.app.core.util.PhotoLocation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PhotoAnalysisResult(val location: PhotoLocation, val payload: PayloadAnalysis)

sealed interface PhotoUiState {
    data object Idle : PhotoUiState
    data object Loading : PhotoUiState
    data class Success(val result: PhotoAnalysisResult) : PhotoUiState
    data class Error(val message: String) : PhotoUiState
}

@HiltViewModel
class PhotoAnalysisViewModel @Inject constructor(
    private val exifLocationExtractor: ExifLocationExtractor,
    private val payloadDetector: PayloadDetector
) : ViewModel() {

    private val _uiState = MutableStateFlow<PhotoUiState>(PhotoUiState.Idle)
    val uiState: StateFlow<PhotoUiState> = _uiState.asStateFlow()

    fun analyze(uri: Uri) {
        _uiState.value = PhotoUiState.Loading
        viewModelScope.launch {
            runCatching {
                coroutineScope {
                    val locationDeferred = async { exifLocationExtractor.extract(uri) }
                    val payloadDeferred = async { payloadDetector.analyze(uri) }
                    PhotoAnalysisResult(locationDeferred.await(), payloadDeferred.await())
                }
            }.onSuccess {
                _uiState.value = PhotoUiState.Success(it)
            }.onFailure {
                _uiState.value = PhotoUiState.Error(it.message ?: "Could not analyze this image")
            }
        }
    }

    fun reset() {
        _uiState.value = PhotoUiState.Idle
    }
}
