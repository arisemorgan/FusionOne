package com.fusionone.app.feature.football

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.fusionone.app.core.network.MatchDto
import com.fusionone.app.feature.football.model.TopLeague
import com.fusionone.app.ui.theme.AccentGold
import com.fusionone.app.ui.theme.DangerRed
import com.fusionone.app.ui.theme.PrimaryTeal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FootballScreen(
    onNavigateHome: () -> Unit = {},
    viewModel: FootballViewModel = hiltViewModel()
) {
    val selectedTab by viewModel.selectedTab.collectAsState()
    var selectedMatch by remember { mutableStateOf<MatchDto?>(null) }
    var selectedCompetition by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Football", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateHome) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Dashboard")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search teams") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )

            TabRow(selectedTabIndex = selectedTab.ordinal) {
                Tab(
                    selected = selectedTab == FootballTab.MATCHES,
                    onClick = { viewModel.selectTab(FootballTab.MATCHES) },
                    text = { Text("Matches") }
                )
                Tab(
                    selected = selectedTab == FootballTab.LEAGUES,
                    onClick = { viewModel.selectTab(FootballTab.LEAGUES) },
                    text = { Text("Leagues") }
                )
            }

            when (selectedTab) {
                FootballTab.MATCHES -> MatchesTab(
                    viewModel = viewModel,
                    searchQuery = searchQuery,
                    onMatchClick = { match, competitionName ->
                        selectedMatch = match
                        selectedCompetition = competitionName
                        viewModel.resetMatchDetailState()
                        viewModel.loadHeadToHead(match.id)
                    }
                )
                FootballTab.LEAGUES -> LeaguesTab(viewModel = viewModel)
            }
        }
    }

    selectedMatch?.let { match ->
        MatchDetailSheet(
            match = match,
            competitionName = selectedCompetition,
            viewModel = viewModel,
            onDismiss = {
                selectedMatch = null
                viewModel.clearHeadToHead()
            }
        )
    }
}

