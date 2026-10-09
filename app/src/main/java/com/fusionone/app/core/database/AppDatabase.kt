package com.fusionone.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ScanHistoryEntity::class,
        FootballCacheEntity::class,
        ApiFootballFixtureMappingEntity::class,
        ApiFootballTeamMappingEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scanHistoryDao(): ScanHistoryDao
    abstract fun footballCacheDao(): FootballCacheDao
    abstract fun apiFootballMappingDao(): ApiFootballMappingDao
}
