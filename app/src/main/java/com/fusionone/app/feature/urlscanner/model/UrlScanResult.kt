package com.fusionone.app.feature.urlscanner.model

enum class Verdict { SAFE, WARNING, DANGER }

data class UrlScanResult(
    val originalUrl: String,
    val finalUrl: String,
    val domain: String,
    val verdict: Verdict,
    val reputationScore: Int,
    val threatExplanations: List<String>,
    val redirectChain: List<String>,
    val sslValid: Boolean,
    val sslIssuer: String?,
    val sslDaysUntilExpiry: Long?,
    val domainAgeDays: Int?,
    val whoisRegistrar: String?,
    val ipAddress: String?,
    val isTyposquat: Boolean,
    val resemblesBrand: String?,
    val scannedAtEpochMillis: Long
)