@Composable
private fun MatchesTab(
    viewModel: FootballViewModel,
    searchQuery: String,
    onMatchClick: (MatchDto, String) -> Unit
) {
    val liveOnly by viewModel.liveOnly.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val uiState by viewModel.matchesUiState.collectAsState()
    val reminderSet by viewModel.reminderSetForMatchId.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = liveOnly,
                onClick = { viewModel.toggleLiveOnly() },
                label = { Text("Live") },
                leadingIcon = if (liveOnly) {
                    { Box(Modifier.size(8.dp).background(PrimaryTeal, shape = CircleShape)) }
                } else null
            )

            if (!liveOnly) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = viewModel::goToPreviousDay) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous day")
                    }
                    Text(formatDayLabel(selectedDate), fontWeight = FontWeight.Medium)
                    IconButton(onClick = viewModel::goToNextDay) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next day")
                    }
                }
            }
        }

        when (val state = uiState) {
            is MatchesUiState.Loading -> Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                CircularProgressIndicator(color = PrimaryTeal)
            }
            is MatchesUiState.Error -> Text(
                state.message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
            is MatchesUiState.Success -> {
                val filtered = state.groupedMatches.mapNotNull { (competition, matches) ->
                    val matching = if (searchQuery.isBlank()) {
                        matches
                    } else {
                        matches.filter {
                            it.homeTeam.name.contains(searchQuery, true) ||
                                it.awayTeam.name.contains(searchQuery, true)
                        }
                    }
                    if (matching.isEmpty()) null else competition to matching
                }

                if (filtered.isEmpty()) {
                    Text(
                        if (liveOnly) "No live matches right now across the top 5 leagues." else "No matches found for this day.",
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                        state.fallbackNote?.let { note ->
                            item {
                                Text(
                                    note,
                                    color = AccentGold,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                )
                            }
                        }
                        filtered.forEach { (competition, matches) ->
                            item { CompetitionHeader(competition) }
                            items(matches, key = { it.id }) { match ->
                                MatchRow(
                                    match = match,
                                    reminderSet = match.id in reminderSet,
                                    onClick = { onMatchClick(match, competition) },
                                    onToggleReminder = { viewModel.toggleReminder(match) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaguesTab(viewModel: FootballViewModel) {
    val selectedLeague by viewModel.selectedLeagueForStandings.collectAsState()
    val uiState by viewModel.standingsUiState.collectAsState()

    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = TopLeague.entries.indexOf(selectedLeague), edgePadding = 12.dp) {
            TopLeague.entries.forEach { league ->
                Tab(
                    selected = league == selectedLeague,
                    onClick = { viewModel.selectLeagueForStandings(league) },
                    text = { Text(league.displayName) }
                )
            }
        }

        when (val state = uiState) {
            is StandingsUiState.Loading -> Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                CircularProgressIndicator(color = PrimaryTeal)
            }
            is StandingsUiState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            is StandingsUiState.Success -> {
                val table = state.standings?.standings?.firstOrNull { it.type == "TOTAL" }
                if (table == null) {
                    Text("No standings available for ${selectedLeague.displayName} right now.", modifier = Modifier.padding(16.dp))
                } else {
                    Column(Modifier.padding(16.dp)) { StandingsTable(table.table) }
                }
            }
        }
    }
}

@Composable
private fun CompetitionHeader(name: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = AccentGold)
    }
    HorizontalDivider()
}

@Composable
private fun MatchRow(
    match: MatchDto,
    reminderSet: Boolean,
    onClick: () -> Unit,
    onToggleReminder: () -> Unit
) {
    val isLive = match.status == "IN_PLAY" || match.status == "PAUSED" || match.status == "LIVE"
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(56.dp)) {
            if (isLive) {
                Text("LIVE", color = PrimaryTeal, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
            } else if (match.status == "FINISHED") {
                Text("FT", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            } else {
                Text(formatKickoffTime(match.utcDate), style = MaterialTheme.typography.bodySmall)
            }
        }

        Column(
            modifier = Modifier.weight(1f).clickable(onClick = onClick)
        ) {
            TeamRow(match.homeTeam.name, match.homeTeam.crest, match.score.fullTime.home)
            Spacer(Modifier.height(4.dp))
            TeamRow(match.awayTeam.name, match.awayTeam.crest, match.score.fullTime.away)
        }

        IconButton(onClick = onToggleReminder) {
            Icon(
                if (reminderSet) Icons.Default.NotificationsActive else Icons.Default.NotificationsNone,
                contentDescription = if (reminderSet) "Cancel kickoff reminder" else "Set kickoff reminder",
                tint = if (reminderSet) AccentGold else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TeamRow(name: String, crestUrl: String?, score: Int?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (crestUrl != null) {
            AsyncImage(model = crestUrl, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(name, modifier = Modifier.weight(1f), maxLines = 1)
        if (score != null) Text("$score", fontWeight = FontWeight.Bold)
    }
}

private fun formatKickoffTime(utcDate: String): String = runCatching {
    OffsetDateTime.parse(utcDate).format(DateTimeFormatter.ofPattern("HH:mm"))
}.getOrDefault("--:--")

private fun formatDayLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
    }
}

@Composable
private fun StandingsTable(rows: List<com.fusionone.app.core.network.StandingRowDto>) {
    Card(shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp)) {
            Column {
                StandingsHeaderRow()
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                rows.forEach { row -> StandingsDataRow(row) }
            }
        }
    }
}

private val colPos = 32.dp
private val colCrest = 24.dp
private val colTeam = 140.dp
private val colStat = 32.dp
private val colForm = 100.dp
private val colPts = 44.dp

@Composable
private fun StandingsHeaderRow() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("#", Modifier.width(colPos), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.width(colCrest))
        Text("Team", Modifier.width(colTeam), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        Text("W", Modifier.width(colStat), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        Text("D", Modifier.width(colStat), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        Text("L", Modifier.width(colStat), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        Text("GF", Modifier.width(colStat), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        Text("GA", Modifier.width(colStat), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        Text("GD", Modifier.width(colStat), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        Text("Pts", Modifier.width(colPts), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        Text("Form", Modifier.width(colForm), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun StandingsDataRow(row: com.fusionone.app.core.network.StandingRowDto) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("${row.position}", Modifier.width(colPos), style = MaterialTheme.typography.bodySmall)
        Box(Modifier.width(colCrest), contentAlignment = Alignment.CenterStart) {
            if (row.team.crest != null) {
                AsyncImage(model = row.team.crest, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
        Text(row.team.shortName ?: row.team.name, Modifier.width(colTeam), maxLines = 1, style = MaterialTheme.typography.bodySmall)
        Text("${row.won}", Modifier.width(colStat), style = MaterialTheme.typography.bodySmall)
        Text("${row.draw}", Modifier.width(colStat), style = MaterialTheme.typography.bodySmall)
        Text("${row.lost}", Modifier.width(colStat), style = MaterialTheme.typography.bodySmall)
        Text("${row.goalsFor}", Modifier.width(colStat), style = MaterialTheme.typography.bodySmall)
        Text("${row.goalsAgainst}", Modifier.width(colStat), style = MaterialTheme.typography.bodySmall)
        Text("${row.goalDifference}", Modifier.width(colStat), style = MaterialTheme.typography.bodySmall)
        Text("${row.points}", Modifier.width(colPts), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
        FormBadges(row.form, Modifier.width(colForm))
    }
}

@Composable
private fun FormBadges(form: String?, modifier: Modifier = Modifier) {
    val results = form?.split(",", "-", " ")?.filter { it.isNotBlank() }?.takeLast(5) ?: emptyList()
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        if (results.isEmpty()) {
            Text("—", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            results.forEach { r ->
                val color = when (r.trim().uppercase()) {
                    "W" -> PrimaryTeal
                    "L" -> DangerRed
                    else -> AccentGold
                }
                Box(
                    Modifier
                        .size(16.dp)
                        .background(color, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(r.trim().take(1).uppercase(), style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color.Black)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MatchDetailSheet(
    match: MatchDto,
    competitionName: String,
    viewModel: FootballViewModel,
    onDismiss: () -> Unit
) {
    val selectedDetailTab by viewModel.selectedDetailTab.collectAsState()
    var transfersTeamName by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "${match.homeTeam.name} vs ${match.awayTeam.name}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Row {
                TeamTag(match.homeTeam.name) { transfersTeamName = match.homeTeam.name }
                Spacer(Modifier.width(8.dp))
                TeamTag(match.awayTeam.name) { transfersTeamName = match.awayTeam.name }
            }

            TabRow(selectedTabIndex = selectedDetailTab.ordinal) {
                Tab(
                    selected = selectedDetailTab == MatchDetailTab.OVERVIEW,
                    onClick = { viewModel.selectDetailTab(MatchDetailTab.OVERVIEW) },
                    text = { Text("H2H") }
                )
                Tab(
                    selected = selectedDetailTab == MatchDetailTab.LINEUPS,
                    onClick = {
                        viewModel.selectDetailTab(MatchDetailTab.LINEUPS)
                        viewModel.loadLineups(match, competitionName)
                    },
                    text = { Text("Lineups") }
                )
                Tab(
                    selected = selectedDetailTab == MatchDetailTab.STATS,
                    onClick = {
                        viewModel.selectDetailTab(MatchDetailTab.STATS)
                        viewModel.loadMatchStatistics(match, competitionName)
                    },
                    text = { Text("Stats") }
                )
                Tab(
                    selected = selectedDetailTab == MatchDetailTab.PLAYERS,
                    onClick = {
                        viewModel.selectDetailTab(MatchDetailTab.PLAYERS)
                        viewModel.loadPlayerRatings(match, competitionName)
                    },
                    text = { Text("Players") }
                )
            }

            Box(Modifier.heightIn(min = 160.dp, max = 420.dp)) {
                when (selectedDetailTab) {
                    MatchDetailTab.OVERVIEW -> OverviewTabContent(viewModel)
                    MatchDetailTab.LINEUPS -> LineupsTabContent(viewModel)
                    MatchDetailTab.STATS -> StatsTabContent(viewModel)
                    MatchDetailTab.PLAYERS -> PlayersTabContent(viewModel)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    transfersTeamName?.let { teamName ->
        TransfersSheet(
            teamName = teamName,
            viewModel = viewModel,
            onDismiss = {
                transfersTeamName = null
                viewModel.clearTeamTransfers()
            }
        )
    }
}

@Composable
private fun TeamTag(name: String, onClick: () -> Unit) {
    AssistChip(onClick = onClick, label = { Text(name, maxLines = 1) })
}

@Composable
private fun OverviewTabContent(viewModel: FootballViewModel) {
    val headToHead by viewModel.headToHead.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (headToHead == null) {
            CircularProgressIndicator(color = PrimaryTeal)
        } else {
            val agg = headToHead!!.aggregates
            Text("Last ${agg.numberOfMatches} meetings, ${agg.totalGoals} total goals")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                H2HStatColumn(agg.homeTeam.name, agg.homeTeam.wins, agg.homeTeam.draws, agg.homeTeam.losses)
                H2HStatColumn(agg.awayTeam.name, agg.awayTeam.wins, agg.awayTeam.draws, agg.awayTeam.losses)
            }
            HorizontalDivider()
            Text("Recent meetings", fontWeight = FontWeight.SemiBold)
            headToHead!!.matches.take(5).forEach { m ->
                Text(
                    "${formatKickoffTime(m.utcDate)}: ${m.homeTeam.shortName ?: m.homeTeam.name} " +
                        "${m.score.fullTime.home ?: "-"}-${m.score.fullTime.away ?: "-"} " +
                        (m.awayTeam.shortName ?: m.awayTeam.name),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun LineupsTabContent(viewModel: FootballViewModel) {
    val state by viewModel.matchLineups.collectAsState()
    when (val s = state) {
        is DeepDataState.Idle, DeepDataState.Loading -> CircularProgressIndicator(color = PrimaryTeal)
        is DeepDataState.Unavailable -> Text(s.message, color = MaterialTheme.colorScheme.error)
        is DeepDataState.Success -> {
            val lineups = s.data
            if (lineups.isNullOrEmpty()) {
                Text("Lineups aren't published yet — usually available closer to kickoff.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(lineups) { lineup ->
                        Column {
                            Text(
                                "${lineup.team.name}${lineup.formation?.let { " ($it)" } ?: ""}",
                                fontWeight = FontWeight.Bold
                            )
                            lineup.coach?.name?.let { Text("Coach: $it", style = MaterialTheme.typography.bodySmall) }
                            Spacer(Modifier.height(4.dp))
                            lineup.startXI.forEach { p ->
                                Text(
                                    "${p.player.number ?: ""}  ${p.player.name}  ${p.player.pos ?: ""}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsTabContent(viewModel: FootballViewModel) {
    val state by viewModel.matchStatistics.collectAsState()
    when (val s = state) {
        is DeepDataState.Idle, DeepDataState.Loading -> CircularProgressIndicator(color = PrimaryTeal)
        is DeepDataState.Unavailable -> Text(s.message, color = MaterialTheme.colorScheme.error)
        is DeepDataState.Success -> {
            val stats = s.data
            if (stats.isNullOrEmpty() || stats.size < 2) {
                Text("Match statistics aren't available for this fixture yet.")
            } else {
                val home = stats[0]
                val away = stats[1]
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(home.team.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(away.team.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        }
                    }
                    val statTypes = home.statistics.map { it.type }
                    items(statTypes) { type ->
                        val homeVal = home.statistics.firstOrNull { it.type == type }?.value ?: "-"
                        val awayVal = away.statistics.firstOrNull { it.type == type }?.value ?: "-"
                        Column {
                            Text(type, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("$homeVal", modifier = Modifier.weight(1f))
                                Text("$awayVal", modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayersTabContent(viewModel: FootballViewModel) {
    val state by viewModel.matchPlayerRatings.collectAsState()
    when (val s = state) {
        is DeepDataState.Idle, DeepDataState.Loading -> CircularProgressIndicator(color = PrimaryTeal)
        is DeepDataState.Unavailable -> Text(s.message, color = MaterialTheme.colorScheme.error)
        is DeepDataState.Success -> {
            val teams = s.data
            if (teams.isNullOrEmpty()) {
                Text("Player ratings aren't available for this fixture yet.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    teams.forEach { teamPlayers ->
                        item { Text(teamPlayers.team.name, fontWeight = FontWeight.Bold) }
                        items(teamPlayers.players) { entry ->
                            val rating = entry.statistics.firstOrNull()?.games?.rating
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(entry.player.name, modifier = Modifier.weight(1f))
                                Text(rating ?: "-", fontWeight = FontWeight.Bold, color = AccentGold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransfersSheet(
    teamName: String,
    viewModel: FootballViewModel,
    onDismiss: () -> Unit
) {
    val state by viewModel.teamTransfers.collectAsState()

    LaunchedEffect(teamName) { viewModel.loadTeamTransfers(teamName) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("$teamName — Transfer History", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            when (val s = state) {
                is DeepDataState.Idle, DeepDataState.Loading -> CircularProgressIndicator(color = PrimaryTeal)
                is DeepDataState.Unavailable -> Text(s.message, color = MaterialTheme.colorScheme.error)
                is DeepDataState.Success -> {
                    val groups = s.data
                    if (groups.isNullOrEmpty()) {
                        Text("No transfer history found for this club.")
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 420.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            groups.flatMap { group -> group.transfers.map { group.player to it } }
                                .sortedByDescending { it.second.date }
                                .take(30)
                                .let { items(it) { (player, transfer) ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column(Modifier.weight(1f)) {
                                            Text(player.name, fontWeight = FontWeight.Medium)
                                            Text(
                                                "${transfer.teams.teamIn.name} ← ${transfer.teams.out.name}",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(transfer.date ?: "-", style = MaterialTheme.typography.bodySmall)
                                            transfer.type?.let { Text(it, color = AccentGold, style = MaterialTheme.typography.labelSmall) }
                                        }
                                    }
                                } }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun H2HStatColumn(name: String, wins: Int, draws: Int, losses: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(name, style = MaterialTheme.typography.bodySmall)
        Text("$wins W · $draws D · $losses L", fontWeight = FontWeight.Bold)
    }
}
