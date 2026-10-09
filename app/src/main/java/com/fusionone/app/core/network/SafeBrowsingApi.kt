package com.fusionone.app.core.network

import com.fusionone.app.BuildConfig
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

interface SafeBrowsingApi {
    @POST("v4/threatMatches:find")
    suspend fun findThreatMatches(
        @Query("key") apiKey: String = BuildConfig.SAFE_BROWSING_API_KEY,
        @Body body: SafeBrowsingRequest
    ): SafeBrowsingResponse
}

@Serializable
data class SafeBrowsingRequest(
    val client: ClientInfo = ClientInfo(),
    val threatInfo: ThreatInfo
) {
    @Serializable
    data class ClientInfo(
        val clientId: String = "com.fusionone.app",
        val clientVersion: String = "1.0.0"
    )

    @Serializable
    data class ThreatInfo(
        val threatTypes: List<String> = listOf(
            "MALWARE", "SOCIAL_ENGINEERING", "UNWANTED_SOFTWARE", "POTENTIALLY_HARMFUL_APPLICATION"
        ),
        val platformTypes: List<String> = listOf("ANY_PLATFORM"),
        val threatEntryTypes: List<String> = listOf("URL"),
        val threatEntries: List<ThreatEntry>
    )

    @Serializable
    data class ThreatEntry(val url: String)
}

@Serializable
data class SafeBrowsingResponse(
    val matches: List<ThreatMatch>? = null
) {
    @Serializable
    data class ThreatMatch(
        val threatType: String,
        val platformType: String,
        val threat: ThreatUrl
    )

    @Serializable
    data class ThreatUrl(val url: String)
}
