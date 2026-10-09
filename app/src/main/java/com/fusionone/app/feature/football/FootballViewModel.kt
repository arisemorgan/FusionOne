package com.fusionone.app.feature.football

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fusionone.app.core.network.ApiFootballLineup
import com.fusionone.app.core.network.ApiFootballTeamPlayers
import com.fusionone.app.core.network.ApiFootballTeamStatistics
import com.fusionone.app.core.network.ApiFootballTransferGroup
import com.fusionone.app.core.notification.MatchReminderScheduler
import com.fusionone.app.core.network.HeadToHeadResponse
import com.fusionone.app.core.network.MatchDto
import com.fusionone.app.core.network.StandingsResponse
import com.fusionone.app.core.util.toFriendlyMessage
import com.fusionone.app.feature.football.model.TopLeague
import com.fusionone.app.feature.football.repository.ApiFootballRepository
import com.fusionone.app.feature.football.repository.FootballRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class FootballTab { MATCHES, LEAGUES }
enum class MatchDetailTab { OVERVIEW, LINEUPS, STATS, PLAYERS }

sealed interface MatchesUiState {
    data object Loading : MatchesUiState
    data class Success(
        val groupedMatches: List<Pair<String, List<MatchDto>>>,
        val fallbackNote: String? = null // set when we jumped to the next available date
    ) : MatchesUiState
    data class Error(val message: String) : MatchesUiState
}

sealed interface StandingsUiState {
    data object Loading : StandingsUiState
    data class Success(val standings: StandingsResponse?) : StandingsUiState
    data class Error(val message: String) : StandingsUiState
}

/** Generic 3-state holder for the deeper API-Football data — Idle until a tab is opened
 *  (we never prefetch this, to conserve the 100 requests/day budget). */
sealed interface DeepDataState<out T> {
    data object Idle : DeepDataState<Nothing>
    data object Loading : DeepDataState<Nothing>
    data class Success<T>(val data: T?) : DeepDataState<T> // null = resolved but nothing available
    data class Unavailable(val message: String) : DeepDataState<Nothing>
}

