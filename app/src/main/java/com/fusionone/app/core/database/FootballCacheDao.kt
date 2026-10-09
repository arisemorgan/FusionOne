package com.fusionone.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FootballCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: FootballCacheEntity)

    @Query("SELECT * FROM football_cache WHERE cacheKey = :key LIMIT 1")
    suspend fun get(key: String): FootballCacheEntity?

    @Query("DELETE FROM football_cache WHERE fetchedAtEpochMillis < :olderThanEpochMillis")
    suspend fun purgeOlderThan(olderThanEpochMillis: Long)
}
