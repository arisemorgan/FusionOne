package com.fusionone.app.di

import com.fusionone.app.BuildConfig
import com.fusionone.app.core.network.ApiFootballApi
import com.fusionone.app.core.network.FootballApi
import com.fusionone.app.core.network.SafeBrowsingApi
import com.fusionone.app.core.network.UrlhausApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Provides
    @Singleton
    @Named("safeBrowsingRetrofit")
    fun provideSafeBrowsingRetrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://safebrowsing.googleapis.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    @Named("urlhausRetrofit")
    fun provideUrlhausRetrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://urlhaus-api.abuse.ch/v1/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    @Named("footballRetrofit")
    fun provideFootballRetrofit(client: OkHttpClient): Retrofit {
        val footballClient = client.newBuilder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("X-Auth-Token", BuildConfig.FOOTBALL_DATA_API_KEY)
                    .build()
                chain.proceed(request)
            }
            .build()
        return Retrofit.Builder()
            .baseUrl("https://api.football-data.org/v4/")
            .client(footballClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    @Named("apiFootballRetrofit")
    fun provideApiFootballRetrofit(client: OkHttpClient): Retrofit {
        val apiFootballClient = client.newBuilder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("x-apisports-key", BuildConfig.API_FOOTBALL_KEY)
                    .build()
                chain.proceed(request)
            }
            .build()
        return Retrofit.Builder()
            .baseUrl("https://v3.football.api-sports.io/")
            .client(apiFootballClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideSafeBrowsingApi(@Named("safeBrowsingRetrofit") retrofit: Retrofit): SafeBrowsingApi =
        retrofit.create(SafeBrowsingApi::class.java)

    @Provides
    @Singleton
    fun provideUrlhausApi(@Named("urlhausRetrofit") retrofit: Retrofit): UrlhausApi =
        retrofit.create(UrlhausApi::class.java)

    @Provides
    @Singleton
    fun provideFootballApi(@Named("footballRetrofit") retrofit: Retrofit): FootballApi =
        retrofit.create(FootballApi::class.java)

    @Provides
    @Singleton
    fun provideApiFootballApi(@Named("apiFootballRetrofit") retrofit: Retrofit): ApiFootballApi =
        retrofit.create(ApiFootballApi::class.java)
}
