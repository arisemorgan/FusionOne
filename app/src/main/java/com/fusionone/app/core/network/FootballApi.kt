package com.fusionone.app.core.network

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface FootballApi {

    @GET("competitions/{code}/matches")
    suspend fun getFixtures(
        @Path("code") competitionCode: String,
        @Query("status") status: String? = null,
        @Query("dateFrom") dateFrom: String? = null,
        @Query("dateTo") dateTo: String? = null
    ): MatchesResponse

    @GET("competitions/{code}/standings")
    suspend fun getStandings(@Path("code") competitionCode: String): StandingsResponse

    @GET("matches/{id}/head2head")
    suspend fun getHeadToHead(
        @Path("id") matchId: Long,
        @Query("limit") limit: Int = 10
    ): HeadToHeadResponse

    @GET("competitions/{code}")
    suspend fun getCompetition(@Path("code") competitionCode: String): CompetitionResponse
}

@Serializable
data class MatchesResponse(val matches: List<MatchDto> = emptyList())

@Serializable
data class MatchDto(
    val id: Long,
    val utcDate: String,
    val status: String,
    val matchday: Int? = null,
    val stage: String? = null,
    val homeTeam: TeamDto,
    val awayTeam: TeamDto,
    val score: ScoreDto,
    val competition: CompetitionRefDto? = null
)

@Serializable
data class TeamDto(
    val id: Long,
    val name: String,
    val shortName: String? = null,
    val tla: String? = null,
    val crest: String? = null
)

@Serializable
data class ScoreDto(
    val winner: String? = null,
    val duration: String? = null,
    val fullTime: ScoreLineDto,
    val halfTime: ScoreLineDto? = null
)

@Serializable
data class ScoreLineDto(val home: Int? = null, val away: Int? = null)

@Serializable
data class CompetitionRefDto(val id: Long, val name: String, val code: String? = null)

@Serializable
data class CompetitionResponse(
    val id: Long,
    val name: String,
    val code: String,
    val emblem: String? = null,
    val currentSeason: SeasonDto? = null
)

@Serializable
data class SeasonDto(val id: Long, val startDate: String, val endDate: String, val currentMatchday: Int? = null)

@Serializable
data class StandingsResponse(
    val competition: CompetitionRefDto,
    val season: SeasonDto? = null,
    val standings: List<StandingTableDto>
)

@Serializable
data class StandingTableDto(
    val stage: String,
    val type: String,
    val group: String? = null,
    val table: List<StandingRowDto>
)

@Serializable
data class StandingRowDto(
    val position: Int,
    val team: TeamDto,
    val playedGames: Int,
    val won: Int,
    val draw: Int,
    val lost: Int,
    val points: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val goalDifference: Int,
    val form: String? = null
)

@Serializable
data class HeadToHeadResponse(
    val aggregates: AggregatesDto,
    val matches: List<MatchDto> = emptyList()
)

@Serializable
data class AggregatesDto(
    val numberOfMatches: Int,
    val totalGoals: Int,
    val homeTeam: H2HTeamStatsDto,
    val awayTeam: H2HTeamStatsDto
)

@Serializable
data class H2HTeamStatsDto(
    val id: Long,
    val name: String,
    val wins: Int,
    val draws: Int,
    val losses: Int
)
