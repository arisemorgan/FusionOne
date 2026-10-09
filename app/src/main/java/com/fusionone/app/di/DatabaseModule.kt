package com.fusionone.app.di

import android.content.Context
import androidx.room.Room
import com.fusionone.app.core.database.ApiFootballMappingDao
import com.fusionone.app.core.database.AppDatabase
import com.fusionone.app.core.database.FootballCacheDao
import com.fusionone.app.core.database.ScanHistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "fusionone.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideScanHistoryDao(db: AppDatabase): ScanHistoryDao = db.scanHistoryDao()

    @Provides
    fun provideFootballCacheDao(db: AppDatabase): FootballCacheDao = db.footballCacheDao()

    @Provides
    fun provideApiFootballMappingDao(db: AppDatabase): ApiFootballMappingDao = db.apiFootballMappingDao()
}