@HiltViewModel
class FootballViewModel @Inject constructor(
    private val repository: FootballRepository,
    private val apiFootballRepository: ApiFootballRepository,
    private val reminderScheduler: MatchReminderScheduler
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(FootballTab.MATCHES)
    val selectedTab: StateFlow<FootballTab> = _selectedTab.asStateFlow()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _liveOnly = MutableStateFlow(false)
    val liveOnly: StateFlow<Boolean> = _liveOnly.asStateFlow()

    private val _matchesUiState = MutableStateFlow<MatchesUiState>(MatchesUiState.Loading)
    val matchesUiState: StateFlow<MatchesUiState> = _matchesUiState.asStateFlow()

    private val _selectedLeagueForStandings = MutableStateFlow(TopLeague.PREMIER_LEAGUE)
    val selectedLeagueForStandings: StateFlow<TopLeague> = _selectedLeagueForStandings.asStateFlow()

    private val _standingsUiState = MutableStateFlow<StandingsUiState>(StandingsUiState.Loading)
    val standingsUiState: StateFlow<StandingsUiState> = _standingsUiState.asStateFlow()

    private val _headToHead = MutableStateFlow<HeadToHeadResponse?>(null)
    val headToHead: StateFlow<HeadToHeadResponse?> = _headToHead.asStateFlow()

    private val _reminderSetForMatchId = MutableStateFlow<Set<Long>>(emptySet())
    val reminderSetForMatchId: StateFlow<Set<Long>> = _reminderSetForMatchId.asStateFlow()

    private val _selectedDetailTab = MutableStateFlow(MatchDetailTab.OVERVIEW)
    val selectedDetailTab: StateFlow<MatchDetailTab> = _selectedDetailTab.asStateFlow()

    private val _matchStatistics = MutableStateFlow<DeepDataState<List<ApiFootballTeamStatistics>>>(DeepDataState.Idle)
    val matchStatistics: StateFlow<DeepDataState<List<ApiFootballTeamStatistics>>> = _matchStatistics.asStateFlow()

    private val _matchLineups = MutableStateFlow<DeepDataState<List<ApiFootballLineup>>>(DeepDataState.Idle)
    val matchLineups: StateFlow<DeepDataState<List<ApiFootballLineup>>> = _matchLineups.asStateFlow()

    private val _matchPlayerRatings = MutableStateFlow<DeepDataState<List<ApiFootballTeamPlayers>>>(DeepDataState.Idle)
    val matchPlayerRatings: StateFlow<DeepDataState<List<ApiFootballTeamPlayers>>> = _matchPlayerRatings.asStateFlow()

    private val _teamTransfers = MutableStateFlow<DeepDataState<List<ApiFootballTransferGroup>>>(DeepDataState.Idle)
    val teamTransfers: StateFlow<DeepDataState<List<ApiFootballTransferGroup>>> = _teamTransfers.asStateFlow()

    init {
        loadMatches()
        loadStandings(_selectedLeagueForStandings.value)
    }

    fun selectTab(tab: FootballTab) {
        _selectedTab.value = tab
    }

    fun toggleLiveOnly() {
        _liveOnly.value = !_liveOnly.value
        loadMatches()
    }

    fun goToPreviousDay() {
        _selectedDate.value = _selectedDate.value.minusDays(1)
        if (!_liveOnly.value) loadMatches()
    }

    fun goToNextDay() {
        _selectedDate.value = _selectedDate.value.plusDays(1)
        if (!_liveOnly.value) loadMatches()
    }

    fun goToToday() {
        _selectedDate.value = LocalDate.now()
        if (!_liveOnly.value) loadMatches()
    }

    fun refresh() = loadMatches()

    private fun loadMatches() {
        _matchesUiState.value = MatchesUiState.Loading
        viewModelScope.launch {
            runCatching {
                if (_liveOnly.value) repository.getAllLiveMatches() else repository.getFixturesForDate(_selectedDate.value)
            }.onSuccess { grouped ->
                if (grouped.isEmpty() && !_liveOnly.value) {
                    // Nothing scheduled for this date (very possibly the close season) —
                    // look ahead for the next date that actually has fixtures instead of
                    // just showing a dead end.
                    val fallback = runCatching { repository.getNextAvailableFixtures() }.getOrNull()
                    if (fallback != null && fallback.second.isNotEmpty()) {
                        _selectedDate.value = fallback.first
                        _matchesUiState.value = MatchesUiState.Success(
                            fallback.second,
                            fallbackNote = "No matches on the date you were viewing — showing the next scheduled fixtures instead"
                        )
                    } else {
                        _matchesUiState.value = MatchesUiState.Success(grouped)
                    }
                } else {
                    _matchesUiState.value = MatchesUiState.Success(grouped)
                }
            }.onFailure {
                _matchesUiState.value = MatchesUiState.Error(it.toFriendlyMessage("football-data.org"))
            }
        }
    }

    fun selectLeagueForStandings(league: TopLeague) {
        _selectedLeagueForStandings.value = league
        loadStandings(league)
    }

    private fun loadStandings(league: TopLeague) {
        _standingsUiState.value = StandingsUiState.Loading
        viewModelScope.launch {
            runCatching { repository.getStandings(league) }
                .onSuccess { _standingsUiState.value = StandingsUiState.Success(it) }
                .onFailure { _standingsUiState.value = StandingsUiState.Error(it.toFriendlyMessage("football-data.org")) }
        }
    }

    fun loadHeadToHead(matchId: Long) {
        viewModelScope.launch {
            _headToHead.value = repository.getHeadToHead(matchId)
        }
    }

    fun clearHeadToHead() {
        _headToHead.value = null
    }

    fun toggleReminder(match: MatchDto) {
        viewModelScope.launch {
            val currentlySet = reminderScheduler.isScheduled(match.id)
            if (currentlySet) {
                reminderScheduler.cancel(match.id)
                _reminderSetForMatchId.value = _reminderSetForMatchId.value - match.id
            } else {
                reminderScheduler.schedule(
                    matchId = match.id,
                    homeTeam = match.homeTeam.shortName ?: match.homeTeam.name,
                    awayTeam = match.awayTeam.shortName ?: match.awayTeam.name,
                    kickoffUtc = match.utcDate
                )
                _reminderSetForMatchId.value = _reminderSetForMatchId.value + match.id
            }
        }
    }

    fun isReminderSet(matchId: Long): Boolean = matchId in _reminderSetForMatchId.value

    // ---- Match detail sheet: deep API-Football data, lazy-loaded per tab ----

    fun selectDetailTab(tab: MatchDetailTab) {
        _selectedDetailTab.value = tab
    }

    /** Call once when the match-detail sheet is opened; resets all deep-data tabs back to
     *  Idle so they lazy-load only when the user actually taps into that tab. */
    fun resetMatchDetailState() {
        _selectedDetailTab.value = MatchDetailTab.OVERVIEW
        _matchStatistics.value = DeepDataState.Idle
        _matchLineups.value = DeepDataState.Idle
        _matchPlayerRatings.value = DeepDataState.Idle
        _teamTransfers.value = DeepDataState.Idle
    }

    fun loadMatchStatistics(match: MatchDto, competitionName: String) {
        val league = TopLeague.entries.firstOrNull { it.displayName == competitionName } ?: run {
            _matchStatistics.value = DeepDataState.Unavailable("Unknown competition")
            return
        }
        _matchStatistics.value = DeepDataState.Loading
        viewModelScope.launch {
            runCatching { apiFootballRepository.getMatchStatistics(match, league) }
                .onSuccess { _matchStatistics.value = DeepDataState.Success(it) }
                .onFailure { _matchStatistics.value = DeepDataState.Unavailable(it.toFriendlyMessage("API-Football")) }
        }
    }

    fun loadLineups(match: MatchDto, competitionName: String) {
        val league = TopLeague.entries.firstOrNull { it.displayName == competitionName } ?: run {
            _matchLineups.value = DeepDataState.Unavailable("Unknown competition")
            return
        }
        _matchLineups.value = DeepDataState.Loading
        viewModelScope.launch {
            runCatching { apiFootballRepository.getLineups(match, league) }
                .onSuccess { _matchLineups.value = DeepDataState.Success(it) }
                .onFailure { _matchLineups.value = DeepDataState.Unavailable(it.toFriendlyMessage("API-Football")) }
        }
    }

    fun loadPlayerRatings(match: MatchDto, competitionName: String) {
        val league = TopLeague.entries.firstOrNull { it.displayName == competitionName } ?: run {
            _matchPlayerRatings.value = DeepDataState.Unavailable("Unknown competition")
            return
        }
        _matchPlayerRatings.value = DeepDataState.Loading
        viewModelScope.launch {
            runCatching { apiFootballRepository.getPlayerRatings(match, league) }
                .onSuccess { _matchPlayerRatings.value = DeepDataState.Success(it) }
                .onFailure { _matchPlayerRatings.value = DeepDataState.Unavailable(it.toFriendlyMessage("API-Football")) }
        }
    }

    fun loadTeamTransfers(teamName: String) {
        _teamTransfers.value = DeepDataState.Loading
        viewModelScope.launch {
            runCatching { apiFootballRepository.getTeamTransfers(teamName) }
                .onSuccess { _teamTransfers.value = DeepDataState.Success(it) }
                .onFailure { _teamTransfers.value = DeepDataState.Unavailable(it.toFriendlyMessage("API-Football")) }
        }
    }

    fun clearTeamTransfers() {
        _teamTransfers.value = DeepDataState.Idle
    }
}
