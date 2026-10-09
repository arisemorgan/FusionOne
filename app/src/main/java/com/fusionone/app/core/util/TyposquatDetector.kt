package com.fusionone.app.core.util

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TyposquatDetector @Inject constructor() {

    private val watchedBrands = listOf(
        "google.com", "facebook.com", "instagram.com", "apple.com", "microsoft.com",
        "paypal.com", "amazon.com", "netflix.com", "whatsapp.com", "gmail.com",
        "outlook.com", "chase.com", "bankofamerica.com", "binance.com", "coinbase.com",
        "wellsfargo.com", "linkedin.com", "twitter.com", "x.com", "dropbox.com"
    )

    data class Result(val isSuspicious: Boolean, val resemblesBrand: String? = null, val editDistance: Int? = null)

    fun analyze(domain: String): Result {
        val normalized = domain.lowercase().removePrefix("www.")
        if (normalized in watchedBrands) return Result(isSuspicious = false)

        for (brand in watchedBrands) {
            val distance = levenshtein(normalized, brand)
            if (distance in 1..2) {
                return Result(isSuspicious = true, resemblesBrand = brand, editDistance = distance)
            }
            val brandRoot = brand.substringBefore(".")
            if (normalized.contains(brandRoot) && normalized != brand &&
                (normalized.contains("-") || normalized.count { it == '.' } > 1)
            ) {
                return Result(isSuspicious = true, resemblesBrand = brand, editDistance = distance)
            }
        }
        return Result(isSuspicious = false)
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) {
                    dp[i - 1][j - 1]
                } else {
                    1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                }
            }
        }
        return dp[a.length][b.length]
    }
}
