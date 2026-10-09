package com.fusionone.app.feature.football.repository

import com.fusionone.app.core.database.ApiFootballFixtureMappingEntity
import com.fusionone.app.core.database.ApiFootballMappingDao
import com.fusionone.app.core.database.ApiFootballTeamMappingEntity
import com.fusionone.app.core.database.FootballCacheDao
import com.fusionone.app.core.database.FootballCacheEntity
import com.fusionone.app.core.network.ApiFootballApi
import com.fusionone.app.core.network.ApiFootballLineup
import com.fusionone.app.core.network.ApiFootballTeamPlayers
import com.fusionone.app.core.network.ApiFootballTeamStatistics
import com.fusionone.app.core.network.ApiFootballTransferGroup
import com.fusionone.app.core.network.MatchDto
import com.fusionone.app.feature.football.model.TopLeague
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * API-Football (API-SPORTS) integration — deliberately separate from FootballRepository
 * (football-data.org), which stays the fast-refreshing source for fixtures/live
 * scores/standings. This repository ONLY supplies what football-data.org doesn't: match
 * statistics, lineups, player ratings, and transfer history.
 *
 * Free tier = 100 requests/day TOTAL for the whole app. Two things make that survivable:
 *  1. ID resolution (matching a football-data.org match/team to its API-Football
 *     equivalent) is cached FOREVER once resolved — that mapping never changes.
 *  2. The actual stats/lineups/players/transfers responses are cached for a long TTL
 *     (12 hours) rather than football-data.org's 60-second TTL, since this data doesn't
 *     meaningfully change that often even for in-progress matches.
 */
@Singleton
class ApiFootballRepository @Inject constructor(
    private val api: ApiFootballApi,
    private val mappingDao: ApiFootballMappingDao,
    private val cacheDao: FootballCacheDao
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val longTtlMillis = 12 * 60 * 60 * 1000L // 12 hours

    private val leagueIds = mapOf(
        TopLeague.PREMIER_LEAGUE to 39,
        TopLeague.LA_LIGA to 140,
        TopLeague.BUNDESLIGA to 78,
        TopLeague.SERIE_A to 135,
        TopLeague.LIGUE_1 to 61
    )

    // ---- Public API ----

    suspend fun getMatchStatistics(match: MatchDto, league: TopLeague): List<ApiFootballTeamStatistics>? {
        val fixtureId = resolveFixtureId(match, league) ?: return null
        return cachedOrNull("af_stats_$fixtureId") { api.getFixtureStatistics(fixtureId).response }
    }

    suspend fun getLineups(match: MatchDto, league: TopLeague): List<ApiFootballLineup>? {
        val fixtureId = resolveFixtureId(match, league) ?: return null
        return cachedOrNull("af_lineups_$fixtureId") { api.getFixtureLineups(fixtureId).response }
    }

    suspend fun getPlayerRatings(match: MatchDto, league: TopLeague): List<ApiFootballTeamPlayers>? {
        val fixtureId = resolveFixtureId(match, league) ?: return null
        return cachedOrNull("af_players_$fixtureId") { api.getFixturePlayerRatings(fixtureId).response }
    }

    suspend fun getTeamTransfers(teamName: String): List<ApiFootballTransferGroup>? {
        val teamId = resolveTeamId(teamName) ?: return null
        return cachedOrNull("af_transfers_$teamId") { api.getTeamTransfers(teamId).response }
    }

    // ---- ID resolution (the bridge between the two providers) ----

    private suspend fun resolveFixtureId(match: MatchDto, league: TopLeague): Int? {
        mappingDao.getFixtureMapping(match.id)?.let { return it.apiFootballFixtureId }

        val leagueId = leagueIds[league] ?: return null
        val matchDate = runCatching { OffsetDateTime.parse(match.utcDate) }.getOrNull() ?: return null
        val season = if (matchDate.monthValue >= 7) matchDate.year else matchDate.year - 1
        val dateStr = matchDate.format(DateTimeFormatter.ISO_LOCAL_DATE)

        val candidates = runCatching {
            api.findFixturesByLeagueAndDate(leagueId, season, dateStr).response
        }.getOrElse { return null }

        val resolved = candidates.firstOrNull { candidate ->
            namesLikelyMatch(candidate.teams.home.name, match.homeTeam.name) &&
                namesLikelyMatch(candidate.teams.away.name, match.awayTeam.name)
        } ?: return null

        mappingDao.putFixtureMapping(
            ApiFootballFixtureMappingEntity(
                footballDataMatchId = match.id,
                apiFootballFixtureId = resolved.fixture.id,
                resolvedAtEpochMillis = System.currentTimeMillis()
            )
        )
        return resolved.fixture.id
    }

    private suspend fun resolveTeamId(teamName: String): Int? {
        mappingDao.getTeamMapping(teamName)?.let { return it.apiFootballTeamId }

        val results = runCatching { api.findTeamByName(teamName).response }.getOrElse { return null }
        val resolved = results.firstOrNull { namesLikelyMatch(it.team.name, teamName) } ?: results.firstOrNull()
            ?: return null

        mappingDao.putTeamMapping(
            ApiFootballTeamMappingEntity(
                teamName = teamName,
                apiFootballTeamId = resolved.team.id,
                resolvedAtEpochMillis = System.currentTimeMillis()
            )
        )
        return resolved.team.id
    }

    /** football-data.org and API-Football rarely spell club names identically
     *  ("Manchester United FC" vs "Manchester United") — a loose contains-match on the
     *  normalized core name handles the vast majority of top-5-league clubs correctly. */
    private fun namesLikelyMatch(a: String, b: String): Boolean {
        fun normalize(s: String) = s.lowercase()
            .replace(Regex("\\b(fc|cf|afc|ac|sc|club|de|futbol|calcio)\\b"), "")
            .replace(Regex("[^a-z0-9]"), "")
            .trim()
        val na = normalize(a)
        val nb = normalize(b)
        if (na.isEmpty() || nb.isEmpty()) return false
        return na.contains(nb) || nb.contains(na)
    }

    // ---- Long-TTL cache helper (separate TTL policy from FootballRepository's) ----

    private suspend inline fun <reified T> cachedOrNull(key: String, crossinline fetch: suspend () -> T): T? {
        val cachedEntry = cacheDao.get(key)
        val now = System.currentTimeMillis()
        if (cachedEntry != null && now - cachedEntry.fetchedAtEpochMillis < longTtlMillis) {
            runCatching { return json.decodeFromString<T>(cachedEntry.jsonPayload) }
        }
        val fresh = runCatching { fetch() }.getOrElse {
            cachedEntry?.let { entry -> return runCatching { json.decodeFromString<T>(entry.jsonPayload) }.getOrNull() }
            return null
        }
        runCatching { cacheDao.put(FootballCacheEntity(key, json.encodeToString(fresh), now)) }
        return fresh
    }
}
