package com.fusionone.app.core.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RedirectChainResolver @Inject constructor() {

    suspend fun resolve(startUrl: String, maxHops: Int = 10): List<String> = withContext(Dispatchers.IO) {
        val chain = mutableListOf(startUrl)
        var current = startUrl

        repeat(maxHops) {
            val next = runCatching {
                val connection = URL(current).openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 6000
                connection.readTimeout = 6000
                connection.requestMethod = "HEAD"
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (FusionOne URL Scanner)")
                connection.connect()

                val code = connection.responseCode
                val location = connection.getHeaderField("Location")
                connection.disconnect()

                if (code in 300..399 && location != null) {
                    if (location.startsWith("http")) location else URL(URL(current), location).toString()
                } else {
                    null
                }
            }.getOrNull()

            if (next == null || next == current || chain.contains(next)) return@withContext chain
            chain.add(next)
            current = next
        }
        chain
    }
}
