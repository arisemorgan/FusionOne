package com.fusionone.app.core.database

import androidx.room.Entity

/**
 * Caches the resolution from a football-data.org match/team ID to the equivalent
 * API-Football fixture/team ID. These are two unrelated providers with unrelated ID
 * schemes, so resolving one requires a real API-Football lookup by team name + date —
 * something we only want to ever do ONCE per match/team, given the 100-requests/day cap.
 */
@Entity(tableName = "api_football_fixture_mapping", primaryKeys = ["footballDataMatchId"])
data class ApiFootballFixtureMappingEntity(
    val footballDataMatchId: Long,
    val apiFootballFixtureId: Int,
    val resolvedAtEpochMillis: Long
)

@Entity(tableName = "api_football_team_mapping", primaryKeys = ["teamName"])
data class ApiFootballTeamMappingEntity(
    val teamName: String,
    val apiFootballTeamId: Int,
    val resolvedAtEpochMillis: Long
)
