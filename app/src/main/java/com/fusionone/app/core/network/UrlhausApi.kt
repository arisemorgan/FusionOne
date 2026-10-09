package com.fusionone.app.core.network

import kotlinx.serialization.Serializable
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface UrlhausApi {
    @FormUrlEncoded
    @POST("url/")
    suspend fun lookupUrl(@Field("url") url: String): UrlhausResponse
}

@Serializable
data class UrlhausResponse(
    val query_status: String,
    val url: String? = null,
    val url_status: String? = null,
    val host: String? = null,
    val date_added: String? = null,
    val threat: String? = null,
    val tags: List<String>? = null,
    val urlhaus_reference: String? = null
)
