package com.fusionone.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ApiFootballMappingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putFixtureMapping(entity: ApiFootballFixtureMappingEntity)

    @Query("SELECT * FROM api_football_fixture_mapping WHERE footballDataMatchId = :matchId LIMIT 1")
    suspend fun getFixtureMapping(matchId: Long): ApiFootballFixtureMappingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putTeamMapping(entity: ApiFootballTeamMappingEntity)

    @Query("SELECT * FROM api_football_team_mapping WHERE teamName = :teamName LIMIT 1")
    suspend fun getTeamMapping(teamName: String): ApiFootballTeamMappingEntity?
}
