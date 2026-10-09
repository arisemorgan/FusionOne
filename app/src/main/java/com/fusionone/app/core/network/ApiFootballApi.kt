package com.fusionone.app.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * API-Football (API-SPORTS) v3 — a separate free-tier provider from football-data.org,
 * used ONLY for the deeper data football-data.org doesn't offer: match statistics,
 * lineups, per-player ratings, and transfer history. Free tier: 100 requests/day TOTAL,
 * so every call here is cached aggressively and only fired on demand (not on a timer).
 *
 * Docs: https://www.api-football.com/documentation-v3
 * Auth: header "x-apisports-key: YOUR_KEY" (free key from https://dashboard.api-football.com/register)
 */
interface ApiFootballApi {

    /** Used only to RESOLVE a football-data.org match to its API-Football fixture ID
     *  (different providers, different ID schemes — see ApiFootballFixtureResolver). */
    @GET("fixtures")
    suspend fun findFixturesByLeagueAndDate(
        @Query("league") leagueId: Int,
        @Query("season") season: Int,
        @Query("date") date: String
    ): ApiFootballFixturesResponse

    @GET("fixtures/statistics")
    suspend fun getFixtureStatistics(@Query("fixture") fixtureId: Int): ApiFootballStatisticsResponse

    @GET("fixtures/lineups")
    suspend fun getFixtureLineups(@Query("fixture") fixtureId: Int): ApiFootballLineupsResponse

    @GET("fixtures/players")
    suspend fun getFixturePlayerRatings(@Query("fixture") fixtureId: Int): ApiFootballPlayersResponse

    /** Used only to resolve a team name to its API-Football team ID (cached after first hit). */
    @GET("teams")
    suspend fun findTeamByName(
        @Query("search") name: String
    ): ApiFootballTeamsResponse

    @GET("transfers")
    suspend fun getTeamTransfers(@Query("team") teamId: Int): ApiFootballTransfersResponse
}

// --- Fixture resolution ---
@Serializable
data class ApiFootballFixturesResponse(val response: List<ApiFootballFixtureItem> = emptyList())

@Serializable
data class ApiFootballFixtureItem(
    val fixture: ApiFootballFixtureInfo,
    val teams: ApiFootballFixtureTeams
)

@Serializable
data class ApiFootballFixtureInfo(val id: Int, val date: String)

@Serializable
data class ApiFootballFixtureTeams(val home: ApiFootballTeamRef, val away: ApiFootballTeamRef)

@Serializable
data class ApiFootballTeamRef(val id: Int, val name: String)

// --- Statistics ---
@Serializable
data class ApiFootballStatisticsResponse(val response: List<ApiFootballTeamStatistics> = emptyList())

@Serializable
data class ApiFootballTeamStatistics(
    val team: ApiFootballTeamRef,
    val statistics: List<ApiFootballStatItem>
)

@Serializable
data class ApiFootballStatItem(
    val type: String, // e.g. "Shots on Goal", "Ball Possession", "Corner Kicks", "Yellow Cards"
    val value: String? = null // API returns mixed types (Int/String/null) — kept as raw string, parsed for display
)

// --- Lineups ---
@Serializable
data class ApiFootballLineupsResponse(val response: List<ApiFootballLineup> = emptyList())

@Serializable
data class ApiFootballLineup(
    val team: ApiFootballTeamRef,
    val formation: String? = null,
    val startXI: List<ApiFootballLineupPlayerWrapper> = emptyList(),
    val substitutes: List<ApiFootballLineupPlayerWrapper> = emptyList(),
    val coach: ApiFootballCoach? = null
)

@Serializable
data class ApiFootballLineupPlayerWrapper(val player: ApiFootballLineupPlayer)

@Serializable
data class ApiFootballLineupPlayer(
    val id: Int,
    val name: String,
    val number: Int? = null,
    val pos: String? = null // G, D, M, F
)

@Serializable
data class ApiFootballCoach(val id: Int? = null, val name: String? = null)

// --- Player ratings ---
@Serializable
data class ApiFootballPlayersResponse(val response: List<ApiFootballTeamPlayers> = emptyList())

@Serializable
data class ApiFootballTeamPlayers(
    val team: ApiFootballTeamRef,
    val players: List<ApiFootballPlayerEntry>
)

@Serializable
data class ApiFootballPlayerEntry(
    val player: ApiFootballLineupPlayer,
    val statistics: List<ApiFootballPlayerMatchStats>
)

@Serializable
data class ApiFootballPlayerMatchStats(
    val games: ApiFootballPlayerGameInfo? = null,
    val goals: ApiFootballPlayerGoalsInfo? = null
)

@Serializable
data class ApiFootballPlayerGameInfo(
    val minutes: Int? = null,
    val rating: String? = null,
    val captain: Boolean = false
)

@Serializable
data class ApiFootballPlayerGoalsInfo(val total: Int? = null, val assists: Int? = null)

// --- Team resolution ---
@Serializable
data class ApiFootballTeamsResponse(val response: List<ApiFootballTeamSearchItem> = emptyList())

@Serializable
data class ApiFootballTeamSearchItem(val team: ApiFootballTeamRef)

// --- Transfers ---
@Serializable
data class ApiFootballTransfersResponse(val response: List<ApiFootballTransferGroup> = emptyList())

@Serializable
data class ApiFootballTransferGroup(
    val player: ApiFootballTeamRef, // reused shape: {id, name}
    val transfers: List<ApiFootballTransferItem>
)

@Serializable
data class ApiFootballTransferItem(
    val date: String? = null,
    val type: String? = null, // e.g. "Loan", "Free", "€5M"
    val teams: ApiFootballTransferTeams
)

@Serializable
data class ApiFootballTransferTeams(
    @SerialName("in") val teamIn: ApiFootballTeamRef,
    val out: ApiFootballTeamRef
)
