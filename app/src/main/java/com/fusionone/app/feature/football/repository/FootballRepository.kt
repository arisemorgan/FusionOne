package com.fusionone.app.feature.football.repository

import com.fusionone.app.core.database.FootballCacheDao
import com.fusionone.app.core.database.FootballCacheEntity
import com.fusionone.app.core.network.FootballApi
import com.fusionone.app.core.network.HeadToHeadResponse
import com.fusionone.app.core.network.MatchDto
import com.fusionone.app.core.network.StandingsResponse
import com.fusionone.app.feature.football.model.TopLeague
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FootballRepository @Inject constructor(
    private val api: FootballApi,
    private val cacheDao: FootballCacheDao
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val cacheTtlMillis = 60_000L

    suspend fun getTodayFixturesAcrossTopLeagues(): Map<TopLeague, List<MatchDto>> = coroutineScope {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        TopLeague.entries.associateWith { league ->
            async {
                cached("fixtures_today_${league.code}_$today") {
                    api.getFixtures(league.code, dateFrom = today, dateTo = today).matches
                }
            }
        }.mapValues { it.value.await() }
    }

    /**
     * All 5 top leagues' fixtures for a single calendar date, combined and grouped by
     * competition name — this is what feeds the Sofascore-style day feed. One request per
     * league (5 total), run in parallel, each individually cached and each individually
     * allowed to fail without taking the others down.
     */
    suspend fun getFixturesForDate(date: LocalDate): List<Pair<String, List<MatchDto>>> = coroutineScope {
        val dateStr = date.format(DateTimeFormatter.ISO_DATE)
        val perLeague = TopLeague.entries.map { league ->
            async {
                league to runCatching {
                    cached("fixtures_date_${league.code}_$dateStr") {
                        api.getFixtures(league.code, dateFrom = dateStr, dateTo = dateStr).matches
                    }
                }.getOrDefault(emptyList())
            }
        }.awaitAll()

        perLeague
            .filter { (_, matches) -> matches.isNotEmpty() }
            .map { (league, matches) -> league.displayName to matches.sortedBy { it.utcDate } }
    }

    /** All 5 top leagues' currently live matches, combined and grouped by competition. */
    suspend fun getAllLiveMatches(): List<Pair<String, List<MatchDto>>> = coroutineScope {
        val perLeague = TopLeague.entries.map { league ->
            async { league to getLiveMatches(league) }
        }.awaitAll()

        perLeague
            .filter { (_, matches) -> matches.isNotEmpty() }
            .map { (league, matches) -> league.displayName to matches }
    }

    /**
     * Falls back for the (very real, currently-happening) case where "today" has zero
     * fixtures across all 5 leagues — the close season, international breaks, etc.
     * Rather than a dead end, finds the next calendar date with any scheduled fixture
     * within the given window, using ONE ranged request per league (not one per day —
     * that would burn through football-data.org's rate limit fast).
     * Returns null if genuinely nothing is scheduled in that window either.
     */
    suspend fun getNextAvailableFixtures(daysAhead: Int = 60): Pair<LocalDate, List<Pair<String, List<MatchDto>>>>? = coroutineScope {
        val perLeague = TopLeague.entries.map { league ->
            async { league to getUpcomingFixtures(league, daysAhead) }
        }.awaitAll()

        val earliestDate = perLeague
            .flatMap { (_, matches) -> matches }
            .mapNotNull { runCatching { java.time.OffsetDateTime.parse(it.utcDate).toLocalDate() }.getOrNull() }
            .minOrNull() ?: return@coroutineScope null

        val grouped = perLeague
            .map { (league, matches) -> league.displayName to matches.filter {
                runCatching { java.time.OffsetDateTime.parse(it.utcDate).toLocalDate() == earliestDate }.getOrDefault(false)
            } }
            .filter { (_, matches) -> matches.isNotEmpty() }
            .map { (name, matches) -> name to matches.sortedBy { it.utcDate } }

        earliestDate to grouped
    }

    suspend fun getUpcomingFixtures(league: TopLeague, daysAhead: Int = 7): List<MatchDto> {
        val from = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        val to = LocalDate.now().plusDays(daysAhead.toLong()).format(DateTimeFormatter.ISO_DATE)
        return cached("fixtures_${league.code}_${from}_$to") {
            api.getFixtures(league.code, dateFrom = from, dateTo = to).matches
        }
    }

    suspend fun getLiveMatches(league: TopLeague): List<MatchDto> {
        return runCatching { api.getFixtures(league.code, status = "LIVE").matches }.getOrDefault(emptyList())
    }

    suspend fun getStandings(league: TopLeague): StandingsResponse? =
        cachedNullable("standings_${league.code}") { api.getStandings(league.code) }

    suspend fun getHeadToHead(matchId: Long): HeadToHeadResponse? =
        cachedNullable("h2h_$matchId") { api.getHeadToHead(matchId) }

    private suspend inline fun <reified T> cached(key: String, crossinline fetch: suspend () -> T): T {
        val cachedEntry = cacheDao.get(key)
        val now = System.currentTimeMillis()
        if (cachedEntry != null && now - cachedEntry.fetchedAtEpochMillis < cacheTtlMillis) {
            runCatching { return json.decodeFromString<T>(cachedEntry.jsonPayload) }
        }
        val fresh = runCatching { fetch() }.getOrElse {
            cachedEntry?.let { entry -> return json.decodeFromString(entry.jsonPayload) }
            throw it
        }
        cacheDao.put(FootballCacheEntity(key, json.encodeToString(fresh), now))
        return fresh
    }

    private suspend inline fun <reified T> cachedNullable(key: String, crossinline fetch: suspend () -> T): T? {
        return runCatching { cached(key, fetch) }.getOrNull()
    }
}
