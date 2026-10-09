package com.fusionone.app.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fusionone.app.core.database.ScanHistoryEntity
import com.fusionone.app.core.network.MatchDto
import com.fusionone.app.feature.football.repository.FootballRepository
import com.fusionone.app.feature.urlscanner.repository.UrlScanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LiveMatchesState {
    data object Loading : LiveMatchesState
    data class Success(val matches: List<MatchDto>) : LiveMatchesState
    data class Error(val message: String) : LiveMatchesState
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val urlScanRepository: UrlScanRepository,
    private val footballRepository: FootballRepository
) : ViewModel() {

    /** Most recent 5 scans, newest first — used for the "Recent Scans" card. */
    val recentScans: StateFlow<List<ScanHistoryEntity>> = urlScanRepository.observeHistory()
        .map { it.take(5) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * A real security score, not a placeholder: the average reputation score across the
     * last 10 scans. Null (rendered as "no scans yet") until at least one scan exists —
     * there's nothing honest to show before that.
     */
    val securityScore: StateFlow<Int?> = urlScanRepository.observeHistory()
        .map { history ->
            if (history.isEmpty()) null else history.take(10).map { it.reputationScore }.average().toInt()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _liveMatches = MutableStateFlow<LiveMatchesState>(LiveMatchesState.Loading)
    val liveMatches: StateFlow<LiveMatchesState> = _liveMatches.asStateFlow()

    init {
        loadLiveMatches()
    }

    fun loadLiveMatches() {
        _liveMatches.value = LiveMatchesState.Loading
        viewModelScope.launch {
            runCatching { footballRepository.getAllLiveMatches().flatMap { it.second }.take(3) }
                .onSuccess { _liveMatches.value = LiveMatchesState.Success(it) }
                .onFailure { _liveMatches.value = LiveMatchesState.Error("Couldn't load live matches") }
        }
    }
}
