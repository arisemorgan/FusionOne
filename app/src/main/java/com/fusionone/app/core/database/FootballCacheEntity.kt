package com.fusionone.app.core.database

import androidx.room.Entity

@Entity(tableName = "football_cache", primaryKeys = ["cacheKey"])
data class FootballCacheEntity(
    val cacheKey: String,
    val jsonPayload: String,
    val fetchedAtEpochMillis: Long
)
